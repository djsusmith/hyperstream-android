package com.hyperstream.downloadmanager.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hyperstream.downloadmanager.browser.ClipboardLinkMonitor
import com.hyperstream.downloadmanager.browser.LinkKind
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.service.HyperStreamDownloadService

class MainActivity : ComponentActivity() {
    private lateinit var repository: MdmRepository
    private var clipboardMonitor: ClipboardLinkMonitor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val url = intent?.getStringExtra(EXTRA_SHARED_URL)?.takeIf { it.isNotBlank() } ?: return
        val kind = intent.getStringExtra(EXTRA_SHARED_KIND)
            ?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
            ?: LinkKind.GENERIC_URL
        intent.removeExtra(EXTRA_SHARED_URL)
        intent.removeExtra(EXTRA_SHARED_KIND)
        routeLink(url, kind)
    }

    /** Torrents are added directly; every other link opens the format & quality picker. */
    private fun routeLink(url: String, kind: LinkKind) {
        when (kind) {
            LinkKind.MAGNET, LinkKind.TORRENT_FILE -> {
                repository.addTorrent(url, sequential = true)
                Toast.makeText(this, "HyperStream: torrent added", Toast.LENGTH_SHORT).show()
            }
            else -> repository.setPendingSharedUrl(url)
        }
    }

    override fun onDestroy() {
        runCatching { clipboardMonitor?.stop() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SHARED_URL = "com.hyperstream.downloadmanager.extra.SHARED_URL"
        const val EXTRA_SHARED_KIND = "com.hyperstream.downloadmanager.extra.SHARED_KIND"
    }
}
