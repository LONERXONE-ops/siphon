package com.lonerxone.siphon.data.engine

import android.content.Context
import com.lonerxone.siphon.data.db.entity.EngineType
import com.lonerxone.siphon.data.engine.model.DownloadProgress
import com.lonerxone.siphon.data.engine.model.DownloadRequest
import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
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

private class DirectSource(
    val url: String,
    val headers: Map<String, String>,
    val fileName: String
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

    private val activeProcesses = ConcurrentHashMap<String, String>()
    private val directConnections = ConcurrentHashMap<String, HttpURLConnection>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val metadataCache = ConcurrentHashMap<String, Pair<Long, MediaMetadata>>()

    // ---------------------------------------------------------------- metadata

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> {
        // the same link inspected again within 5 minutes is answered instantly
        val cached = metadataCache[url]
        if (cached != null && System.currentTimeMillis() - cached.first < 5L * 60 * 1000) {
            return Result.success(cached.second)
        }
        val result = fetchMetadataUncached(url)
        result.getOrNull()?.let { metadataCache[url] = Pair(System.currentTimeMillis(), it) }
        return result
    }

    private suspend fun fetchMetadataUncached(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            val siteName = extractDomain(url)

            // If the site changed and the engine can't read it, update the engine once and retry
            val rawText = try {
                runInfo(url)
            } catch (e: Exception) {
                runInfoRecover(url, e)
            }

            val jsonStart = rawText.indexOf('{')
            if (jsonStart < 0) throw IllegalStateException("No media information found for this link.")
            val jsonText = rawText.substring(jsonStart)
            val root = JSONObject(jsonText)

            // Keep the info so the download step does not need to extract the page again
            saveInfoFile(url, jsonText)

            val title = root.str("title").ifBlank { "Media Download" }
            val thumbnail = root.str("thumbnail").ifBlank { null } ?: run {
                val thumbs = root.optJSONArray("thumbnails")
                var picked: String? = null
                if (thumbs != null) {
                    for (i in thumbs.length() - 1 downTo 0) {
                        val u = thumbs.optJSONObject(i)?.str("url") ?: ""
                        if (u.startsWith("http")) {
                            picked = u
                            break
                        }
                    }
                }
                picked
            }
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

    private fun runInfo(url: String): String {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("--force-ipv4")
        }
        val response = YoutubeDL.getInstance().execute(
            request = request,
            processId = "info-" + System.nanoTime()
        ) { _, _, _ -> }
        return response.out.trim()
    }

    private fun isYoutube(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
        return host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith("youtube-nocookie.com")
    }

    private fun runInfoClient(url: String, client: String): String {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("--force-ipv4")
            addOption("--extractor-args", "youtube:player_client=$client")
        }
        val response = YoutubeDL.getInstance().execute(
            request = request,
            processId = "info-" + System.nanoTime()
        ) { _, _, _ -> }
        return response.out.trim()
    }

    /** When the first attempt fails: update the engine, then try other YouTube clients. */
    private fun runInfoRecover(url: String, first: Exception): String {
        var lastError: Exception = first

        if (isExtractorProblem(first.message) && tryEngineUpdate()) {
            try {
                return runInfo(url)
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (isYoutube(url)) {
            for (client in listOf("ios", "tv", "android_vr", "mweb")) {
                try {
                    return runInfoClient(url, client)
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }

        throw lastError
    }

    private fun isExtractorProblem(message: String?): Boolean {
        if (message == null) return false
        return message.contains("Unable to extract", ignoreCase = true) ||
            message.contains("ExtractorError", ignoreCase = true) ||
            message.contains("yt-dlp -U", ignoreCase = true)
    }

    private fun tryEngineUpdate(): Boolean {
        val prefs = context.getSharedPreferences("siphon_engine", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_heal", 0L) < 15L * 60 * 1000) return false
        prefs.edit().putLong("last_heal", now).apply()
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
            if (status == YoutubeDL.UpdateStatus.DONE) {
                prefs.edit().putLong("last_update", now).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun infoDir(): File {
        val dir = File(context.cacheDir, "info")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun saveInfoFile(url: String, json: String) {
        try {
            val dir = infoDir()
            val cutoff = System.currentTimeMillis() - 6L * 60 * 60 * 1000
            dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            File(dir, sha1(url) + ".json").writeText(json)
        } catch (e: Exception) {
        }
    }

    private fun loadInfoFile(url: String): File? {
        val file = File(infoDir(), sha1(url) + ".json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 3L * 60 * 60 * 1000
        return if (fresh) file else null
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
                    label = "Audio only (original)",
                    ext = audioExt,
                    fileSizeApprox = audioChoice.size,
                    isAudioOnly = true
                )
            )
            if (!audioExt.equals("mp3", ignoreCase = true)) {
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

    // ---------------------------------------------------------------- download

    override suspend fun startDownload(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        pausedIds.remove(request.id)
        activeProcesses[request.id] = request.id

        try {
            if (!request.outputDir.exists()) {
                request.outputDir.mkdirs()
            }

            val fid = request.formatId ?: "bv*+ba/b"
            val infoFile = loadInfoFile(request.url)

            // Attempt 1: plain HTTP download of a direct file (fastest, no Python)
            if (infoFile != null) {
                val source = directSourceFor(infoFile, fid)
                if (source != null) {
                    try {
                        val file = downloadDirectFast(request, source, onProgress)
                        return@withContext Result.success(file)
                    } catch (e: Exception) {
                        if (pausedIds.contains(request.id)) {
                            return@withContext Result.failure(Exception("Paused"))
                        }
                    }
                }
            }

            // Attempt 2: yt-dlp with the saved info and multi-connection aria2c
            // Attempt 3: yt-dlp doing everything itself
            val attempts = if (infoFile != null) listOf(infoFile, null) else listOf<File?>(null)
            var lastError: Exception? = null
            for (attemptInfo in attempts) {
                try {
                    val file = runYtDlp(request, fid, attemptInfo, onProgress)
                    return@withContext Result.success(file)
                } catch (e: Exception) {
                    lastError = e
                    if (pausedIds.contains(request.id)) break
                }
            }

            val error = lastError ?: IllegalStateException("Download failed")
            Result.failure(Exception(parseErrorMessage(error), error))
        } finally {
            activeProcesses.remove(request.id)
            directConnections.remove(request.id)
        }
    }

    private fun directSourceFor(infoFile: File, formatId: String): DirectSource? {
        if (formatId.contains("+") || formatId.contains("/") || formatId.startsWith("audio:")) return null
        return try {
            val root = JSONObject(infoFile.readText())
            if (root.str("extractor_key").lowercase().contains("youtube")) return null

            var chosen: JSONObject? = null
            val arr = root.optJSONArray("formats")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.str("format_id") == formatId) {
                        chosen = o
                        break
                    }
                }
            }
            val src: JSONObject = chosen ?: (if (formatId == "best") root else return null)

            val mediaUrl = src.str("url")
            val protocol = src.str("protocol").ifBlank { "https" }
            if (!mediaUrl.startsWith("http")) return null
            if (protocol != "https" && protocol != "http") return null
            if (src.has("fragments")) return null

            val headers = LinkedHashMap<String, String>()
            val hdr = src.optJSONObject("http_headers") ?: root.optJSONObject("http_headers")
            if (hdr != null) {
                val keys = hdr.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    headers[k] = hdr.optString(k)
                }
            }
            val cookies = src.str("cookies")
            if (cookies.isNotBlank()) headers["Cookie"] = cookies

            val ext = src.str("ext").ifBlank { root.str("ext").ifBlank { "mp4" } }
            val title = root.str("title").ifBlank { "download" }
            val id = root.str("id")
            val safeTitle = title.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").take(100).trim()
            val name = if (id.isNotBlank()) "$safeTitle [$id].$ext" else "$safeTitle.$ext"
            DirectSource(mediaUrl, headers, name)
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadDirect(
        request: DownloadRequest,
        source: DirectSource,
        onProgress: (DownloadProgress) -> Unit
    ): File {
        val target = File(request.outputDir, source.fileName)
        val part = File(request.outputDir, source.fileName + ".part")
        var startAt = if (part.exists()) part.length() else 0L

        val conn = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = true
            source.headers.forEach { (k, v) ->
                if (!k.equals("Accept-Encoding", ignoreCase = true)) setRequestProperty(k, v)
            }
            setRequestProperty("Accept-Encoding", "identity")
            if (startAt > 0L) setRequestProperty("Range", "bytes=$startAt-")
        }
        directConnections[request.id] = conn

        try {
            val code = conn.responseCode
            if (code != 200 && code != 206) {
                if (code == 416) part.delete()
                throw IOException("HTTP $code")
            }
            val append = code == 206 && startAt > 0L
            if (!append) startAt = 0L

            val remaining = conn.contentLengthLong
            val total = if (remaining > 0L) remaining + startAt else 0L
            var done = startAt
            var lastTick = System.currentTimeMillis()
            var lastBytes = done

            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 500L) {
                            val speed = ((done - lastBytes) * 1000L) / (now - lastTick)
                            val pct = if (total > 0L) done * 100f / total else 0f
                            val eta = if (speed > 0L && total > 0L) (total - done) / speed else 0L
                            onProgress(
                                DownloadProgress(
                                    progressPercent = pct,
                                    bytesDownloaded = done,
                                    totalBytes = total,
                                    speedBps = speed,
                                    etaSeconds = eta
                                )
                            )
                            lastTick = now
                            lastBytes = done
                        }
                    }
                }
            }

            if (total > 0L && done < total) throw IOException("Connection closed early")
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IOException("Could not finish the file")
            onProgress(
                DownloadProgress(
                    progressPercent = 100f,
                    bytesDownloaded = done,
                    totalBytes = done,
                    speedBps = 0L,
                    etaSeconds = 0L
                )
            )
            return target
        } finally {
            directConnections.remove(request.id)
            conn.disconnect()
        }
    }

    private fun downloadDirectFast(
        request: DownloadRequest,
        source: DirectSource,
        onProgress: (DownloadProgress) -> Unit
    ): File {
        val target = File(request.outputDir, source.fileName)
        val part = File(request.outputDir, source.fileName + ".part")

        val result = HttpDownloader.download(
            url = source.url,
            headers = source.headers,
            partFile = part,
            isCancelled = { pausedIds.contains(request.id) },
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

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("Could not finish the file")
        onProgress(
            DownloadProgress(
                progressPercent = 100f,
                bytesDownloaded = result.bytes,
                totalBytes = result.bytes,
                speedBps = 0L,
                etaSeconds = 0L
            )
        )
        return target
    }

    private fun runYtDlp(
        request: DownloadRequest,
        fid: String,
        infoFile: File?,
        onProgress: (DownloadProgress) -> Unit
    ): File {
        val ytdlRequest = if (infoFile != null) {
            YoutubeDLRequest(emptyList())
        } else {
            YoutubeDLRequest(request.url)
        }

        ytdlRequest.apply {
            if (infoFile != null) {
                addOption("--load-info-json", infoFile.absolutePath)
                addOption("--downloader", "libaria2c.so")
                addOption("--external-downloader-args", "aria2c:-x 16 -s 16 -k 1M --min-split-size=1M --file-allocation=none")
            }

            val outTemplate = File(request.outputDir, "%(title).100s [%(id)s].%(ext)s").absolutePath
            addOption("-o", outTemplate)

            addOption("--no-playlist")
            addOption("-c")
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--socket-timeout", "30")
            addOption("--concurrent-fragments", "16")
            addOption("--force-ipv4")
            addOption("--add-metadata")
            addOption("--no-mtime")
            RequestHeaderRegistry.get(request.url)?.forEach { (headerName, headerValue) ->
                if (headerValue.isNotBlank()) addOption("--add-header", "$headerName:$headerValue")
            }

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
            processId = request.id
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
            val ariaMatch = Regex("""([\d.]+)([KMGT]?i?B)/([\d.]+)([KMGT]?i?B)\((\d+)%\).*?DL:([\d.]+)([KMGT]?i?B)""").find(text)

            var sizeBytes = sizeMatch?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
            var speedBytes = speedMatch?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
            if (ariaMatch != null) {
                sizeBytes = toBytes(ariaMatch.groupValues[3], ariaMatch.groupValues[4])
                speedBytes = toBytes(ariaMatch.groupValues[6], ariaMatch.groupValues[7])
            }

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
                !file.name.endsWith(".aria2") &&
                !intermediate.containsMatchIn(file.name)
        }
        return files?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("Download finished but the output file was not found")
    }

    // ---------------------------------------------------------------- control

    override suspend fun pause(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!activeProcesses.containsKey(id)) return@withContext false
        pausedIds.add(id)
        directConnections[id]?.let {
            try {
                it.disconnect()
            } catch (e: Exception) {
            }
        }
        try {
            // Stops the process but leaves the partial file so it can resume
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Exception) {
        }
        true
    }

    override suspend fun resume(id: String): Boolean {
        // Resume = start again; partial files are continued
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
            msg.contains("Unable to download webpage", ignoreCase = true) ||
                msg.contains("Temporary failure in name resolution", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ||
                msg.contains("Network is unreachable", ignoreCase = true) ->
                "Network problem. Check your connection and try again."
            msg.contains("Unable to extract", ignoreCase = true) ||
                msg.contains("ExtractorError", ignoreCase = true) ->
                "This site changed how it serves videos. SIPHON tried updating its engine. Try the link again in a few minutes."
            msg.contains("No video could be found", ignoreCase = true) ->
                "No video was found here, and no downloadable images either. Try opening the page in the Browser tab."
            msg.contains("Sign in to confirm", ignoreCase = true) ||
                msg.contains("not a bot", ignoreCase = true) ->
                "This site wants a login or a bot check."
            msg.contains("HTTP Error 404", ignoreCase = true) ||
                msg.contains("404 Not Found", ignoreCase = true) ->
                "Link expired or the content was removed (404)."
            msg.contains("HTTP Error 403", ignoreCase = true) ->
                "Access denied (403). The link may have expired."
            msg.contains("No space left on device", ignoreCase = true) ->
                "Not enough free storage space."
            msg.contains("Unsupported URL", ignoreCase = true) ->
                "This site isn't supported yet. Try updating the engine in Settings."
            else -> msg
        }
    }
}