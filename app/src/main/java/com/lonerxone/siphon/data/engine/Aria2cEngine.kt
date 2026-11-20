package com.lonerxone.siphon.data.engine

import android.content.Context
import com.lonerxone.siphon.data.db.entity.EngineType
import com.lonerxone.siphon.data.engine.model.DownloadProgress
import com.lonerxone.siphon.data.engine.model.DownloadRequest
import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.regex.Pattern
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aria2cEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadEngine {

    override val engineType: EngineType = EngineType.ARIA2C

    private val activeStreams = ConcurrentHashMap<String, Boolean>() // downloadId -> isCancelled/isPaused

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 Siphon/2.0")
            }
            connection.connect()

            val contentLength = connection.contentLengthLong
            val contentType = connection.contentType ?: "application/octet-stream"
            val disposition = connection.getHeaderField("Content-Disposition")
            var filename = extractFilename(url, disposition)

            val ext = filename.substringAfterLast('.', "bin")
            val domain = extractDomain(url)

            val format = MediaFormat(
                formatId = "direct",
                label = "Direct File ($ext)",
                ext = ext,
                fileSizeApprox = contentLength
            )

            Result.success(
                MediaMetadata(
                    url = url,
                    title = filename,
                    durationSeconds = 0L,
                    site = domain,
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

            val targetFileName = request.customFileName ?: extractFilename(request.url, null)
            val outputFile = File(request.outputDir, targetFileName)
            val controlFile = File(request.outputDir, "$targetFileName.aria2")

            var downloadedBytes = 0L
            if (outputFile.exists()) {
                downloadedBytes = outputFile.length()
            }

            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Mozilla/5.0 Siphon/2.0")
                if (downloadedBytes > 0) {
                    // Resume range request
                    setRequestProperty("Range", "bytes=$downloadedBytes-")
                }
            }
            connection.connect()

            val responseCode = connection.responseCode
            val isResumed = responseCode == HttpURLConnection.HTTP_PARTIAL
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IllegalStateException("Server returned HTTP $responseCode")
            }

            val totalBytes = if (isResumed) {
                downloadedBytes + connection.contentLengthLong
            } else {
                downloadedBytes = 0L
                connection.contentLengthLong
            }

            // Write control file to mark partial download
            if (!controlFile.exists()) {
                controlFile.writeText("siphon_aria2_control:${request.url}")
            }

            val inputStream: InputStream = connection.inputStream
            val outputStream = if (isResumed && downloadedBytes > 0) {
                FileOutputStream(outputFile, true) // append mode for resume
            } else {
                FileOutputStream(outputFile, false)
            }

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var lastUpdate = System.currentTimeMillis()
            var lastBytes = downloadedBytes

            outputStream.use { out ->
                inputStream.use { inp ->
                    while (inp.read(buffer).also { bytesRead = it } != -1) {
                        if (activeStreams[request.id] == true) {
                            // Paused / Cancelled
                            out.flush()
                            return@withContext Result.failure(Exception("Download paused by user"))
                        }

                        out.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 500) {
                            val timeDelta = (now - lastUpdate) / 1000.0
                            val bytesDelta = downloadedBytes - lastBytes
                            val speed = if (timeDelta > 0) (bytesDelta / timeDelta).toLong() else 0L
                            val remainingBytes = if (totalBytes > downloadedBytes) totalBytes - downloadedBytes else 0L
                            val eta = if (speed > 0) remainingBytes / speed else 0L
                            val percent = if (totalBytes > 0) (downloadedBytes * 100f / totalBytes) else 0f

                            onProgress(
                                DownloadProgress(
                                    progressPercent = percent,
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speedBps = speed,
                                    etaSeconds = eta
                                )
                            )

                            lastUpdate = now
                            lastBytes = downloadedBytes
                        }
                    }
                    out.flush()
                }
            }

            // Remove aria2 control file after successful download
            if (controlFile.exists()) {
                controlFile.delete()
            }

            Result.success(outputFile)
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

    private fun extractFilename(url: String, contentDisposition: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            val pattern = Pattern.compile("filename=\"?([^\";]+)\"?")
            val matcher = pattern.matcher(contentDisposition)
            if (matcher.find()) {
                val match = matcher.group(1)
                if (!match.isNullOrBlank()) return match
            }
        }
        val uri = URI(url)
        val path = uri.path
        val lastSegment = path.substringAfterLast('/', "")
        return if (lastSegment.isNotBlank()) lastSegment else "download.bin"
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
