package com.lonerxone.siphon

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.net.ConnectivityManager
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lonerxone.siphon.data.db.dao.DownloadDao
import com.lonerxone.siphon.data.repository.WaitlistScheduler
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class SiphonApp : Application(), ImageLoaderFactory {

    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var scheduler: WaitlistScheduler

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = Log.getStackTraceString(throwable)
                saveCrashReport("SIPHON crashed on thread ${thread.name}\n\n$trace")
            } catch (e: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashReport(text: String) {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "siphon-crash.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SIPHON")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }

    override fun onCreate() {
        super.onCreate()

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            Log.e("SiphonApp", "Failed to initialize native download engines", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                downloadDao.normalizeStuckDownloads()
                scheduler.startListening()
            } catch (e: Exception) {
                Log.e("SiphonApp", "Error during startup initialization", e)
            }
        }

        autoUpdateEngine()
    }

    /**
     * Sites change all the time. Keep yt-dlp fresh by updating it in the background
     * (at most every 6 hours, and never on mobile data).
     */
    private fun autoUpdateEngine() {
        val prefs = getSharedPreferences("siphon_engine", MODE_PRIVATE)
        val last = prefs.getLong("last_update", 0L)
        if (System.currentTimeMillis() - last < 6L * 60 * 60 * 1000) return

        val cm = getSystemService(ConnectivityManager::class.java)
        if (cm == null || cm.activeNetwork == null || cm.isActiveNetworkMetered) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.NIGHTLY)
                prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Log.w("SiphonApp", "Engine auto-update failed", e)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}