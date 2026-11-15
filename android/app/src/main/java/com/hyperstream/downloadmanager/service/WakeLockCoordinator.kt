package com.hyperstream.downloadmanager.service

import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.QueueUi
import com.hyperstream.downloadmanager.core.TorrentItemUi

object WakeLockCoordinator {
    const val CPU_WAKE_LOCK_TAG = "HyperStream::DownloadWakeLock"
    const val WIFI_WAKE_LOCK_TAG = "HyperStream::DownloadWifiLock"

    private val activeDownloadStates = setOf(
        "Probing",
        "Downloading",
        "Verifying",
        "Merging",
        "Completing"
    )

    private val activeTorrentStates = setOf(
        "Metadata",
        "Downloading",
        "Seeding"
    )

    fun shouldHoldLocks(
        downloads: List<DownloadItemUi>,
        torrents: List<TorrentItemUi>,
        queues: List<QueueUi>
    ): Boolean {
        val hasActiveDownload = downloads.any { it.status in activeDownloadStates }
        val hasActiveTorrent = torrents.any { it.status in activeTorrentStates }
        val hasRunningQueue = queues.any { it.running }
        return hasActiveDownload || hasActiveTorrent || hasRunningQueue
    }
}
