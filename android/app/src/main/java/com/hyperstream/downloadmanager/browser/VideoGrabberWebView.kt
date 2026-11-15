package com.hyperstream.downloadmanager.browser

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoGrabberWebView(
    initialUrl: String,
    onDownloadDetectedMedia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val detectedMediaUrls = remember { mutableStateListOf<String>() }
    var currentPageUrl by remember { mutableStateOf(initialUrl) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString()
                            if (reqUrl != null && isSniffableMediaUrl(reqUrl)) {
                                if (!detectedMediaUrls.contains(reqUrl)) {
                                    detectedMediaUrls.add(0, reqUrl)
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null) {
                                currentPageUrl = url
                            }
                            view?.evaluateJavascript(VIDEO_DOM_SNIFFER_JS, null)
                        }
                    }
                    setDownloadListener { url, _, _, _, _ ->
                        onDownloadDetectedMedia(url)
                    }
                    loadUrl(initialUrl)
                }
            }
        )

        val bestCandidate = detectedMediaUrls.firstOrNull() ?: currentPageUrl
        ExtendedFloatingActionButton(
            onClick = { onDownloadDetectedMedia(bestCandidate) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
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
                    "Download Video / Page Media"
                }
                Text(badge)
            }
        )
    }
}

private fun isSniffableMediaUrl(url: String): Boolean {
    val lower = url.lowercase().substringBefore('?')
    return lower.endsWith(".m3u8") ||
        lower.endsWith(".mpd") ||
        lower.endsWith(".mp4") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mkv") ||
        lower.contains("/videoplayback")
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
