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

        val request = LinkParser.extractExternalDownloadRequest(intent)
        if (request == null) {
            Toast.makeText(this, "HyperStream: no link found in shared text", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Open HyperStream itself: browser file handoffs open Add Download pre-filled,
        // torrents are added directly, and social video links open the format picker.
        val open = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SHARED_URL, request.url)
            putExtra(MainActivity.EXTRA_SHARED_KIND, request.kind.name)
            request.fileName?.let { putExtra(MainActivity.EXTRA_SHARED_FILENAME, it) }
            request.mimeType?.let { putExtra(MainActivity.EXTRA_SHARED_MIME, it) }
            request.userAgent?.let { putExtra(MainActivity.EXTRA_SHARED_USER_AGENT, it) }
            request.referer?.let { putExtra(MainActivity.EXTRA_SHARED_REFERER, it) }
            request.cookie?.let { putExtra(MainActivity.EXTRA_SHARED_COOKIE, it) }
            putExtra(MainActivity.EXTRA_IS_BROWSER_HANDOFF, request.isExternalBrowserHandoff)
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
