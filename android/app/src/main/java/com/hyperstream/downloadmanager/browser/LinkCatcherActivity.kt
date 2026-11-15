package com.hyperstream.downloadmanager.browser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.hyperstream.downloadmanager.core.MdmNative
import com.hyperstream.downloadmanager.ui.MainActivity

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
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        }

        val target = LinkParser.extractDownloadableTarget(candidateText)
        if (target == null) {
            Toast.makeText(this, "HyperStream: no link found in shared text", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Open HyperStream itself: torrents are added there, everything else opens the format picker.
        val open = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SHARED_URL, target.uri)
            putExtra(MainActivity.EXTRA_SHARED_KIND, target.kind.name)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(open)
        finish()
    }
}
