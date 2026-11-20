package com.lonerxone.siphon.data.engine

import android.content.Context
import com.lonerxone.siphon.data.db.entity.EngineType
import com.lonerxone.siphon.data.engine.model.DownloadProgress
import com.lonerxone.siphon.data.engine.model.DownloadRequest
import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadEngine {

    override val engineType: EngineType = EngineType.YT_DLP

    private val activeProcesses = ConcurrentHashMap<String, String>() // downloadId to processId

    // Regex patterns to parse progress and speed from yt-dlp output if callback stream is parsed
    private val progressPattern = Pattern.compile("\\[download\\]\\s+(\\d+\\.\\d+)%\\s+of\\s+~?(\\d+\\.\\d+)(\\w+)\\s+at\\s+(\\d+\\.\\d+)(\\w+/s)\\s+ETA\\s+(\\d+:\\d+)")

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            val siteName = extractDomain(url)
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
            }

            val videoInfo = YoutubeDL.getInstance().getInfo(request)
            val title = videoInfo.title ?: "Media Download"
            val thumbnail = videoInfo.thumbnail
            val duration = videoInfo.duration.toLong()

            // Map available formats
            val formats = mutableListOf<MediaFormat>()
            val rawFormats = videoInfo.formats
            if (!rawFormats.isNullOrEmpty()) {
                // High quality video with audio
                formats.add(
                    MediaFormat(
                        formatId = "bestvideo+bestaudio/best",
                        label = "Best Quality (Default)",
                        ext = videoInfo.ext ?: "mp4",
                        fileSizeApprox = videoInfo.fileSize
                    )
                )

                // 1080p
                val has1080p = rawFormats.any { (it.height ?: 0) >= 1080 }
                if (has1080p) {
                    formats.add(
                        MediaFormat(
                            formatId = "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
                            label = "1080p Full HD",
                            ext = "mp4",
                            resolution = "1080p"
                        )
                    )
                }

                // 720p
                val has720p = rawFormats.any { (it.height ?: 0) in 720..1079 }
                if (has720p) {
                    formats.add(
                        MediaFormat(
                            formatId = "bestvideo[height<=720]+bestaudio/best[height<=720]",
                            label = "720p HD",
                            ext = "mp4",
                            resolution = "720p"
                        )
                    )
                }

                // 480p
                formats.add(
                    MediaFormat(
                        formatId = "bestvideo[height<=480]+bestaudio/best[height<=480]",
                        label = "480p SD",
                        ext = "mp4",
                        resolution = "480p"
                    )
                )

                // Audio only
                formats.add(
                    MediaFormat(
                        formatId = "bestaudio/best",
                        label = "Audio Only (M4A/MP3)",
                        ext = "m4a",
                        isAudioOnly = true
                    )
                )
            } else {
                // Single default format
                formats.add(
                    MediaFormat(
                        formatId = "best",
                        label = "Default Format",
                        ext = videoInfo.ext ?: "mp4",
                        fileSizeApprox = videoInfo.fileSize
                    )
                )
            }

            Result.success(
                MediaMetadata(
                    url = url,
                    title = title,
                    thumbnailUrl = thumbnail,
                    durationSeconds = duration,
                    site = siteName,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            val userFriendlyMessage = parseErrorMessage(e)
            Result.failure(Exception(userFriendlyMessage, e))
        }
    }

    override suspend fun startDownload(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val processId = request.id
        activeProcesses[request.id] = processId

        try {
            if (!request.outputDir.exists()) {
                request.outputDir.mkdirs()
            }

            val ytdlRequest = YoutubeDLRequest(request.url).apply {
                // Staging output template
                val outTemplate = File(request.outputDir, "%(title)s [%(id)s].%(ext)s").absolutePath
                addOption("-o", outTemplate)

                // Continuous resume from partial .part files
                addOption("-c")
                addOption("--no-part=false")

                // Embed metadata and thumbnails if applicable
                addOption("--add-metadata")
                addOption("--embed-thumbnail")
                addOption("--no-mtime")

                // Format selection
                val format = request.formatId ?: "bestvideo+bestaudio/best"
                addOption("-f", format)

                // Optional speed limit
                if (request.speedLimitKbps > 0) {
                    addOption("-r", "${request.speedLimitKbps}K")
                }
            }

            YoutubeDL.getInstance().execute(
                request = ytdlRequest,
                processId = processId
            ) { progress, etaInSeconds, line ->
                val eta = etaInSeconds.toLong()
                onProgress(
                    DownloadProgress(
                        progressPercent = progress,
                        bytesDownloaded = 0L,
                        totalBytes = 0L,
                        speedBps = 0L,
                        etaSeconds = eta
                    )
                )
            }

            // Find the downloaded file in the staging directory
            val files = request.outputDir.listFiles { file ->
                !file.name.endsWith(".part") && !file.name.endsWith(".ytdl") && file.isFile
            }
            val resultFile = files?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("Download finished but output file not found in staging")

            Result.success(resultFile)
        } catch (e: YoutubeDLException) {
            val friendlyMsg = parseErrorMessage(e)
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: Exception) {
            Result.failure(Exception(parseErrorMessage(e), e))
        } finally {
            activeProcesses.remove(request.id)
        }
    }

    override suspend fun pause(id: String): Boolean = withContext(Dispatchers.IO) {
        val processId = activeProcesses[id] ?: return@withContext false
        try {
            // Destroys child processes cleanly, leaving .part file intact on disk for resume
            YoutubeDL.getInstance().destroyProcessById(processId)
            activeProcesses.remove(id)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun resume(id: String): Boolean {
        // Resume in yt-dlp is executed by re-issuing startDownload with -c (continue)
        return true
    }

    override suspend fun cancel(id: String): Boolean = withContext(Dispatchers.IO) {
        pause(id)
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return "Web"
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            "Web"
        }
    }

    private fun parseErrorMessage(e: Throwable): String {
        val msg = e.message ?: return "Download failed. Please check link."
        return when {
            msg.contains("Sign in to confirm", ignoreCase = true) ||
            msg.contains("bot", ignoreCase = true) ->
                "Site requires authentication or bot verification."
            msg.contains("HTTP Error 404", ignoreCase = true) ||
            msg.contains("404 Not Found", ignoreCase = true) ->
                "Link expired or content removed (404)."
            msg.contains("HTTP Error 403", ignoreCase = true) ->
                "Access forbidden (403). Link may have expired."
            msg.contains("No space left on device", ignoreCase = true) ->
                "No space on SD card."
            msg.contains("ExtractorError", ignoreCase = true) ||
            msg.contains("Unsupported URL", ignoreCase = true) ->
                "Site structure changed. Please update engine in Settings."
            else -> msg
        }
    }
}
