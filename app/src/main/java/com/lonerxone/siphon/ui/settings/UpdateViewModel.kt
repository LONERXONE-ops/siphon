package com.lonerxone.siphon.ui.settings

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonerxone.siphon.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

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

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        checkForUpdate()
    }

    private fun isMetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm?.isActiveNetworkMetered ?: true
    }

    fun checkForUpdate() {
        val current = _state.value
        if (current is UpdateUiState.Checking || current is UpdateUiState.Downloading) return

        viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            try {
                val body: String? = withContext(Dispatchers.IO) {
                    val conn = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 15000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "SIPHON-Updater")
                    }
                    try {
                        val code = conn.responseCode
                        if (code == 200) {
                            conn.inputStream.bufferedReader().use { it.readText() }
                        } else if (code == 404) {
                            null
                        } else {
                            throw IllegalStateException("GitHub returned HTTP $code")
                        }
                    } finally {
                        conn.disconnect()
                    }
                }

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
                } else {
                    _state.value = UpdateUiState.UpToDate(BuildConfig.VERSION_CODE)
                }
            } catch (e: Exception) {
                _state.value = UpdateUiState.Error(describe("Could not check for updates", e))
            }
        }
    }

    fun downloadUpdate() {
        val available = _state.value as? UpdateUiState.Available ?: return

        viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(0)
            try {
                val file = withContext(Dispatchers.IO) { downloadApk(available) }
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

    private fun openWithRedirects(startUrl: String): HttpURLConnection {
        var url = startUrl
        repeat(6) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 45000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SIPHON-Updater")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IllegalStateException("Redirect without a location")
                url = URL(URL(url), location).toString()
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("HTTP $code")
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun downloadApk(available: UpdateUiState.Available): File {
        val dir = File(context.cacheDir, "update")
        dir.deleteRecursively()
        dir.mkdirs()
        val target = File(dir, "siphon-${available.buildNumber}.apk")

        val conn = openWithRedirects(available.url)
        try {
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else available.sizeBytes
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
                        val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = UpdateUiState.Downloading(percent)
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            conn.disconnect()
        }

        if (target.length() < 1_000_000L) {
            throw IllegalStateException("The downloaded file is too small to be the app")
        }
        val info = context.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
            ?: throw IllegalStateException("The downloaded file is not a valid app package")
        if (info.packageName != context.packageName) {
            throw IllegalStateException("The downloaded file belongs to a different app")
        }
        return target
    }

    fun installUpdate(path: String) {
        val file = File(path)
        if (!file.exists()) {
            _state.value = UpdateUiState.Error("Update file is missing. Check for updates again.")
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