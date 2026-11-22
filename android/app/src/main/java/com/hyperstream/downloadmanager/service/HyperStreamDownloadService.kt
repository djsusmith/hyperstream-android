package com.hyperstream.downloadmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class HyperStreamDownloadService : Service() {
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastNotifyTimeMs: Long = 0L
    private var lastActiveCount: Int = -1
    private var lastPausedOrFailedCount: Int = -1
    private val notifiedItemIds = mutableSetOf<Long>()
    private val completedNotifiedIds = mutableSetOf<Long>()

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        val repo = MdmRepository.forContext(this)
        serviceScope.launch {
            repo.uiState.collectLatest { state ->
                syncWithState(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = MdmRepository.forContext(this)
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
                if (id > 0L) {
                    repo.togglePauseResume(id)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
                if (id > 0L) {
                    repo.cancelDownload(id, deleteFile = false)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(ITEM_NOTIFICATION_BASE_ID + id.toInt())
                }
            }
            ACTION_PAUSE_ALL -> {
                repo.uiState.value.downloads
                    .filter { it.status == "Downloading" || it.status == "Probing" }
                    .forEach { repo.togglePauseResume(it.id) }
            }
            ACTION_RESUME_ALL -> {
                repo.uiState.value.downloads
                    .filter { it.status == "Paused" || it.status == "Queued" }
                    .forEach { repo.togglePauseResume(it.id) }
            }
        }
        startForeground(
            NOTIFICATION_ID,
            buildSummaryNotification(
                title = "HyperStream 128-Thread Engine Ready",
                text = "Monitoring downloads & queues",
                progressPct = -1,
                hasActive = false
            )
        )
        syncWithState(repo.uiState.value)
        return START_STICKY
    }

    fun syncWithState(state: MdmUiState) {
        val hold = WakeLockCoordinator.shouldHoldLocks(state.downloads, state.torrents, state.queues)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeDownloads = state.downloads.filter { it.status == "Downloading" || it.status == "Probing" }
        val pausedOrFailedDownloads = state.downloads.filter { it.status == "Paused" || it.status == "Failed" || it.status == "Queued" }
        val activeCount = activeDownloads.size + state.torrents.count { it.status == "Downloading" }
        val pausedOrFailedCount = pausedOrFailedDownloads.size
        val now = System.currentTimeMillis()
        val primaryItem = activeDownloads.firstOrNull() ?: pausedOrFailedDownloads.firstOrNull()

        if (hold) {
            acquireLocks()
            if (activeCount != lastActiveCount || pausedOrFailedCount != lastPausedOrFailedCount || now - lastNotifyTimeMs >= 1000L) {
                lastNotifyTimeMs = now
                lastActiveCount = activeCount
                lastPausedOrFailedCount = pausedOrFailedCount
                val speedMb = String.format("%.2f MB/s", state.totalDownSpeedBps / (1024.0 * 1024.0))
                val totalBytes = activeDownloads.sumOf { it.totalBytes }
                val doneBytes = activeDownloads.sumOf { it.downloadedBytes }
                val aggregatePct = if (totalBytes > 0L) {
                    ((doneBytes.toDouble() / totalBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                } else {
                    0
                }
                val summaryTitle = if (activeDownloads.size == 1 && primaryItem != null) {
                    "⬇ ${primaryItem.name} • $aggregatePct% ($speedMb)"
                } else {
                    "⬇ Downloading $activeCount files • $aggregatePct% ($speedMb)"
                }
                nm.notify(
                    NOTIFICATION_ID,
                    buildSummaryNotification(
                        title = summaryTitle,
                        text = "${state.activeThreadsTotal} active threads • Tap to open A2UI Notification Center",
                        progressPct = aggregatePct,
                        hasActive = true,
                        primaryItem = primaryItem
                    )
                )
                syncPerItemNotifications(nm, state.downloads)
            }
        } else {
            releaseLocks()
            if (lastActiveCount != 0 || pausedOrFailedCount != lastPausedOrFailedCount) {
                lastActiveCount = 0
                lastPausedOrFailedCount = pausedOrFailedCount
                val pausedPct = if (primaryItem != null && primaryItem.totalBytes > 0L) {
                    ((primaryItem.downloadedBytes.toDouble() / primaryItem.totalBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                } else {
                    -1
                }
                val summaryTitle = if (primaryItem != null) {
                    "⏸ ${primaryItem.name} (${if (pausedPct >= 0) "$pausedPct% • " else ""}${primaryItem.status})"
                } else {
                    "HyperStream 128-Thread Engine Ready"
                }
                val summarySub = if (primaryItem != null) {
                    val doneMb = String.format("%.1f MB", primaryItem.downloadedBytes / (1024.0 * 1024.0))
                    val totMb = String.format("%.1f MB", primaryItem.totalBytes / (1024.0 * 1024.0))
                    "$doneMb / $totMb • Tap Resume or open A2UI Notification Center"
                } else {
                    "${state.downloads.count { it.status == "Completed" }} completed • Tap to view Notification Center"
                }
                nm.notify(
                    NOTIFICATION_ID,
                    buildSummaryNotification(
                        title = summaryTitle,
                        text = summarySub,
                        progressPct = pausedPct,
                        hasActive = false,
                        primaryItem = primaryItem
                    )
                )
                syncPerItemNotifications(nm, state.downloads)
            }
        }
    }

    private fun syncPerItemNotifications(nm: NotificationManager, downloads: List<DownloadItemUi>) {
        val currentIds = downloads.map { it.id }.toSet()
        val removed = notifiedItemIds - currentIds
        removed.forEach { id ->
            nm.cancel(ITEM_NOTIFICATION_BASE_ID + id.toInt())
            notifiedItemIds.remove(id)
        }

        val prioritized = downloads.sortedWith(
            compareBy<DownloadItemUi> {
                when (it.status) {
                    "Downloading", "Probing" -> 0
                    "Paused", "Failed", "Queued" -> 1
                    "Completed" -> 2
                    else -> 3
                }
            }.thenByDescending { it.id }
        )

        prioritized.take(6).forEach { item ->
            val notifId = ITEM_NOTIFICATION_BASE_ID + item.id.toInt()
            when (item.status) {
                "Downloading", "Probing", "Paused", "Failed", "Queued" -> {
                    completedNotifiedIds.remove(item.id)
                    notifiedItemIds.add(item.id)
                    nm.notify(notifId, buildItemProgressNotification(item))
                }
                "Completed" -> {
                    if (completedNotifiedIds.add(item.id) && notifiedItemIds.contains(item.id)) {
                        nm.notify(notifId, buildItemCompletedNotification(item))
                    }
                }
            }
        }
    }

    private fun acquireLocks() {
        if (cpuWakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WakeLockCoordinator.CPU_WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (cpuWakeLock?.isHeld == false) {
            cpuWakeLock?.acquire(6 * 60 * 60 * 1000L)
        }

        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                WakeLockCoordinator.WIFI_WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        if (cpuWakeLock?.isHeld == true) {
            cpuWakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "HyperStream Active Transfers",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun openAppPendingIntent(openNotificationsTab: Boolean = true): PendingIntent {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openNotificationsTab) {
                putExtra(EXTRA_OPEN_NOTIFICATIONS_TAB, true)
            }
        }
        return PendingIntent.getActivity(
            this,
            if (openNotificationsTab) 901 else 900,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceActionPendingIntent(action: String, downloadId: Long, reqCode: Int): PendingIntent {
        val intent = Intent(this, HyperStreamDownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getService(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSummaryNotification(
        title: String,
        text: String,
        progressPct: Int,
        hasActive: Boolean,
        primaryItem: DownloadItemUi? = null
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openAppPendingIntent(openNotificationsTab = true))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (progressPct in 0..100) {
            builder.setProgress(100, progressPct, false)
        }
        if (hasActive) {
            val pauseAllPi = serviceActionPendingIntent(ACTION_PAUSE_ALL, -1L, 910)
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Pause All",
                    pauseAllPi
                ).build()
            )
        } else if (primaryItem != null) {
            val resumePi = serviceActionPendingIntent(ACTION_TOGGLE_PAUSE, primaryItem.id, 911)
            val cancelPi = serviceActionPendingIntent(ACTION_CANCEL_DOWNLOAD, primaryItem.id, 912)
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    resumePi
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    cancelPi
                ).build()
            )
        }
        return builder.build()
    }

    private fun buildItemProgressNotification(item: DownloadItemUi): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val pct = if (item.totalBytes > 0L) {
            ((item.downloadedBytes.toDouble() / item.totalBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
        val isActive = item.status == "Downloading" || item.status == "Probing"
        val speedStr = if (isActive && item.speedBps > 0L) {
            String.format(" • %.2f MB/s", item.speedBps / (1024.0 * 1024.0))
        } else {
            ""
        }
        val etaStr = if (isActive && (item.etaSeconds ?: 0L) > 0L) {
            " • ${item.etaSeconds}s left"
        } else {
            ""
        }
        val sizeStr = if (item.totalBytes > 0L) {
            String.format(
                "%.1f / %.1f MB",
                item.downloadedBytes / (1024.0 * 1024.0),
                item.totalBytes / (1024.0 * 1024.0)
            )
        } else {
            String.format("%.1f MB", item.downloadedBytes / (1024.0 * 1024.0))
        }

        val togglePi = serviceActionPendingIntent(
            ACTION_TOGGLE_PAUSE,
            item.id,
            2000 + item.id.toInt()
        )
        val cancelPi = serviceActionPendingIntent(
            ACTION_CANCEL_DOWNLOAD,
            item.id,
            3000 + item.id.toInt()
        )

        return builder
            .setContentTitle("${item.name} ($pct%)")
            .setContentText("${item.status} • $sizeStr$speedStr$etaStr • ${item.activeConnections}/${item.maxConnections}T")
            .setSmallIcon(
                if (isActive) android.R.drawable.stat_sys_download else android.R.drawable.ic_media_pause
            )
            .setProgress(100, pct, item.status == "Probing")
            .setContentIntent(openAppPendingIntent(openNotificationsTab = true))
            .setOngoing(isActive)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    if (isActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isActive) "Pause" else "Resume",
                    togglePi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    cancelPi
                ).build()
            )
            .build()
    }

    private fun buildItemCompletedNotification(item: DownloadItemUi): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val sizeMb = String.format("%.1f MB", item.totalBytes.coerceAtLeast(item.downloadedBytes) / (1024.0 * 1024.0))
        return builder
            .setContentTitle("✓ Download Complete: ${item.name}")
            .setContentText("$sizeMb • Saved in ${item.category}")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openAppPendingIntent(openNotificationsTab = true))
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "hyperstream_transfers"
        const val NOTIFICATION_ID = 1001
        const val ITEM_NOTIFICATION_BASE_ID = 4000
        const val NOTIFICATION_GROUP_KEY = "com.hyperstream.downloadmanager.TRANSFERS_GROUP"
        const val ACTION_TOGGLE_PAUSE = "com.hyperstream.downloadmanager.action.TOGGLE_PAUSE"
        const val ACTION_CANCEL_DOWNLOAD = "com.hyperstream.downloadmanager.action.CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.hyperstream.downloadmanager.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.hyperstream.downloadmanager.action.RESUME_ALL"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_OPEN_NOTIFICATIONS_TAB = "extra_open_notifications_tab"
    }
}
