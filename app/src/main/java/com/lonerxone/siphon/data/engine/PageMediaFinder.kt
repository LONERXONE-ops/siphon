package com.lonerxone.siphon.data.engine

import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private class ScanResult(
    val title: String,
    val images: List<String>,
    val videos: List<String>
)

@Singleton
class PageMediaFinder @Inject constructor() {

    private val browserAgent =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private val botAgent = "Mozilla/5.0 (compatible; Twitterbot/1.0)"

    suspend fun find(pageUrl: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            val host = hostOf(pageUrl)
            val images = LinkedHashSet<String>()
            val videos = LinkedHashSet<String>()
            var title = ""

            // X / Twitter posts: ask a public mirror for the media attached to the post
            if (host.endsWith("x.com") || host.endsWith("twitter.com")) {
                val tweet = tweetMedia(pageUrl)
                images.addAll(tweet.images)
                videos.addAll(tweet.videos)
                title = tweet.title
            }

            // everything else: read the page itself
            if (images.isEmpty() && videos.isEmpty()) {
                val scanned = scanPage(pageUrl)
                images.addAll(scanned.images)
                videos.addAll(scanned.videos)
                if (title.isBlank()) title = scanned.title
            }

            val items = mutableListOf<MediaFormat>()
            var n = 0
            for (u in images.take(40)) {
                n++
                items.add(
                    MediaFormat(
                        formatId = "img:$u",
                        label = "Image $n",
                        ext = guessExt(u, "jpg"),
                        fileSizeApprox = 0L
                    )
                )
            }
            n = 0
            for (u in videos.take(10)) {
                n++
                items.add(
                    MediaFormat(
                        formatId = "vid:$u",
                        label = "Video $n",
                        ext = guessExt(u, "mp4"),
                        fileSizeApprox = 0L
                    )
                )
            }

            if (items.isEmpty()) throw IllegalStateException("No images or videos were found on this page.")

            Result.success(
                MediaMetadata(
                    url = pageUrl,
                    title = title.ifBlank { host },
                    thumbnailUrl = images.firstOrNull(),
                    durationSeconds = 0L,
                    site = host,
                    formats = items
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------- X / Twitter

    private class TweetMedia(val images: List<String>, val videos: List<String>, val title: String)

    private fun tweetMedia(pageUrl: String): TweetMedia {
        val empty = TweetMedia(emptyList(), emptyList(), "")
        val id = Regex("""/status(?:es)?/(\d+)""").find(pageUrl)?.groupValues?.get(1) ?: return empty
        val user = Regex("""(?:x|twitter)\.com/([^/?#]+)/status""").find(pageUrl)?.groupValues?.get(1) ?: "i"

        val json = try {
            httpGet("https://api.fxtwitter.com/$user/status/$id", browserAgent)
        } catch (e: Exception) {
            return empty
        }

        return try {
            val tweet = JSONObject(json).optJSONObject("tweet") ?: return empty
            val media = tweet.optJSONObject("media")
            val images = mutableListOf<String>()
            val videos = mutableListOf<String>()

            val photos = media?.optJSONArray("photos")
            if (photos != null) {
                for (i in 0 until photos.length()) {
                    val url = photos.optJSONObject(i)?.optString("url").orEmpty()
                    if (url.startsWith("http")) images.add(originalQuality(url))
                }
            }
            val vids = media?.optJSONArray("videos")
            if (vids != null) {
                for (i in 0 until vids.length()) {
                    val url = vids.optJSONObject(i)?.optString("url").orEmpty()
                    if (url.startsWith("http")) videos.add(url)
                }
            }

            val text = tweet.optString("text").replace("\n", " ").trim().take(80)
            val author = tweet.optJSONObject("author")?.optString("name").orEmpty()
            val title = if (text.isNotBlank()) text else author
            TweetMedia(images, videos, title)
        } catch (e: Exception) {
            empty
        }
    }

    private fun originalQuality(url: String): String {
        return if (url.contains("pbs.twimg.com") && !url.contains("name=")) {
            url + (if (url.contains("?")) "&name=orig" else "?name=orig")
        } else {
            url
        }
    }

    // ---------------------------------------------------------------- generic pages

    private fun scanPage(pageUrl: String): ScanResult {
        val html = try {
            httpGet(pageUrl, browserAgent)
        } catch (e: Exception) {
            ""
        }
        var result = parseHtml(html, pageUrl)

        if (result.images.isEmpty() && result.videos.isEmpty()) {
            // some sites only show their preview tags to link-preview bots
            val botHtml = try {
                httpGet(pageUrl, botAgent)
            } catch (e: Exception) {
                ""
            }
            val botResult = parseHtml(botHtml, pageUrl)
            if (botResult.images.isNotEmpty() || botResult.videos.isNotEmpty()) result = botResult
        }
        return result
    }

    private fun attr(tag: String, name: String): String? {
        val m = Regex("""\b$name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)
        return m?.groupValues?.get(1)
    }

    private fun isJunk(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.substringBefore("?").endsWith(".svg")) return true
        val words = listOf("sprite", "favicon", "/icon", "logo", "pixel", "spacer", "1x1", "blank.", "loading", "avatar", "emoji", "badge", "tracking")
        return words.any { lower.contains(it) }
    }

    private fun parseHtml(html: String, base: String): ScanResult {
        if (html.isBlank()) return ScanResult("", emptyList(), emptyList())

        val images = LinkedHashSet<String>()
        val videos = LinkedHashSet<String>()

        fun resolve(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.trim().replace("&amp;", "&")
            if (cleaned.startsWith("data:") || cleaned.startsWith("javascript:") || cleaned.startsWith("blob:")) return null
            val absolute = try {
                URL(URL(base), cleaned).toString()
            } catch (e: Exception) {
                return null
            }
            return if (absolute.startsWith("http")) absolute else null
        }

        var ogTitle = ""

        for (m in Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)) {
            val tag = m.value
            val key = (attr(tag, "property") ?: attr(tag, "name"))?.lowercase() ?: continue
            val content = attr(tag, "content")
            when (key) {
                "og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src" ->
                    resolve(content)?.let { images.add(it) }
                "og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream" ->
                    resolve(content)?.let { videos.add(it) }
                "og:title" -> if (content != null) ogTitle = content
            }
        }

        for (m in Regex("""<img\s[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)) {
            val tag = m.value
            var src = attr(tag, "data-src") ?: attr(tag, "data-original") ?: attr(tag, "src")
            val srcset = attr(tag, "srcset")
            if (srcset != null) {
                val last = srcset.split(",").lastOrNull()?.trim()?.substringBefore(" ")
                if (!last.isNullOrBlank()) src = last
            }
            val url = resolve(src) ?: continue
            if (!isJunk(url)) images.add(url)
        }

        val videoExts = listOf(".mp4", ".webm", ".m4v", ".mov")
        for (m in Regex("""<(?:video|source)\s[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)) {
            val url = resolve(attr(m.value, "src")) ?: continue
            val path = url.substringBefore("?").lowercase()
            if (videoExts.any { path.endsWith(it) }) videos.add(url)
        }

        val pageTitle = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.trim().orEmpty()
        val title = (if (ogTitle.isNotBlank()) ogTitle else pageTitle)
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").take(100)

        return ScanResult(title, images.toList().take(40), videos.toList().take(10))
    }

    private fun guessExt(url: String, fallback: String): String {
        val path = url.substringBefore("?").lowercase()
        val ext = path.substringAfterLast('.', "")
        val known = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "mp4", "webm", "m4v", "mov")
        if (ext in known) return ext
        val format = Regex("""format=([a-z0-9]+)""").find(url.lowercase())?.groupValues?.get(1)
        return if (format != null && format in known) format else fallback
    }

    private fun hostOf(url: String): String {
        return try {
            val host = URL(url).host ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            ""
        }
    }

    private fun httpGet(url: String, userAgent: String): String {
        var current = url
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirect without a location")
                current = URL(URL(current), location).toString()
            } else if (code in 200..299) {
                val out = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (total < 2_000_000) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                }
                conn.disconnect()
                return out.toString("UTF-8")
            } else {
                conn.disconnect()
                throw IOException("HTTP $code")
            }
        }
        throw IOException("Too many redirects")
    }
}