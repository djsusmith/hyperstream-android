package com.hyperstream.downloadmanager.browser

import android.content.ClipboardManager
import android.content.Context

class ClipboardLinkMonitor(
    context: Context,
    private val onTargetDetected: (ParsedLinkTarget) -> Unit
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastObservedUri: String? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        val parsed = LinkParser.extractDownloadableTarget(text)
        if (parsed != null && parsed.uri != lastObservedUri) {
            lastObservedUri = parsed.uri
            onTargetDetected(parsed)
        }
    }

    fun start() {
        clipboard.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        clipboard.removePrimaryClipChangedListener(listener)
    }
}
