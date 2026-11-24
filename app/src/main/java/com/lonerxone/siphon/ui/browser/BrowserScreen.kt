package com.lonerxone.siphon.ui.browser

import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lonerxone.siphon.data.engine.RequestHeaderRegistry
import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import com.lonerxone.siphon.ui.components.BrandMark
import com.lonerxone.siphon.ui.components.CircleActionButton
import com.lonerxone.siphon.ui.components.PillTextField
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.formatFileSize
import com.lonerxone.siphon.ui.components.pressable
import com.lonerxone.siphon.ui.home.HomeViewModel
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.OnAccent
import com.lonerxone.siphon.ui.theme.SiphonPrimaryGradient
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val QUICK_SITES = listOf(
    "YouTube" to "https://m.youtube.com",
    "TikTok" to "https://www.tiktok.com",
    "X" to "https://x.com",
    "Instagram" to "https://www.instagram.com",
    "Facebook" to "https://m.facebook.com",
    "Reddit" to "https://www.reddit.com",
    "Pinterest" to "https://www.pinterest.com",
    "Vimeo" to "https://vimeo.com"
)

private val probePermits = Semaphore(3)

private fun hostOf(url: String): String {
    return try {
        val host = Uri.parse(url).host ?: ""
        if (host.startsWith("www.")) host.substring(4) else host
    } catch (e: Exception) {
        ""
    }
}

private fun extensionOf(url: String, kind: String): String {
    val path = url.substringBefore("?").lowercase()
    val ext = path.substringAfterLast('.', "")
    if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) return ext
    return when (kind) {
        "VIDEO" -> "mp4"
        "AUDIO" -> "mp3"
        "IMAGE" -> "jpg"
        "STREAM" -> "mp4"
        else -> "bin"
    }
}

private fun normalizeInput(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(" ") || !t.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(t)
        else -> "https://$t"
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "VIDEO" -> "Video"
    "AUDIO" -> "Audio"
    "STREAM" -> "Stream (HLS / DASH)"
    "IMAGE" -> "Image"
    else -> "File"
}

private fun isVideoSiteUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("tiktok.com") ||
        lower.contains("vimeo.com") || lower.contains("dailymotion.com") || lower.contains("twitch.tv")
}

