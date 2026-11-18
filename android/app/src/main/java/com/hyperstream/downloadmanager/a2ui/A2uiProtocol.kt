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
}
