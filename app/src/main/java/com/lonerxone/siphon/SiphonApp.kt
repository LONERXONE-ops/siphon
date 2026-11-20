package com.lonerxone.siphon

import android.app.Application
import android.util.Log
import com.lonerxone.siphon.data.db.dao.DownloadDao
import com.lonerxone.siphon.data.repository.WaitlistScheduler
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.ffmpeg.FFmpeg
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SiphonApp : Application() {

    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var scheduler: WaitlistScheduler

    override fun onCreate() {
        super.onCreate()

        // Initialize youtubedl-android, FFmpeg, and native tools
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            Log.e("SiphonApp", "Failed to initialize native download engines", e)
        }

        // Background startup recovery & scheduler start
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Normalize any stuck QUEUED/DOWNLOADING items to WAITING
                downloadDao.normalizeStuckDownloads()

                // Start observing connectivity changes for waitlist
                scheduler.startListening()
            } catch (e: Exception) {
                Log.e("SiphonApp", "Error during startup initialization", e)
            }
        }
    }
}
