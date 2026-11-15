package com.hyperstream.downloadmanager.service

import com.hyperstream.downloadmanager.core.DownloadItemUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeLockCoordinatorTest {
    @Test
    fun holdsWakeLockOnlyWhenDownloadTorrentOrQueueIsActive() {
        val idleDownload = DownloadItemUi(
            id = 1L,
            name = "a.zip",
            url = "https://example.com/a.zip",
            status = "Paused"
        )
        val activeDownload = idleDownload.copy(status = "Downloading", activeConnections = 128)
        assertFalse(WakeLockCoordinator.shouldHoldLocks(listOf(idleDownload), emptyList(), emptyList()))
        assertTrue(WakeLockCoordinator.shouldHoldLocks(listOf(activeDownload), emptyList(), emptyList()))
    }

    @Test
    fun releasesWakeLockWhenAllTransfersFinishOrFail() {
        val done = DownloadItemUi(
            id = 1L,
            name = "a.zip",
            url = "https://example.com/a.zip",
            status = "Done"
        )
        val failed = DownloadItemUi(
            id = 2L,
            name = "b.zip",
            url = "https://example.com/b.zip",
            status = "Failed"
        )
        assertFalse(WakeLockCoordinator.shouldHoldLocks(listOf(done, failed), emptyList(), emptyList()))
    }
}
