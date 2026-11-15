package com.hyperstream.downloadmanager.browser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.hyperstream.downloadmanager.core.MdmNative
import com.hyperstream.downloadmanager.service.HyperStreamDownloadService

class LinkCatcherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val candidateText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        }

        val parsed = LinkParser.extractDownloadableTarget(candidateText)
        if (parsed != null) {
            when (parsed.kind) {
                LinkKind.MAGNET, LinkKind.TORRENT_FILE -> {
                    MdmNative.safeExecuteCommand(
                        """{"op":"add_torrent","source":"${parsed.uri.replace("\"", "\\\"")}","sequential":true}"""
                    )
                    Toast.makeText(
                        this,
                        "HyperStream: Added Torrent (${parsed.suggestedName ?: "Magnet"})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    MdmNative.safeExecuteCommand(
                        """{"op":"add_download","url":"${parsed.uri.replace("\"", "\\\"")}","connections":128}"""
                    )
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
