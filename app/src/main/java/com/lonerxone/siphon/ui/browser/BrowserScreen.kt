package com.lonerxone.siphon.ui.browser

import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lonerxone.siphon.data.engine.RequestHeaderRegistry
import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import com.lonerxone.siphon.ui.components.CircleActionButton
import com.lonerxone.siphon.ui.components.PillTextField
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonSecondaryButton
import com.lonerxone.siphon.ui.home.HomeViewModel
import com.lonerxone.siphon.ui.home.components.AddLinkBottomSheet
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.OnAccent
import com.lonerxone.siphon.ui.theme.PrimaryGradientStart
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary
import kotlinx.coroutines.delay

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenDownloads: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val addSheetState by viewModel.addSheetState.collectAsState()
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
        headers["Referer"] = media.referer.ifBlank { BrowserState.currentUrl }
        val agent = BrowserHolder.current()?.settings?.userAgentString
        if (!agent.isNullOrBlank()) headers["User-Agent"] = agent
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

    BackHandler(enabled = BrowserState.canGoBack || !BrowserState.showStart) {
        val web = BrowserHolder.current()
        if (!BrowserState.showStart && web != null && web.canGoBack()) {
            web.goBack()
        } else {
            BrowserState.showStart = true
        }
    }

    // a download link tapped inside a page opens the found list
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillTextField(
                        value = BrowserState.urlText,
                        onValueChange = { BrowserState.urlText = it },
                        placeholder = "Search or type a link",
                        modifier = Modifier.weight(1f),
                        leading = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        onImeAction = {
                            if (BrowserState.urlText.isNotBlank()) openUrl(BrowserState.urlText)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FoundBadge(count = BrowserState.found.size, onClick = { showFound = true })
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircleActionButton(
                        icon = Icons.Default.Home,
                        description = "Start page",
                        size = 36.dp,
                        onClick = { BrowserState.showStart = true }
                    )
                    CircleActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Back",
                        size = 36.dp,
                        onClick = { BrowserHolder.current()?.goBack() }
                    )
                    CircleActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        description = "Forward",
                        size = 36.dp,
                        onClick = { BrowserHolder.current()?.goForward() }
                    )
                    CircleActionButton(
                        icon = Icons.Default.Refresh,
                        description = "Reload",
                        size = 36.dp,
                        onClick = { BrowserHolder.current()?.reload() }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SiphonSecondaryButton(
                        text = "Inspect page",
                        onClick = {
                            if (BrowserState.currentUrl.isNotBlank()) {
                                viewModel.fetchLinkInfo(BrowserState.currentUrl)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val container = FrameLayout(ctx)
                        val web = BrowserHolder.obtain(ctx)
                        (web.parent as? ViewGroup)?.removeView(web)
                        container.addView(
                            web,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        web.onResume()
                        web.resumeTimers()
                        if (!BrowserState.showStart && web.url == null && BrowserState.currentUrl.isNotBlank()) {
                            web.loadUrl(BrowserState.currentUrl)
                        }
                        container
                    },
                    onRelease = { container ->
                        // leaving the tab: stop sound and timers, but keep the page alive
                        BrowserHolder.current()?.onPause()
                        container.removeAllViews()
                        BrowserHolder.detach(context.applicationContext)
                    }
                )

                if (BrowserState.showStart) {
                    StartPage(onOpen = { openUrl(it) })
                }

                val message = toast
                if (message != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
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
                        text = "Nothing yet. Press play on the video or scroll the page, then open this again.",
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

    AddLinkBottomSheet(
        uiState = addSheetState,
        onDismiss = { viewModel.dismissSheet() },
        onDownloadNow = { meta, formatId, label -> viewModel.onDownloadNowClicked(meta, formatId, label) },
        onDownloadLater = { meta, formatId, label -> viewModel.downloadLater(meta, formatId, label) },
        onConfirmMobileData = { meta, formatId -> viewModel.confirmMobileDataDownload(meta, formatId) },
        onDownloadSelected = { meta, ids, later -> viewModel.onDownloadSelected(meta, ids, later) }
    )
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Browser",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "Find videos, audio and images on any website.",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(18.dp))

            SiphonCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "How it works",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                StepRow("1", "Open a site and play the video, or scroll the page.")
                Spacer(modifier = Modifier.height(10.dp))
                StepRow("2", "SIPHON spots the real media files and shows a count badge next to the address bar.")
                Spacer(modifier = Modifier.height(10.dp))
                StepRow("3", "Tap the badge, pick a file and download. SIPHON sends the same cookies the site gave you, so protected media works.")
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Tip: for a single video on YouTube, TikTok, X and similar sites, tap Inspect page. It is usually faster.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "Quick sites",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        items(QUICK_SITES.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (name, url) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                            .clickable { onOpen(url) }
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(DarkElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FoundBadge(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (count > 0) PrimaryGradientStart else DarkElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (count > 0) {
            Text(
                text = if (count > 99) "99+" else "$count",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OnAccent
            )
        } else {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Found media",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FoundRow(media: SniffedMedia, onDownload: () -> Unit) {
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
            Text(
                text = kindLabel(media.kind),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
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