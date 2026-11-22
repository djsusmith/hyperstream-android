package com.hyperstream.downloadmanager.a2ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight native implementation of the A2UI protocol (https://a2ui.org/quickstart/agent.md)
 * for Jetpack Compose:
 *  - Surface: a UI region driven by declarative component definitions (`updateComponents`)
 *  - DataModel: reactive key-value state bound to components (`updateDataModel`)
 *  - Action: user interaction events emitted back to the host (`action`)
 */
data class A2uiAction(
    val name: String,
    val surfaceId: String,
    val payload: Map<String, Any?> = emptyMap()
)

sealed class A2uiComponent(open val id: String) {
    data class SpeedometerHero(
        override val id: String,
        val title: String,
        val speedBpsPath: String,
        val peakBpsPath: String,
        val activeCountPath: String,
        val queuedCountPath: String,
        val doneCountPath: String,
        val collapsedPath: String,
        val showMapPath: String,
        val hasActivePath: String,
        val speedHistoryPath: String
    ) : A2uiComponent(id)

    data class StatusBanner(
        override val id: String,
        val title: String,
        val subtitle: String,
        val tone: String = "info"
    ) : A2uiComponent(id)

    data class MetricGrid(
        override val id: String,
        val items: List<MetricItem>
    ) : A2uiComponent(id) {
        data class MetricItem(
            val label: String,
            val valuePath: String,
            val icon: String = "bolt"
        )
    }

    data class DownloadNotificationCard(
        override val id: String,
        val downloadId: Long,
        val fileNamePath: String,
        val categoryPath: String,
        val statusPath: String,
        val progressPercentPath: String,
        val downloadedBytesPath: String,
        val totalBytesPath: String,
        val speedBpsPath: String,
        val etaSecondsPath: String,
        val activeThreadsPath: String,
        val maxThreadsPath: String,
        val segmentStatesPath: String,
        val savePathKey: String,
        val errorMessagePath: String
    ) : A2uiComponent(id)
}

data class A2uiSurface(
    val surfaceId: String,
    val components: List<A2uiComponent>,
    val dataModel: Map<String, Any?>
) {
    fun toWireJson(): String {
        val root = JSONObject()
        root.put("surfaceId", surfaceId)

        val compsArr = JSONArray()
        components.forEach { c ->
            val obj = JSONObject().put("id", c.id)
            when (c) {
                is A2uiComponent.SpeedometerHero -> {
                    obj.put("component", "SpeedometerHero")
                    obj.put("title", c.title)
                    obj.put("bindings", JSONObject()
                        .put("speedBps", c.speedBpsPath)
                        .put("peakBps", c.peakBpsPath)
                        .put("activeCount", c.activeCountPath)
                        .put("queuedCount", c.queuedCountPath)
                        .put("doneCount", c.doneCountPath))
                }
                is A2uiComponent.StatusBanner -> {
                    obj.put("component", "StatusBanner")
                    obj.put("title", c.title)
                    obj.put("subtitle", c.subtitle)
                    obj.put("tone", c.tone)
                }
                is A2uiComponent.MetricGrid -> {
                    obj.put("component", "MetricGrid")
                    obj.put("count", c.items.size)
                }
                is A2uiComponent.DownloadNotificationCard -> {
                    obj.put("component", "DownloadNotificationCard")
                    obj.put("downloadId", c.downloadId)
                    obj.put("bindings", JSONObject()
                        .put("fileName", c.fileNamePath)
                        .put("status", c.statusPath)
                        .put("progressPercent", c.progressPercentPath)
                        .put("speedBps", c.speedBpsPath)
                        .put("etaSeconds", c.etaSecondsPath))
                }
            }
            compsArr.put(obj)
        }
        root.put("updateComponents", compsArr)

        val dm = JSONObject()
        dataModel.forEach { (k, v) -> dm.put(k, v) }
        root.put("updateDataModel", dm)
        return root.toString(2)
    }
}

object HyperStreamA2uiCatalog {
    const val HERO_SURFACE_ID = "hyperstream.downloads.hero"
    const val NOTIFICATIONS_SURFACE_ID = "hyperstream.notifications.progress"

    fun buildDownloadsHeroSurface(
        speedBps: Long,
        peakBps: Long,
        activeCount: Int,
        queuedCount: Int,
        doneCount: Int,
        activeThreads: Int,
        collapsed: Boolean,
        showConnectionMap: Boolean,
        speedHistory: List<Long>
    ): A2uiSurface {
        return A2uiSurface(
            surfaceId = HERO_SURFACE_ID,
            components = listOf(
                A2uiComponent.SpeedometerHero(
                    id = "hero_speedometer",
                    title = "Total speed",
                    speedBpsPath = "speedBps",
                    peakBpsPath = "peakBps",
                    activeCountPath = "activeCount",
                    queuedCountPath = "queuedCount",
                    doneCountPath = "doneCount",
                    collapsedPath = "collapsed",
                    showMapPath = "showConnectionMap",
                    hasActivePath = "hasActive",
                    speedHistoryPath = "speedHistory"
                )
            ),
            dataModel = mapOf(
                "speedBps" to speedBps,
                "peakBps" to peakBps,
                "activeCount" to activeCount,
                "queuedCount" to queuedCount,
                "doneCount" to doneCount,
                "activeThreads" to activeThreads,
                "collapsed" to collapsed,
                "showConnectionMap" to showConnectionMap,
                "hasActive" to (activeCount > 0),
                "speedHistory" to speedHistory
            )
        )
    }

    fun buildDownloadNotificationsSurface(
        downloads: List<com.hyperstream.downloadmanager.core.DownloadItemUi>,
        totalDownSpeedBps: Long,
        activeThreadsTotal: Int,
        wakeLockActive: Boolean,
        batteryOptimizationIgnored: Boolean,
        filter: String = "All"
    ): A2uiSurface {
        val activeCount = downloads.count { it.status == "Downloading" || it.status == "Probing" }
        val completedCount = downloads.count { it.status == "Completed" }
        val pausedOrFailedCount = downloads.count { it.status == "Paused" || it.status == "Failed" || it.status == "Queued" }

        val filtered = when (filter) {
            "Active" -> downloads.filter { it.status == "Downloading" || it.status == "Probing" }
            "Paused/Failed" -> downloads.filter { it.status == "Paused" || it.status == "Failed" || it.status == "Queued" }
            "Completed" -> downloads.filter { it.status == "Completed" }
            else -> downloads
        }.sortedWith(
            compareBy<com.hyperstream.downloadmanager.core.DownloadItemUi> {
                when (it.status) {
                    "Downloading", "Probing" -> 0
                    "Failed" -> 1
                    "Paused", "Queued" -> 2
                    "Completed" -> 3
                    else -> 4
                }
            }.thenByDescending { it.id }
        )

        val comps = mutableListOf<A2uiComponent>()
        val dm = mutableMapOf<String, Any?>(
            "totalDownSpeedBps" to totalDownSpeedBps,
            "activeThreadsTotal" to activeThreadsTotal,
            "activeCount" to activeCount,
            "completedCount" to completedCount,
            "pausedOrFailedCount" to pausedOrFailedCount,
            "wakeLockActive" to wakeLockActive,
            "batteryOptimizationIgnored" to batteryOptimizationIgnored
        )

        val statusTitle = if (activeCount > 0) {
            val mbps = String.format("%.2f MB/s", totalDownSpeedBps / (1024.0 * 1024.0))
            "Live Notification Stream • $activeCount Active ($mbps • $activeThreadsTotal Threads)"
        } else {
            "Notification Center Ready • $completedCount Completed • $pausedOrFailedCount Queued/Paused"
        }
        val statusSub = buildString {
            append(if (wakeLockActive) "Foreground Service & High-Perf Wi-Fi Lock Active" else "Engine Idle (Standby)")
            append(" • ")
            append(if (batteryOptimizationIgnored) "Battery: Unrestricted" else "Battery: Optimized (Tap to Whitelist)")
        }

        comps.add(
            A2uiComponent.StatusBanner(
                id = "notif_system_banner",
                title = statusTitle,
                subtitle = statusSub,
                tone = if (!batteryOptimizationIgnored) "warning" else if (activeCount > 0) "active" else "info"
            )
        )

        filtered.forEach { d ->
            val p = "dl_${d.id}"
            val pct = if (d.status == "Completed") {
                100f
            } else if (d.totalBytes > 0L) {
                ((d.downloadedBytes.toDouble() / d.totalBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
            } else {
                0f
            }
            // Build a 16-bucket segment state array: 0 = waiting, 1 = active, 2 = done
            val buckets = IntArray(16) { idx ->
                val seg = d.segments.getOrNull(idx % d.segments.size.coerceAtLeast(1))
                when {
                    d.status == "Completed" -> 2
                    seg == null -> if (idx < (pct / 6.25f).toInt()) 2 else 0
                    seg.end > seg.start && seg.pos >= seg.end -> 2
                    seg.active -> 1
                    seg.pos > seg.start -> 1
                    else -> 0
                }
            }.toList()

            dm["${p}_name"] = d.name
            dm["${p}_cat"] = d.category
            dm["${p}_status"] = d.status
            dm["${p}_pct"] = pct
            dm["${p}_done"] = d.downloadedBytes
            dm["${p}_total"] = d.totalBytes
            dm["${p}_speed"] = d.speedBps
            dm["${p}_eta"] = d.etaSeconds ?: -1L
            dm["${p}_actConns"] = d.activeConnections
            dm["${p}_maxConns"] = d.maxConnections
            dm["${p}_segments"] = buckets
            dm["${p}_savePath"] = d.savePath
            dm["${p}_error"] = d.errorMessage ?: ""

            comps.add(
                A2uiComponent.DownloadNotificationCard(
                    id = "notif_card_${d.id}",
                    downloadId = d.id,
                    fileNamePath = "${p}_name",
                    categoryPath = "${p}_cat",
                    statusPath = "${p}_status",
                    progressPercentPath = "${p}_pct",
                    downloadedBytesPath = "${p}_done",
                    totalBytesPath = "${p}_total",
                    speedBpsPath = "${p}_speed",
                    etaSecondsPath = "${p}_eta",
                    activeThreadsPath = "${p}_actConns",
                    maxThreadsPath = "${p}_maxConns",
                    segmentStatesPath = "${p}_segments",
                    savePathKey = "${p}_savePath",
                    errorMessagePath = "${p}_error"
                )
            )
        }

        return A2uiSurface(
            surfaceId = NOTIFICATIONS_SURFACE_ID,
            components = comps,
            dataModel = dm
        )
    }
}
