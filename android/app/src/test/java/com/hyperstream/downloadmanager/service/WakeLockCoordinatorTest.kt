package com.hyperstream.downloadmanager.service

import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.core.QueueUi
import org.junit.Assert.assertEquals
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

    @Test
    fun doesNotHoldWakeLockWhenRunningQueueHasOnlyIdleOrEmptyDownloads() {
        val emptyRunningQueue = QueueUi(name = "Main", running = true, itemIds = emptyList())
        assertFalse(WakeLockCoordinator.shouldHoldLocks(emptyList(), emptyList(), listOf(emptyRunningQueue)))

        val pausedDownload = DownloadItemUi(
            id = 1L,
            name = "a.zip",
            url = "https://example.com/a.zip",
            status = "Paused"
        )
        val queueWithPaused = QueueUi(name = "Main", running = true, itemIds = listOf(1L))
        assertFalse(WakeLockCoordinator.shouldHoldLocks(listOf(pausedDownload), emptyList(), listOf(queueWithPaused)))

        val queuedDownload = pausedDownload.copy(status = "Queued")
        assertTrue(WakeLockCoordinator.shouldHoldLocks(listOf(queuedDownload), emptyList(), listOf(queueWithPaused)))
    }

    @Test
    fun repositoryAppliesEmptyNativeSnapshotAndTogglesQueue() {
        val repo = MdmRepository(autoPoll = false)
        val applied = repo.applySnapshotJson("""{"downloads":[],"torrents":[],"queues":[{"name":"Main","running":true}]}""")
        assertTrue(applied)
        assertEquals(0, repo.uiState.value.downloads.size)
        assertEquals(0, repo.uiState.value.torrents.size)
        assertTrue(repo.uiState.value.queues.first { it.name == "Main" }.running)

        repo.toggleQueue("Main", false)
        assertFalse(repo.uiState.value.queues.first { it.name == "Main" }.running)
    }
}
