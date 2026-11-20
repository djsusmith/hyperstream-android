package com.hyperstream.downloadmanager.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hyperstream.downloadmanager.browser.ClipboardLinkMonitor
import com.hyperstream.downloadmanager.browser.ExternalDownloadRequest
import com.hyperstream.downloadmanager.browser.LinkKind
import com.hyperstream.downloadmanager.browser.YouTubeWebViewExtractor
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.service.HyperStreamDownloadService

class MainActivity : ComponentActivity() {
    private lateinit var repository: MdmRepository
    private var clipboardMonitor: ClipboardLinkMonitor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YouTubeWebViewExtractor.attachHost(this)
        repository = MdmRepository.forContext(this)

        runCatching {
            startService(Intent(this, HyperStreamDownloadService::class.java))
        }

        clipboardMonitor = ClipboardLinkMonitor(this) { parsed ->
            if (repository.uiState.value.settings.clipboardMonitor) {
                routeLink(parsed.uri, parsed.kind)
            }
        }.also {
            runCatching { it.start() }
        }

        // Only handle the launch intent once (not again after the activity is recreated).
        if (savedInstanceState == null) {
            com.hyperstream.downloadmanager.core.MetaPixelTracker.trackAppOpen()
            handleSharedIntent(intent)
        }

        setContent {
            HyperStreamApp(repository = repository)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Forwarded via EXTRA_SHARED_URL from LinkCatcherActivity
        val forwardedUrl = intent.getStringExtra(EXTRA_SHARED_URL)?.takeIf { it.isNotBlank() }
        if (forwardedUrl != null) {
            val kind = intent.getStringExtra(EXTRA_SHARED_KIND)
                ?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                ?: LinkKind.GENERIC_URL
            val req = ExternalDownloadRequest(
                url = forwardedUrl,
                kind = kind,
                fileName = intent.getStringExtra(EXTRA_SHARED_FILENAME),
                mimeType = intent.getStringExtra(EXTRA_SHARED_MIME),
                userAgent = intent.getStringExtra(EXTRA_SHARED_USER_AGENT),
                referer = intent.getStringExtra(EXTRA_SHARED_REFERER),
                cookie = intent.getStringExtra(EXTRA_SHARED_COOKIE),
                isExternalBrowserHandoff = intent.getBooleanExtra(EXTRA_IS_BROWSER_HANDOFF, false)
            )
            intent.removeExtra(EXTRA_SHARED_URL)
            intent.removeExtra(EXTRA_SHARED_KIND)
            intent.removeExtra(EXTRA_SHARED_FILENAME)
            intent.removeExtra(EXTRA_SHARED_MIME)
            intent.removeExtra(EXTRA_SHARED_USER_AGENT)
            intent.removeExtra(EXTRA_SHARED_REFERER)
            intent.removeExtra(EXTRA_SHARED_COOKIE)
            intent.removeExtra(EXTRA_IS_BROWSER_HANDOFF)
            routeRequest(req)
            return
        }

        // 2. Direct ACTION_SEND or ACTION_VIEW received by MainActivity
        val req = com.hyperstream.downloadmanager.browser.LinkParser.extractExternalDownloadRequest(intent)
        if (req != null) {
            routeRequest(req)
        }
    }

    /** Torrents are added directly; browser file handoffs open Add Download pre-filled; social/share opens format picker. */
    private fun routeLink(url: String, kind: LinkKind) {
        routeRequest(ExternalDownloadRequest(url = url, kind = kind))
    }

    private fun routeRequest(req: ExternalDownloadRequest) {
        val platform = com.hyperstream.downloadmanager.browser.LinkParser.detectPlatformName(req.url)
        com.hyperstream.downloadmanager.core.MetaPixelTracker.trackShareReceived(req.url, platform)
        when {
            req.kind == LinkKind.MAGNET || req.kind == LinkKind.TORRENT_FILE -> {
                repository.addTorrent(req.url, sequential = true)
                Toast.makeText(this, "HyperStream: torrent added", Toast.LENGTH_SHORT).show()
            }
            req.isExternalBrowserHandoff && req.kind != LinkKind.VIDEO_STREAM_PAGE -> {
                repository.setPendingExternalDownload(req)
            }
            else -> repository.setPendingSharedUrl(req.url)
        }
    }

    override fun onResume() {
        super.onResume()
        YouTubeWebViewExtractor.attachHost(this)
    }

    override fun onDestroy() {
        runCatching { clipboardMonitor?.stop() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SHARED_URL = "com.hyperstream.downloadmanager.extra.SHARED_URL"
        const val EXTRA_SHARED_KIND = "com.hyperstream.downloadmanager.extra.SHARED_KIND"
        const val EXTRA_SHARED_FILENAME = "com.hyperstream.downloadmanager.extra.SHARED_FILENAME"
        const val EXTRA_SHARED_MIME = "com.hyperstream.downloadmanager.extra.SHARED_MIME"
        const val EXTRA_SHARED_USER_AGENT = "com.hyperstream.downloadmanager.extra.SHARED_USER_AGENT"
        const val EXTRA_SHARED_REFERER = "com.hyperstream.downloadmanager.extra.SHARED_REFERER"
        const val EXTRA_SHARED_COOKIE = "com.hyperstream.downloadmanager.extra.SHARED_COOKIE"
        const val EXTRA_IS_BROWSER_HANDOFF = "com.hyperstream.downloadmanager.extra.IS_BROWSER_HANDOFF"
    }
}
