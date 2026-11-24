package com.lonerxone.siphon.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONTokener

data class SniffedMedia(val url: String, val kind: String, val referer: String)

/** What the browser screen shows. */
object BrowserState {
    var currentUrl by mutableStateOf("")
    var urlText by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var pageTitle by mutableStateOf("")
    var showStart by mutableStateOf(true)
    var openFoundSheet by mutableStateOf(false)
    var userAgent by mutableStateOf("")
    val found = mutableStateListOf<SniffedMedia>()

    fun addFound(url: String, kind: String, referer: String) {
        if (found.size >= 80) return
        // the same file with a different signature in the address counts once
        val key = url.substringBefore("?")
        if (found.any { it.url.substringBefore("?") == key }) return
        found.add(SniffedMedia(url, kind, referer))
    }

    fun reset() {
        currentUrl = ""
        urlText = ""
        progress = 100
        canGoBack = false
        canGoForward = false
        pageTitle = ""
        showStart = true
        openFoundSheet = false
        found.clear()
    }
}

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

private val junkMarkers = listOf(
    "doubleclick", "googlesyndication", "adservice", "/ads/", "adsystem",
    "analytics", "tracking", "beacon", "pixel", "telemetry", "/ping"
)

private fun classify(url: String): String? {
    val lower = url.substringBefore("#").lowercase()
    if (junkMarkers.any { lower.contains(it) }) return null
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

private fun errorHtml(url: String, reason: String): String {
    val safeUrl = url.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val safeReason = reason.replace("<", "&lt;").replace(">", "&gt;")
    return """<html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="background:#000;color:#F5F5F7;font-family:sans-serif;padding:28px"><h2 style="margin-top:48px">Couldn't open this page</h2><p style="color:#9A9AA3">$safeReason</p><p style="color:#5C5C64;word-break:break-all;font-size:12px">$safeUrl</p><p><a href="$safeUrl" style="color:#F5F5F7">Try again</a></p></body></html>"""
}

/**
 * Owns the single WebView. The browser screen plugs it in; leaving the tab resets the session.
 */
object BrowserHolder {
    private var webView: WebView? = null
    private var lastPageUrl = ""
    private var clearHistoryPending = false

    fun current(): WebView? = webView

    /** The address of the page that is really open right now. */
    fun liveUrl(): String {
        val url = webView?.url
        return if (!url.isNullOrBlank() && url != "about:blank") url else BrowserState.currentUrl
    }

    /** Creates the web engine early, in idle time, so the first visit to the Browser tab is fast. */
    fun warmUp(appContext: Context) {
        if (webView == null) {
            webView = createWebView(MutableContextWrapper(appContext))
        }
    }

    fun obtain(context: Context): WebView {
        val existing = webView
        if (existing != null) {
            (existing.context as? MutableContextWrapper)?.baseContext = context
            return existing
        }
        val created = createWebView(MutableContextWrapper(context))
        webView = created
        return created
    }

    fun detach(appContext: Context) {
        val view = webView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        (view.context as? MutableContextWrapper)?.baseContext = appContext
    }

    /** Leaving the Browser tab: forget the page, the history and everything that was detected. */
    fun resetSession() {
        BrowserState.reset()
        lastPageUrl = ""
        val view = webView ?: return
        view.stopLoading()
        clearHistoryPending = true
        view.loadUrl("about:blank")
    }

    private fun scanPage(view: WebView) {
        view.evaluateJavascript(SCAN_JS) { raw ->
            val text = try {
                (JSONTokener(raw).nextValue() as? String) ?: ""
            } catch (e: Exception) {
                ""
            }
            val referer = view.url ?: ""
            text.split("\n").forEach { line ->
                val idx = line.indexOf('|')
                if (idx > 0) BrowserState.addFound(line.substring(idx + 1), line.substring(0, idx), referer)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val view = WebView(context)
        view.setBackgroundColor(Color.BLACK)

        val s = view.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.setSupportMultipleWindows(false)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        BrowserState.userAgent = s.userAgentString ?: ""

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(view, true)

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val req = request ?: return false
                val uri = req.url
                val scheme = uri.scheme?.lowercase().orEmpty()

                if (scheme == "http" || scheme == "https" || scheme == "about" ||
                    scheme == "data" || scheme == "blob" || scheme == "javascript"
                ) {
                    return false
                }

                if (scheme == "intent") {
                    val fallback = try {
                        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                            .getStringExtra("browser_fallback_url")
                    } catch (e: Exception) {
                        null
                    }
                    if (!fallback.isNullOrBlank()) view?.loadUrl(fallback)
                    return true
                }

                // app links such as snssdk1340:// or market:// cannot be opened here: ignore them
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                if (url != null && view != null) {
                    val kind = classify(url)
                    if (kind != null) {
                        val referer = request.requestHeaders["Referer"] ?: view.url ?: ""
                        view.post { BrowserState.addFound(url, kind, referer) }
                    }
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && url != "about:blank") {
                    lastPageUrl = url
                    BrowserState.currentUrl = url
                    BrowserState.urlText = url
                    BrowserState.showStart = false
                    BrowserState.found.clear()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view == null) return
                if (url == "about:blank") {
                    if (clearHistoryPending) {
                        view.clearHistory()
                        clearHistoryPending = false
                    }
                    BrowserState.canGoBack = false
                    BrowserState.canGoForward = false
                    return
                }
                scanPage(view)
                view.postDelayed({ scanPage(view) }, 2500)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && view != null) {
                    val failing = request.url.toString()
                    if (failing.startsWith("http")) {
                        val reason = error?.description?.toString().orEmpty()
                        view.loadDataWithBaseURL(failing, errorHtml(failing, reason), "text/html", "UTF-8", failing)
                    }
                }
            }

            /** Also fires when a site changes page without a real reload (YouTube, TikTok, X...). */
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (url == "about:blank") {
                    BrowserState.canGoBack = false
                    BrowserState.canGoForward = false
                    return
                }
                BrowserState.canGoBack = view?.canGoBack() == true
                BrowserState.canGoForward = view?.canGoForward() == true

                if (url != null && !isReload && url != lastPageUrl) {
                    lastPageUrl = url
                    BrowserState.currentUrl = url
                    BrowserState.urlText = url
                    BrowserState.showStart = false
                    // a new page starts with an empty list
                    BrowserState.found.clear()
                    view?.postDelayed({ view.let { scanPage(it) } }, 1200)
                }
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                BrowserState.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                BrowserState.pageTitle = title ?: ""
            }
        }

        view.setDownloadListener { url, _, _, _, _ ->
            view.post {
                BrowserState.addFound(url, "FILE", BrowserState.currentUrl)
                BrowserState.openFoundSheet = true
            }
        }

        return view
    }
}