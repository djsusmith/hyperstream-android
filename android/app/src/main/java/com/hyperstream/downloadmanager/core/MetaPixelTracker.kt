package com.hyperstream.downloadmanager.core

import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Native Meta (Facebook) Pixel & Conversion Event Tracker for HyperStream Android.
 * Dispatches conversion events directly to Meta's Pixel telemetry endpoint (id: 1683790373120460),
 * reflecting inside Facebook Events Manager and Ads Manager in real time.
 */
object MetaPixelTracker {
    private const val TAG = "MetaPixelTracker"
    const val PIXEL_ID = "1683790373120460"
    private const val PIXEL_ENDPOINT = "https://www.facebook.com/tr/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appVersion = com.hyperstream.downloadmanager.BuildConfig.VERSION_NAME

    /**
     * Track standard or custom Meta conversion event.
     *
     * Standard events: PageView, AppOpen, Lead, CompleteRegistration, ViewContent
     * Custom events: ShareReceived, StartDownload, CompleteDownload
     */
    fun track(eventName: String, params: Map<String, Any?> = emptyMap()) {
        scope.launch {
            runCatching {
                val uriBuilder = Uri.parse(PIXEL_ENDPOINT).buildUpon()
                    .appendQueryParameter("id", PIXEL_ID)
                    .appendQueryParameter("ev", eventName)
                    .appendQueryParameter("dl", "https://hyperstream.app/android/v$appVersion")
                    .appendQueryParameter("noscript", "1")
                    .appendQueryParameter("cd[app_version]", appVersion)
                    .appendQueryParameter("cd[device_model]", Build.MODEL)
                    .appendQueryParameter("cd[android_sdk]", Build.VERSION.SDK_INT.toString())

                params.forEach { (key, value) ->
                    if (value != null) {
                        uriBuilder.appendQueryParameter("cd[$key]", value.toString())
                    }
                }

                val finalUrl = URL(uriBuilder.build().toString())
                val conn = (finalUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty(
                        "User-Agent",
                        "HyperStream-Android/$appVersion (${Build.MANUFACTURER}; ${Build.MODEL}; Android ${Build.VERSION.RELEASE})"
                    )
                    instanceFollowRedirects = true
                }

                val code = conn.responseCode
                conn.disconnect()
                Log.d(TAG, "Meta Pixel Event '$eventName' sent -> HTTP $code")
            }.onFailure { e ->
                Log.w(TAG, "Failed to send Meta Pixel event '$eventName': ${e.message}")
            }
        }
    }

    /** Convenience methods for conversion funnels */
    fun trackAppOpen() {
        track("AppOpen")
        track("PageView")
    }

    fun trackShareReceived(url: String, platform: String) {
        track(
            "ShareReceived",
            mapOf(
                "shared_url" to url,
                "platform" to platform
            )
        )
    }

    fun trackStartDownload(fileName: String?, platform: String, category: String, connections: Int) {
        track(
            "StartDownload",
            mapOf(
                "content_name" to (fileName ?: "Media"),
                "platform" to platform,
                "category" to category,
                "connections" to connections
            )
        )
        // Also log standard 'Lead' conversion event for Meta Ads optimization
        track(
            "Lead",
            mapOf(
                "content_name" to (fileName ?: "Media"),
                "content_category" to category
            )
        )
    }

    fun trackCompleteDownload(fileName: String, bytes: Long, category: String) {
        track(
            "CompleteDownload",
            mapOf(
                "content_name" to fileName,
                "bytes" to bytes,
                "category" to category
            )
        )
    }
}
