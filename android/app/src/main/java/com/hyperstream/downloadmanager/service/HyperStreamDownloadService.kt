package com.hyperstream.downloadmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.core.MdmUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HyperStreamDownloadService : Service() {
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastNotifyTimeMs: Long = 0L
    private var lastActiveCount: Int = -1

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        serviceScope.launch {
            MdmRepository.shared.uiState.collectLatest { state ->
                syncWithState(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification("HyperStream 128-Thread Engine Ready", "Monitoring downloads & queues")
        )
        syncWithState(MdmRepository.shared.uiState.value)
        return START_STICKY
    }

    fun syncWithState(state: MdmUiState) {
        val hold = WakeLockCoordinator.shouldHoldLocks(state.downloads, state.torrents, state.queues)
        if (hold) {
            acquireLocks()
            val activeCount = state.downloads.count { it.status == "Downloading" } +
                state.torrents.count { it.status == "Downloading" }
            val now = System.currentTimeMillis()
            // Throttle notifications to at most 1 Hz unless active download count changed
            if (activeCount != lastActiveCount || now - lastNotifyTimeMs >= 1000L) {
                lastNotifyTimeMs = now
                lastActiveCount = activeCount
                val speedMb = String.format("%.1f MB/s", state.totalDownSpeedBps / 1_000_000.0)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        "Downloading ($activeCount active • ${state.activeThreadsTotal} threads)",
                        "$speedMb • WakeLock & High-Perf Wi-Fi Lock active"
                    )
                )
            }
        } else {
            releaseLocks()
            lastActiveCount = -1
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
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "hyperstream_transfers"
        const val NOTIFICATION_ID = 1001
    }
}
