package com.hyperstream.downloadmanager.browser

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object VideoGrabberMediaTracker {
    fun isSniffableMediaUrl(url: String): Boolean {
        val lower = url.lowercase().substringBefore('?')
        return lower.endsWith(".m3u8") ||
            lower.endsWith(".mpd") ||
            lower.endsWith(".mp4") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".mkv") ||
            lower.contains("/videoplayback")
    }

    fun onInterceptedUrl(
        reqUrl: String?,
        detectedMediaUrls: MutableList<String>,
        mainPoster: (() -> Unit) -> Unit
    ) {
        if (reqUrl != null && isSniffableMediaUrl(reqUrl)) {
            mainPoster {
                if (!detectedMediaUrls.contains(reqUrl)) {
                    detectedMediaUrls.add(0, reqUrl)
                }
            }
        }
    }
}

@Composable
fun VideoGrabberWebView(
    initialUrl: String,
    onDownloadDetectedMedia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFullscreen by remember { mutableStateOf(false) }
    var currentUrl by remember(initialUrl) {
        mutableStateOf(initialUrl.ifBlank { "https://m.youtube.com" })
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                VideoGrabberBrowserContent(
                    startUrl = currentUrl,
                    isFullscreen = true,
                    onUrlChanged = { currentUrl = it },
                    onToggleFullscreen = { isFullscreen = false },
                    onDownloadDetectedMedia = { url ->
                        isFullscreen = false
                        onDownloadDetectedMedia(url)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        VideoGrabberBrowserContent(
            startUrl = currentUrl,
            isFullscreen = false,
            onUrlChanged = { currentUrl = it },
            onToggleFullscreen = { isFullscreen = true },
            onDownloadDetectedMedia = onDownloadDetectedMedia,
            modifier = modifier
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun VideoGrabberBrowserContent(
    startUrl: String,
    isFullscreen: Boolean,
    onUrlChanged: (String) -> Unit,
    onToggleFullscreen: () -> Unit,
    onDownloadDetectedMedia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val detectedMediaUrls = remember { mutableStateListOf<String>() }
    var currentPageUrl by remember { mutableStateOf(startUrl) }
    var urlInput by remember(startUrl) { mutableStateOf(startUrl) }
    var loadProgress by remember { mutableIntStateOf(100) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun navigateToInput() {
        val raw = urlInput.trim()
        if (raw.isBlank()) return
        val target = when {
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
            raw.contains('.') && !raw.contains(' ') -> "https://$raw"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(raw)}"
        }
        urlInput = target
        currentPageUrl = target
        onUrlChanged(target)
        webViewRef?.loadUrl(target)
    }

    fun scrollPageBy(deltaPx: Int) {
        webViewRef?.let { wv ->
            wv.evaluateJavascript("window.scrollBy({top: $deltaPx, behavior: 'smooth'});", null)
            if (deltaPx > 0) wv.pageDown(false) else wv.pageUp(false)
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
    ) {
        // Navigation + Address + Scroll + Fullscreen Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        placeholder = { Text("Enter URL or search...", style = MaterialTheme.typography.labelSmall) },
                        textStyle = MaterialTheme.typography.labelSmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateToInput() }),
                        trailingIcon = {
                            IconButton(onClick = { navigateToInput() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "Go", modifier = Modifier.size(16.dp))
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    )

                    // Quick 1-tap Page Scroll Up / Down buttons
                    IconButton(
                        onClick = { scrollPageBy(-500) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll Up", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { scrollPageBy(500) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll Down", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen Browser",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = true
                        isScrollbarFadingEnabled = false
                        isNestedScrollingEnabled = true

                        // Prevent parent Compose LazyColumn / ModalBottomSheet from stealing vertical scroll gestures!
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val scheme = request?.url?.scheme?.lowercase() ?: return false
                                // Block fb://, intent://, market:// etc. so social pages stay inside WebView
                                return scheme != "http" && scheme != "https"
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString()
                                VideoGrabberMediaTracker.onInterceptedUrl(
                                    reqUrl = reqUrl,
                                    detectedMediaUrls = detectedMediaUrls,
                                    mainPoster = { block -> view?.post(block) }
                                )
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null) {
                                    currentPageUrl = url
                                    urlInput = url
                                    onUrlChanged(url)
                                }
                                view?.evaluateJavascript(VIDEO_DOM_SNIFFER_JS, null)
                            }
                        }
                        setDownloadListener { url, _, _, _, _ ->
                            onDownloadDetectedMedia(url)
                        }
                        webViewRef = this
                        loadUrl(startUrl)
                    }
                }
            )

            val bestCandidate = detectedMediaUrls.firstOrNull() ?: currentPageUrl
            ExtendedFloatingActionButton(
                onClick = { onDownloadDetectedMedia(bestCandidate) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Download Video"
                    )
                },
                text = {
                    val badge = if (detectedMediaUrls.isNotEmpty()) {
                        "Download Video (${detectedMediaUrls.size} streams)"
                    } else {
                        "Analyze & Download Page Media"
                    }
                    Text(badge)
                }
            )
        }
    }
}

private const val VIDEO_DOM_SNIFFER_JS = """
(function() {
    const videos = document.querySelectorAll('video, source');
    videos.forEach(v => {
        if (v.src && v.src.startsWith('http')) {
            console.log('MDM_VIDEO_DETECTED:' + v.src);
        }
    });
})();
"""
