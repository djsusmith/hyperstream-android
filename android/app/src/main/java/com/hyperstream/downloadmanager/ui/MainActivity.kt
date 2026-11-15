package com.hyperstream.downloadmanager.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hyperstream.downloadmanager.browser.ClipboardLinkMonitor
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.service.HyperStreamDownloadService

class MainActivity : ComponentActivity() {
    private lateinit var repository: MdmRepository
    private var clipboardMonitor: ClipboardLinkMonitor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataDir = filesDir.resolve("hyperstream_data").absolutePath
        val downloadsDir = "/storage/emulated/0/Download/HyperStream"
        repository = MdmRepository(dataDir = dataDir, downloadsDir = downloadsDir)

        runCatching {
            startService(Intent(this, HyperStreamDownloadService::class.java))
        }

        clipboardMonitor = ClipboardLinkMonitor(this) { parsed ->
            if (repository.uiState.value.settings.clipboardMonitor) {
                repository.addDownload(url = parsed.uri, fileName = parsed.suggestedName, connections = 128)
            }
        }.also {
            runCatching { it.start() }
        }

        setContent {
            HyperStreamApp(repository = repository)
        }
    }

    override fun onDestroy() {
        runCatching { clipboardMonitor?.stop() }
        super.onDestroy()
    }
}
