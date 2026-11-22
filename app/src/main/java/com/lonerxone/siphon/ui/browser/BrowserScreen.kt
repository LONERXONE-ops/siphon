package com.lonerxone.siphon.ui.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.json.JSONTokener

private data class SniffedMedia(val url: String, val kind: String, val referer: String)

private val SCAN_JS = """
(function() {
  var out = [];
  function add(kind, u) { if (u && u.indexOf('http') === 0) out.push(kind + '|' + u); }
  document.querySelectorAll('video').forEach(function(v) {
    add('VIDEO', v.currentSrc || v.src);
    v.querySelectorAll('source').forEach(function(s) { add('VIDEO', s.src); });
  });
  document.querySelectorAll('audio').forEach(function(a) { add('AUDIO', a.currentSrc || a.src); });
  document.querySelectorAll('img').forEach(function(i) {
    if ((i.naturalWidth || 0) >= 200 && (i.naturalHeight || 0) >= 200) { add('IMAGE', i.currentSrc || i.src); }
  });
  var og = document.querySelector('meta[property="og:image"]');
  if (og) { add('IMAGE', og.content); }
  return out.join('\n');
})();
"""

private fun classify(url: String): String? {
    val lower = url.substringBefore("#").lowercase()
    val path = lower.substringBefore("?")
    return when {
        path.endsWith(".m3u8") || path.endsWith(".mpd") -> "STREAM"
        path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") ||
            path.endsWith(".mov") || path.endsWith(".m4v") -> "VIDEO"
        path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") ||
            path.endsWith(".ogg") || path.endsWith(".wav") || path.endsWith(".flac") -> "AUDIO"
        else -> null
    }
}

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
fun BrowserScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val addSheetState by viewModel.addSheetState.collectAsState()

    var currentUrl by rememberSaveable { mutableStateOf("https://www.google.com") }
    var urlText by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showFound by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<SniffedMedia>() }

    fun addFound(url: String, kind: String, referer: String) {
        if (found.size >= 80) return
        if (found.none { it.url == url }) found.add(SniffedMedia(url, kind, referer))
    }

    fun scanPage(view: WebView) {
        view.evaluateJavascript(SCAN_JS) { raw ->
            val text = try {
                (JSONTokener(raw).nextValue() as? String) ?: ""
            } catch (e: Exception) {
                ""
            }
            val referer = view.url ?: ""
            text.split("\n").forEach { line ->
                val idx = line.indexOf('|')
                if (idx > 0) addFound(line.substring(idx + 1), line.substring(0, idx), referer)
            }
        }
    }

    fun startDownload(media: SniffedMedia) {
        val headers = HashMap<String, String>()
        headers["Referer"] = media.referer.ifBlank { currentUrl }
        val agent = webViewRef?.settings?.userAgentString
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
            title = pageTitle.ifBlank { hostOf(media.url) }.take(80),
            thumbnailUrl = if (media.kind == "IMAGE") media.url else null,
            durationSeconds = 0L,
            site = hostOf(media.url),
            formats = listOf(format),
            isDirectFile = direct
        )
        viewModel.onDownloadNowClicked(meta, format.formatId, format.label)
    }

    BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

    DisposableEffect(Unit) {
        onDispose { webViewRef?.destroy() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("Search or type a link", color = TextMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = TextSecondary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onGo = {
                        if (urlText.isNotBlank()) webViewRef?.loadUrl(normalizeInput(urlText))
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                FoundBadge(count = found.size, onClick = { showFound = true })
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircleActionButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Back",
                    size = 36.dp,
                    onClick = { webViewRef?.goBack() }
                )
                CircleActionButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    description = "Forward",
                    size = 36.dp,
                    onClick = { webViewRef?.goForward() }
                )
                CircleActionButton(
                    icon = Icons.Default.Refresh,
                    description = "Reload",
                    size = 36.dp,
                    onClick = { webViewRef?.reload() }
                )
                Spacer(modifier = Modifier.weight(1f))
                SiphonSecondaryButton(
                    text = "Inspect page",
                    onClick = { viewModel.fetchLinkInfo(currentUrl) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (progress < 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = TextPrimary,
                trackColor = DarkBorder
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString()
                            if (url != null && view != null) {
                                val kind = classify(url)
                                if (kind != null) {
                                    val referer = request.requestHeaders["Referer"] ?: view.url ?: ""
                                    view.post { addFound(url, kind, referer) }
                                }
                            }
                            return null
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (url != null) {
                                currentUrl = url
                                urlText = url
                            }
                            found.clear()
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (view != null) {
                                scanPage(view)
                                view.postDelayed({ scanPage(view) }, 2500)
                            }
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            canGoBack = view?.canGoBack() == true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title ?: ""
                        }
                    }

                    setDownloadListener { url, _, _, _, _ ->
                        post {
                            addFound(url, "FILE", currentUrl)
                            showFound = true
                        }
                    }

                    webViewRef = this
                    loadUrl(currentUrl)
                }
            }
        )
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
                    text = "${found.size} items",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (found.isEmpty()) {
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
                        items(found.toList(), key = { it.url }) { media ->
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
private fun FoundBadge(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (count > 0) PrimaryGradientStart else DarkElevated)
            .clickableNoRipple(onClick),
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

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    return this.then(Modifier.clickable { onClick() })
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