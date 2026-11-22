package com.lonerxone.siphon.data.engine

import android.content.Context
import android.webkit.MimeTypeMap
import com.lonerxone.siphon.data.db.entity.EngineType
import com.lonerxone.siphon.data.engine.model.DownloadProgress
import com.lonerxone.siphon.data.engine.model.DownloadRequest
import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aria2cEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadEngine {

    override val engineType: EngineType = EngineType.ARIA2C

    private val activeStreams = ConcurrentHashMap<String, Boolean>() // downloadId -> isCancelled/isPaused

    private val defaultUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "svg")
    private val videoExts = setOf("mp4", "m4v", "webm", "mkv", "mov", "avi")
    private val audioExts = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            var conn = open(url, "HEAD")
            var code = conn.responseCode
            if (code !in 200..299) {
                // some servers refuse HEAD requests
                conn.disconnect()
                conn = open(url, "GET")
                code = conn.responseCode
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("Server returned HTTP $code")
            }

            val contentLength = conn.contentLengthLong
            val contentType = (conn.contentType ?: "").substringBefore(";").trim().lowercase()
            val disposition = conn.getHeaderField("Content-Disposition")
            conn.disconnect()

            if (contentType.startsWith("text/html")) {
                throw IOException("This link is a web page, not a file")
            }

            val filename = withExtension(baseNameFor(url, disposition), contentType)
            val ext = filename.substringAfterLast('.', "bin").lowercase()
            val kind = kindOf(contentType, ext)
            val label = when (kind) {
                "IMAGE" -> "Image ($ext)"
                "VIDEO" -> "Video ($ext)"
                "AUDIO" -> "Audio ($ext)"
                else -> "File ($ext)"
            }

            val format = MediaFormat(
                formatId = "direct",
                label = label,
                ext = ext,
                fileSizeApprox = if (contentLength > 0L) contentLength else 0L,
                isAudioOnly = kind == "AUDIO"
            )

            Result.success(
                MediaMetadata(
                    url = url,
                    title = filename,
                    thumbnailUrl = if (kind == "IMAGE") url else null,
                    durationSeconds = 0L,
                    site = extractDomain(url),
                    formats = listOf(format),
                    isDirectFile = true
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Failed to inspect link: ${e.message}", e))
        }
    }

    override suspend fun startDownload(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        activeStreams[request.id] = false

        try {
            if (!request.outputDir.exists()) {
                request.outputDir.mkdirs()
            }

            val base = request.customFileName ?: baseNameFor(request.url, null)
            val part = File(request.outputDir, "$base.part")

            val headers = LinkedHashMap<String, String>()
            headers["User-Agent"] = defaultUserAgent
            headers["Accept"] = "*/*"
            RequestHeaderRegistry.get(request.url)?.forEach { (name, value) ->
                if (value.isNotBlank()) headers[name] = value
            }

            val result = HttpDownloader.download(
                url = request.url,
                headers = headers,
                partFile = part,
                isCancelled = { activeStreams[request.id] == true },
                onProgress = { done, total, speed ->
                    val percent = if (total > 0L) done * 100f / total else 0f
                    val eta = if (speed > 0L && total > done) (total - done) / speed else 0L
                    onProgress(
                        DownloadProgress(
                            progressPercent = percent,
                            bytesDownloaded = done,
                            totalBytes = total,
                            speedBps = speed,
                            etaSeconds = eta
                        )
                    )
                }
            )

            val finalName = withExtension(
                baseNameFor(request.url, result.contentDisposition, request.customFileName),
                result.contentType
            )
            val finalFile = File(request.outputDir, finalName)
            if (finalFile.exists()) finalFile.delete()
            if (!part.renameTo(finalFile)) throw IOException("Could not finish the file")

            onProgress(
                DownloadProgress(
                    progressPercent = 100f,
                    bytesDownloaded = result.bytes,
                    totalBytes = result.bytes,
                    speedBps = 0L,
                    etaSeconds = 0L
                )
            )

            Result.success(finalFile)
        } catch (e: DownloadPausedException) {
            Result.failure(Exception("Download paused by user"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeStreams.remove(request.id)
        }
    }

    override suspend fun pause(id: String): Boolean {
        activeStreams[id] = true
        return true
    }

    override suspend fun resume(id: String): Boolean {
        activeStreams[id] = false
        return true
    }

    override suspend fun cancel(id: String): Boolean {
        activeStreams[id] = true
        return true
    }

    // ---------------------------------------------------------------- helpers

    private fun open(url: String, method: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", defaultUserAgent)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
            }
            RequestHeaderRegistry.get(url)?.forEach { (name, value) ->
                if (value.isNotBlank() &&
                    !name.equals("Accept-Encoding", ignoreCase = true) &&
                    !name.equals("Range", ignoreCase = true)
                ) {
                    conn.setRequestProperty(name, value)
                }
            }

            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirect without a location")
                current = URL(URL(current), location).toString()
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects")
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().take(120)
    }

    private fun baseNameFor(url: String, contentDisposition: String?, custom: String? = null): String {
        if (!custom.isNullOrBlank()) return custom
        if (!contentDisposition.isNullOrBlank()) {
            val matcher = Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE)
                .matcher(contentDisposition)
            if (matcher.find()) {
                val match = matcher.group(1)
                if (!match.isNullOrBlank()) {
                    val decoded = try {
                        URLDecoder.decode(match, "UTF-8")
                    } catch (e: Exception) {
                        match
                    }
                    return sanitize(decoded)
                }
            }
        }
        val path = try {
            URI(url).path ?: ""
        } catch (e: Exception) {
            ""
        }
        val last = path.substringAfterLast('/', "")
        val decoded = try {
            URLDecoder.decode(last, "UTF-8")
        } catch (e: Exception) {
            last
        }
        return if (decoded.isNotBlank()) sanitize(decoded) else "download"
    }

    private fun withExtension(name: String, contentType: String): String {
        val currentExt = name.substringAfterLast('.', "")
        val looksLikeExt = currentExt.length in 2..5 && currentExt.all { it.isLetterOrDigit() }
        if (looksLikeExt) return name
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType)
        return if (!ext.isNullOrBlank()) "$name.$ext" else if (name.contains('.')) name else "$name.bin"
    }

    private fun kindOf(contentType: String, ext: String): String {
        return when {
            contentType.startsWith("image/") || ext in imageExts -> "IMAGE"
            contentType.startsWith("video/") || ext in videoExts -> "VIDEO"
            contentType.startsWith("audio/") || ext in audioExts -> "AUDIO"
            else -> "FILE"
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return "Direct File"
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            "Direct File"
        }
    }
}