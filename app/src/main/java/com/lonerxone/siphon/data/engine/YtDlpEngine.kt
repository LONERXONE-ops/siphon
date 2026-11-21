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
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private data class Fmt(
    val id: String,
    val ext: String,
    val shortSide: Int,
    val bitrate: Double,
    val vcodec: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val size: Long
)

private fun JSONObject.str(name: String): String {
    val s = optString(name, "")
    return if (s == "null") "" else s
}

@Singleton
class YtDlpEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadEngine {

    override val engineType: EngineType = EngineType.YT_DLP

    private val activeProcesses = ConcurrentHashMap<String, String>() // downloadId to processId

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            val siteName = extractDomain(url)
            val infoId = "info-" + System.nanoTime()
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
            }

            val response = YoutubeDL.getInstance().execute(request = request, processId = infoId) { _, _, _ -> }
            val text = response.out.trim()
            val jsonStart = text.indexOf('{')
            if (jsonStart < 0) throw IllegalStateException("No media information found for this link.")
            val root = JSONObject(text.substring(jsonStart))

            val title = root.str("title").ifBlank { "Media Download" }
            val thumbnail = root.str("thumbnail").ifBlank { null }
            val durationRaw = root.optDouble("duration", 0.0)
            val duration = if (durationRaw.isNaN()) 0L else durationRaw.toLong()
            val rootExt = root.str("ext").ifBlank { "mp4" }
            val rootExact = root.optLong("filesize", 0L)
            val rootSize = if (rootExact > 0L) rootExact else root.optLong("filesize_approx", 0L)

            val formats = buildFormatOptions(root, duration).ifEmpty {
                listOf(
                    MediaFormat(
                        formatId = "best",
                        label = "Original quality",
                        ext = rootExt,
                        fileSizeApprox = rootSize
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
            Result.failure(Exception(parseErrorMessage(e), e))
        }
    }

    private fun buildFormatOptions(root: JSONObject, durationSeconds: Long): List<MediaFormat> {
        val parsed = mutableListOf<Fmt>()
        val arr = root.optJSONArray("formats")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val id = f.str("format_id")
                if (id.isBlank()) continue
                if (f.str("protocol").startsWith("mhtml")) continue
                if (f.str("format_note").equals("storyboard", ignoreCase = true)) continue

                val vcodecRaw = f.str("vcodec")
                val acodecRaw = f.str("acodec")
                val height = f.optInt("height", 0)
                val width = f.optInt("width", 0)
                val hasVideo = vcodecRaw != "none" && (vcodecRaw.isNotBlank() || height > 0)
                val hasAudio = acodecRaw != "none" && (acodecRaw.isNotBlank() || hasVideo)
                if (!hasVideo && !hasAudio) continue

                val shortSide = if (width > 0 && height > 0) minOf(width, height) else height
                val tbrRaw = f.optDouble("tbr", 0.0)
                val abrRaw = f.optDouble("abr", 0.0)
                val tbr = if (tbrRaw.isNaN()) 0.0 else tbrRaw
                val abr = if (abrRaw.isNaN()) 0.0 else abrRaw
                val bitrate = maxOf(tbr, abr)

                val exact = f.optLong("filesize", 0L)
                val approx = f.optLong("filesize_approx", 0L)
                val estimated = if (durationSeconds > 0L && bitrate > 0.0) {
                    (bitrate * 1000.0 / 8.0 * durationSeconds).toLong()
                } else {
                    0L
                }
                val size = if (exact > 0L) exact else if (approx > 0L) approx else estimated

                parsed.add(Fmt(id, f.str("ext"), shortSide, bitrate, vcodecRaw, hasVideo, hasAudio, size))
            }
        }

        val videos = parsed.filter { it.hasVideo }
        val audios = parsed.filter { it.hasAudio && !it.hasVideo }
        val bestAudio = audios
            .sortedWith(compareByDescending<Fmt> { it.ext == "m4a" }.thenByDescending { it.bitrate })
            .firstOrNull()

        val standard = listOf(2160, 1440, 1080, 720, 480, 360, 240)
        fun bucketOf(side: Int): Int = standard.firstOrNull { side >= it * 0.9 } ?: 240
        val groups = videos.groupBy { bucketOf(it.shortSide) }

        val options = mutableListOf<MediaFormat>()
        for (bucket in standard) {
            val group = groups[bucket] ?: continue
            val best = group
                .sortedWith(compareByDescending<Fmt> { it.vcodec.startsWith("avc1") }.thenByDescending { it.bitrate })
                .first()
            val audio = bestAudio
            val needsAudio = !best.hasAudio && audio != null
            val selector = if (needsAudio && audio != null) best.id + "+" + audio.id else best.id
            val totalSize = best.size + (if (needsAudio && audio != null) audio.size else 0L)
            options.add(
                MediaFormat(
                    formatId = selector,
                    label = bucketLabel(bucket),
                    ext = best.ext.ifBlank { "mp4" },
                    fileSizeApprox = totalSize,
                    resolution = "${bucket}p"
                )
            )
        }

        val audioChoice = bestAudio
        if (audioChoice != null) {
            val audioExt = audioChoice.ext.ifBlank { "m4a" }
            options.add(
                MediaFormat(
                    formatId = audioChoice.id,
                    label = "Audio only",
                    ext = audioExt,
                    fileSizeApprox = audioChoice.size,
                    isAudioOnly = true
                )
            )
            options.add(
                MediaFormat(
                    formatId = "audio:mp3",
                    label = "Audio only (MP3)",
                    ext = "mp3",
                    fileSizeApprox = audioChoice.size,
                    isAudioOnly = true
                )
            )
        }

        return options
    }

    private fun bucketLabel(bucket: Int): String = when (bucket) {
        2160 -> "4K Ultra HD"
        1440 -> "1440p QHD"
        1080 -> "1080p Full HD"
        720 -> "720p HD"
        480 -> "480p"
        360 -> "360p"
        else -> "${bucket}p"
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
                val outTemplate = File(request.outputDir, "%(title).100s [%(id)s].%(ext)s").absolutePath
                addOption("-o", outTemplate)

                addOption("--no-playlist")
                addOption("-c")
                addOption("--add-metadata")
                addOption("--no-mtime")

                val fid = request.formatId ?: "bv*+ba/b"
                if (fid == "audio:mp3") {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                } else {
                    val formatArg = if (fid.contains("/")) fid else fid + "/bv*+ba/b"
                    addOption("-f", formatArg)
                }

                if (request.speedLimitKbps > 0) {
                    addOption("-r", "${request.speedLimitKbps}K")
                }
            }

            YoutubeDL.getInstance().execute(
                request = ytdlRequest,
                processId = processId
            ) { progress, etaInSeconds, line ->
                val eta = etaInSeconds.toLong()
                val text = line ?: ""
                fun toBytes(n: String, u: String): Long {
                    val v = n.toDoubleOrNull() ?: return 0L
                    val mul = when (u.uppercase().take(1)) {
                        "K" -> 1024.0
                        "M" -> 1024.0 * 1024.0
                        "G" -> 1024.0 * 1024.0 * 1024.0
                        "T" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
                        else -> 1.0
                    }
                    return (v * mul).toLong()
                }
                val sizeMatch = Regex("""of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)""").find(text)
                val speedMatch = Regex("""at\s+([\d.]+)\s*([KMGT]?i?B)/s""").find(text)
                val sizeBytes = sizeMatch?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
                val speedBytes = speedMatch?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
                onProgress(
                    DownloadProgress(
                        progressPercent = progress,
                        bytesDownloaded = (sizeBytes * progress / 100.0).toLong(),
                        totalBytes = sizeBytes,
                        speedBps = speedBytes,
                        etaSeconds = eta
                    )
                )
            }

            val intermediate = Regex("""\.f[\w-]+\.\w+$""")
            val files = request.outputDir.listFiles { file ->
                file.isFile &&
                    !file.name.endsWith(".part") &&
                    !file.name.endsWith(".ytdl") &&
                    !file.name.endsWith(".temp") &&
                    !intermediate.containsMatchIn(file.name)
            }
            val resultFile = files?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("Download finished but the output file was not found")

            Result.success(resultFile)
        } catch (e: YoutubeDLException) {
            Result.failure(Exception(parseErrorMessage(e), e))
        } catch (e: Exception) {
            Result.failure(Exception(parseErrorMessage(e), e))
        } finally {
            activeProcesses.remove(request.id)
        }
    }

    override suspend fun pause(id: String): Boolean = withContext(Dispatchers.IO) {
        val processId = activeProcesses[id] ?: return@withContext false
        try {
            // Stops the process but leaves the partial file on disk so it can resume
            YoutubeDL.getInstance().destroyProcessById(processId)
            activeProcesses.remove(id)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun resume(id: String): Boolean {
        // Resume is done by starting the download again; yt-dlp continues from the partial file
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
        val msg = e.message ?: return "Download failed. Please check the link."
        return when {
            msg.contains("Sign in to confirm", ignoreCase = true) ||
                msg.contains("bot", ignoreCase = true) ->
                "This site requires a login or bot check."
            msg.contains("HTTP Error 404", ignoreCase = true) ||
                msg.contains("404 Not Found", ignoreCase = true) ->
                "Link expired or the content was removed (404)."
            msg.contains("HTTP Error 403", ignoreCase = true) ->
                "Access denied (403). The link may have expired."
            msg.contains("No space left on device", ignoreCase = true) ->
                "Not enough free storage space."
            msg.contains("ExtractorError", ignoreCase = true) ||
                msg.contains("Unsupported URL", ignoreCase = true) ->
                "This site isn't supported yet. Try updating the engine in Settings."
            else -> msg
        }
    }
}