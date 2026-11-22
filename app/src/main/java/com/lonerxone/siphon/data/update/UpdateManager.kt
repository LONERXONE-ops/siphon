package com.lonerxone.siphon.data.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lonerxone.siphon.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val buildNumber: Int) : UpdateUiState
    data class Available(
        val buildNumber: Int,
        val sizeBytes: Long,
        val url: String,
        val metered: Boolean
    ) : UpdateUiState
    data class Downloading(val percent: Int) : UpdateUiState
    data class ReadyToInstall(val path: String) : UpdateUiState
    data class NeedsPermission(val path: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private fun isMetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm?.isActiveNetworkMetered ?: true
    }

    /** Looks for a newer build. With [autoDownload], it also fetches it right away on Wi-Fi. */
    fun check(autoDownload: Boolean = false) {
        val current = _state.value
        if (current is UpdateUiState.Checking || current is UpdateUiState.Downloading) return
        if (current is UpdateUiState.ReadyToInstall) return

        scope.launch {
            _state.value = UpdateUiState.Checking
            try {
                val body = fetchReleaseJson()
                if (body == null) {
                    _state.value = UpdateUiState.Error("No update has been published yet.")
                    return@launch
                }

                val assets = JSONObject(body).getJSONArray("assets")
                val pattern = Regex("""siphon-(\d+)\.apk""")
                var found: UpdateUiState.Available? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val match = pattern.matchEntire(asset.optString("name")) ?: continue
                    found = UpdateUiState.Available(
                        buildNumber = match.groupValues[1].toInt(),
                        sizeBytes = asset.optLong("size"),
                        url = asset.getString("browser_download_url"),
                        metered = isMetered()
                    )
                    break
                }

                val release = found
                if (release == null) {
                    _state.value = UpdateUiState.Error("The latest release has no APK attached.")
                } else if (release.buildNumber > BuildConfig.VERSION_CODE) {
                    _state.value = release
                    if (autoDownload && !release.metered) {
                        download()
                    }
                } else {
                    _state.value = UpdateUiState.UpToDate(BuildConfig.VERSION_CODE)
                }
            } catch (e: Exception) {
                _state.value = UpdateUiState.Error(describe("Could not check for updates", e))
            }
        }
    }

    private fun fetchReleaseJson(): String? {
        val conn = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SIPHON-Updater")
        }
        try {
            val code = conn.responseCode
            return when (code) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> throw IllegalStateException("GitHub returned HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun download() {
        val available = _state.value as? UpdateUiState.Available ?: return

        scope.launch {
            _state.value = UpdateUiState.Downloading(0)
            try {
                val file = downloadApk(available)
                _state.value = UpdateUiState.ReadyToInstall(file.absolutePath)
            } catch (e: Exception) {
                _state.value = UpdateUiState.Error(describe("Download failed", e))
            }
        }
    }

    private fun describe(prefix: String, e: Exception): String {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        return "$prefix: $detail"
    }

    private class Probe(val url: String, val total: Long, val rangeOk: Boolean)

    /** Follows redirects to the real download address and checks if the server allows ranges. */
    private fun probe(startUrl: String): Probe {
        var url = startUrl
        repeat(6) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SIPHON-Updater")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Range", "bytes=0-0")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirect without a location")
                url = URL(URL(url), location).toString()
            } else if (code == 206) {
                val range = conn.getHeaderField("Content-Range") ?: ""
                conn.disconnect()
                val total = range.substringAfterLast('/').toLongOrNull() ?: -1L
                return Probe(url, total, total > 0L)
            } else if (code in 200..299) {
                val length = conn.contentLengthLong
                conn.disconnect()
                return Probe(url, length, false)
            } else {
                conn.disconnect()
                throw IOException("HTTP $code")
            }
        }
        throw IOException("Too many redirects")
    }

    private fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < 1_000_000L) return false
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        return info.packageName == context.packageName
    }

    private fun downloadApk(available: UpdateUiState.Available): File {
        val dir = File(context.cacheDir, "update")
        val target = File(dir, "siphon-${available.buildNumber}.apk")

        // A finished download from an earlier launch is reused
        if (isValidApk(target)) return target

        dir.deleteRecursively()
        dir.mkdirs()

        val probe = probe(available.url)
        val total = if (probe.total > 0L) probe.total else available.sizeBytes

        if (probe.rangeOk && total > 4_000_000L) {
            parallelDownload(probe.url, total, target)
        } else {
            singleDownload(probe.url, total, target)
        }

        if (!isValidApk(target)) {
            throw IllegalStateException("The downloaded file is not a valid app package")
        }
        return target
    }

    private fun singleDownload(url: String, total: Long, target: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 45000
            setRequestProperty("User-Agent", "SIPHON-Updater")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = if (total > 0L) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = UpdateUiState.Downloading(percent)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Four connections each fetch a quarter of the file at the same time. */
    private fun parallelDownload(url: String, total: Long, target: File) {
        val parts = 4
        val chunk = total / parts
        val done = AtomicLong(0L)
        val lastPercent = AtomicInteger(-1)

        RandomAccessFile(target, "rw").use { it.setLength(total) }

        val pool = Executors.newFixedThreadPool(parts)
        try {
            val futures = (0 until parts).map { i ->
                val start = i * chunk
                val end = if (i == parts - 1) total - 1 else start + chunk - 1
                pool.submit(Callable {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000
                        readTimeout = 45000
                        setRequestProperty("User-Agent", "SIPHON-Updater")
                        setRequestProperty("Accept-Encoding", "identity")
                        setRequestProperty("Range", "bytes=$start-$end")
                    }
                    try {
                        if (conn.responseCode != 206) {
                            throw IOException("The server refused a partial request (HTTP ${conn.responseCode})")
                        }
                        RandomAccessFile(target, "rw").use { raf ->
                            raf.seek(start)
                            conn.inputStream.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    raf.write(buffer, 0, read)
                                    val soFar = done.addAndGet(read.toLong())
                                    val percent = ((soFar * 100) / total).toInt().coerceIn(0, 100)
                                    if (lastPercent.getAndSet(percent) != percent) {
                                        _state.value = UpdateUiState.Downloading(percent)
                                    }
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                })
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }

        if (target.length() != total) throw IOException("File size mismatch after download")
    }

    /** Opens the installer for the downloaded update. */
    fun install() {
        val path = when (val s = _state.value) {
            is UpdateUiState.ReadyToInstall -> s.path
            is UpdateUiState.NeedsPermission -> s.path
            else -> return
        }
        val file = File(path)
        if (!file.exists()) {
            _state.value = UpdateUiState.Error("The update file is missing. Check for updates again.")
            return
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            _state.value = UpdateUiState.NeedsPermission(path)
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = UpdateUiState.Error(describe("Could not open the installer", e))
        }
    }

    fun openReleasePage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    private companion object {
        const val RELEASE_URL = "https://api.github.com/repos/LONERXONE-ops/siphon/releases/tags/latest"
        const val RELEASE_PAGE = "https://github.com/LONERXONE-ops/siphon/releases/tag/latest"
    }
}