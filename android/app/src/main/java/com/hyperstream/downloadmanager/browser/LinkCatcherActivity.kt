package com.hyperstream.downloadmanager.browser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.hyperstream.downloadmanager.core.MdmNative
import com.hyperstream.downloadmanager.service.HyperStreamDownloadService

object LinkCatcherDispatcher {
    fun dispatchSharedText(
        rawText: String?,
        dataDir: String = "/storage/emulated/0/Download/HyperStream/.data",
        downloadsDir: String = "/storage/emulated/0/Download/HyperStream",
        initFn: (String, String) -> Boolean = MdmNative::safeInit,
        executeFn: (String) -> String = MdmNative::safeExecuteCommand
    ): ParsedLinkTarget? {
        val parsed = LinkParser.extractDownloadableTarget(rawText) ?: return null
        initFn(dataDir, downloadsDir)
        when (parsed.kind) {
            LinkKind.MAGNET, LinkKind.TORRENT_FILE -> {
                executeFn(
                    """{"op":"add_torrent","source":"${parsed.uri.replace("\"", "\\\"")}","sequential":true}"""
                )
            }
            else -> {
                executeFn(
                    """{"op":"add_download","url":"${parsed.uri.replace("\"", "\\\"")}","connections":128}"""
                )
            }
        }
        return parsed
    }
}

class LinkCatcherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val candidateText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        }

        val dataDir = filesDir.resolve("hyperstream_data").absolutePath
        val downloadsDir = "/storage/emulated/0/Download/HyperStream"
        val parsed = LinkCatcherDispatcher.dispatchSharedText(
            rawText = candidateText,
            dataDir = dataDir,
            downloadsDir = downloadsDir
        )
        if (parsed != null) {
            when (parsed.kind) {
                LinkKind.MAGNET, LinkKind.TORRENT_FILE -> {
                    Toast.makeText(
                        this,
                        "HyperStream: Added Torrent (${parsed.suggestedName ?: "Magnet"})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        this,
                        "HyperStream: Catching ${parsed.suggestedName ?: parsed.uri} (128 Threads)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            runCatching {
                startService(Intent(this, HyperStreamDownloadService::class.java))
            }
        }
        finish()
    }
}