/** Asks the server how big a found file is, so junk can be told apart from real media. */
private suspend fun probeSize(media: SniffedMedia): String? {
    if (media.kind == "STREAM") return "Stream"
    return probePermits.withPermit {
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL(media.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 4000
                    readTimeout = 4000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", BrowserState.userAgent.ifBlank { "Mozilla/5.0" })
                    if (media.referer.isNotBlank()) setRequestProperty("Referer", media.referer)
                    val cookie = CookieManager.getInstance().getCookie(media.url)
                    if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
                }
                val length = try {
                    conn.contentLengthLong
                } finally {
                    conn.disconnect()
                }
                if (length > 0L) formatFileSize(length) else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    visible: Boolean,
    onOpenDownloads: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val networkState by viewModel.networkState.collectAsState()

    var showFound by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun openUrl(raw: String) {
        val target = normalizeInput(raw)
        BrowserState.showStart = false
        BrowserState.urlText = target
        BrowserHolder.current()?.loadUrl(target)
    }

    fun startDownload(media: SniffedMedia) {
        val headers = HashMap<String, String>()
        headers["Referer"] = media.referer.ifBlank { BrowserHolder.liveUrl() }
        if (BrowserState.userAgent.isNotBlank()) headers["User-Agent"] = BrowserState.userAgent
        val cookie = CookieManager.getInstance().getCookie(media.url)
        if (!cookie.isNullOrBlank()) headers["Cookie"] = cookie

        val direct = media.kind != "STREAM"
        RequestHeaderRegistry.put(media.url, headers, direct)

        val ext = extensionOf(media.url, media.kind)
        val format = MediaFormat(
            formatId = if (direct) "direct" else "best",
            label = kindLabel(media.kind) + " ($ext)",
            ext = ext,
            fileSizeApprox = 0L,
            isAudioOnly = media.kind == "AUDIO"
        )
        val meta = MediaMetadata(
            url = media.url,
            title = BrowserState.pageTitle.ifBlank { hostOf(media.url) }.take(80),
            thumbnailUrl = if (media.kind == "IMAGE") media.url else null,
            durationSeconds = 0L,
            site = hostOf(media.url),
            formats = listOf(format),
            isDirectFile = direct
        )
        viewModel.onDownloadNowClicked(meta, format.formatId, format.label)
        if (networkState.isWifi) toast = "Added to downloads"
    }

    BackHandler(enabled = visible && (BrowserState.canGoBack || !BrowserState.showStart)) {
        val web = BrowserHolder.current()
        if (!BrowserState.showStart && web != null && web.canGoBack()) {
            web.goBack()
        } else {
            BrowserState.showStart = true
        }
    }

    // Leaving the tab forgets the page, the history and everything that was detected
    LaunchedEffect(visible) {
        val view = BrowserHolder.current()
        if (visible) {
            view?.onResume()
            view?.resumeTimers()
        } else {
            showFound = false
            toast = null
            BrowserHolder.resetSession()
            view?.onPause()
            view?.pauseTimers()
        }
    }

    LaunchedEffect(BrowserState.openFoundSheet) {
        if (BrowserState.openFoundSheet) {
            showFound = true
            BrowserState.openFoundSheet = false
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3500)
            toast = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // address bar
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
            PillTextField(
                value = BrowserState.urlText,
                onValueChange = { BrowserState.urlText = it },
                placeholder = "Search or type a link",
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    Icon(
                        imageVector = if (BrowserState.currentUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                onImeAction = {
                    if (BrowserState.urlText.isNotBlank()) openUrl(BrowserState.urlText)
                }
            )
        }

        if (BrowserState.progress < 100 && !BrowserState.showStart) {
            LinearProgressIndicator(
                progress = { BrowserState.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = TextPrimary,
                trackColor = DarkBorder
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        // page
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val web = BrowserHolder.obtain(ctx)
                    (web.parent as? ViewGroup)?.removeView(web)
                    web
                }
            )

            if (BrowserState.showStart) {
                StartPage(onOpen = { openUrl(it) })
            }

            // floating "found media" button
            AnimatedVisibility(
                visible = BrowserState.found.isNotEmpty() && !BrowserState.showStart,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .pressable(onClick = { showFound = true })
                        .clip(RoundedCornerShape(28.dp))
                        .background(SiphonPrimaryGradient)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = OnAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${BrowserState.found.size} found",
                        color = OnAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            val message = toast
            if (message != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkElevated)
                        .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            toast = null
                            onOpenDownloads()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = message, color = TextPrimary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "VIEW",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // bottom toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolIcon(Icons.Default.Home, "Start page", true) { BrowserState.showStart = true }
            ToolIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", BrowserState.canGoBack) {
                BrowserHolder.current()?.goBack()
            }
            ToolIcon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", BrowserState.canGoForward) {
                BrowserHolder.current()?.goForward()
            }
            ToolIcon(Icons.Default.Refresh, "Reload", !BrowserState.showStart) {
                BrowserHolder.current()?.reload()
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .pressable(enabled = !BrowserState.showStart, onClick = {
                        val live = BrowserHolder.liveUrl()
                        if (live.isNotBlank()) viewModel.fetchLinkInfo(live)
                    })
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (BrowserState.showStart) DarkElevated else SiphonPrimaryGradientBrushColor())
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (BrowserState.showStart) TextMuted else OnAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Inspect page",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (BrowserState.showStart) TextMuted else OnAccent
                )
            }
        }
    }

    if (showFound) {
        ModalBottomSheet(
            onDismissRequest = { showFound = false },
            containerColor = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Found on this page",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${BrowserState.found.size} items",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (BrowserState.found.isEmpty()) {
                    Text(
                        text = if (isVideoSiteUrl(BrowserHolder.liveUrl())) {
                            "Videos on this site are read by Inspect page. Open a video, then tap Inspect page."
                        } else {
                            "Nothing yet. Press play on the video or scroll the page, then open this again."
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(BrowserState.found.toList(), key = { it.url }) { media ->
                            FoundRow(
                                media = media,
                                onDownload = {
                                    startDownload(media)
                                    showFound = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiphonPrimaryGradientBrushColor() = androidx.compose.ui.graphics.Color.White

@Composable
private fun ToolIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .pressable(enabled = enabled, onClick = onClick)
            .size(42.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandMark(boxSize = 64.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Browse. Detect. Download.",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Open any site. SIPHON spots the media on the page for you.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepCard(Icons.Default.Language, "Open", "Visit a site", Modifier.weight(1f))
                StepCard(Icons.Default.PlayArrow, "Detect", "Play or scroll", Modifier.weight(1f))
                StepCard(Icons.Default.Download, "Save", "Tap the badge", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "Quick sites",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(QUICK_SITES.chunked(4)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (name, url) ->
                    SiteTile(name = name, onClick = { onOpen(url) }, modifier = Modifier.weight(1f))
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            SiphonCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tip",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "For one video on YouTube, TikTok or X, open it and tap Inspect page. For media hidden inside a page, play it and use the Found button.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StepCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(DarkElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = subtitle, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun SiteTile(name: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DarkSurface)
                .border(1.dp, DarkBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = 11.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FoundRow(media: SniffedMedia, onDownload: () -> Unit) {
    val sizeText by produceState<String?>(initialValue = null, media.url) {
        value = probeSize(media)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkElevated)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface),
            contentAlignment = Alignment.Center
        ) {
            if (media.kind == "IMAGE") {
                AsyncImage(
                    model = media.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (media.kind == "AUDIO") Icons.Default.MusicNote else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = kindLabel(media.kind),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                val size = sizeText
                if (size != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = size, fontSize = 11.sp, color = TextSecondary)
                }
            }
            Text(
                text = hostOf(media.url) + "  " + media.url.substringAfterLast('/').substringBefore('?'),
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        CircleActionButton(
            icon = Icons.Default.Download,
            description = "Download",
            filled = true,
            size = 38.dp,
            onClick = onDownload
        )
    }
}