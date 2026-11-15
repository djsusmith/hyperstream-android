package com.hyperstream.downloadmanager.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateChannel(val id: String, val displayName: String) {
    STABLE("stable", "Stable Release"),
    NIGHTLY_BUGFIX("nightly", "Bug-Fix / Nightly Channel")
}

data class GithubReleaseDto(
    val tagName: String,
    val name: String,
    val prerelease: Boolean,
    val upstreamCommit: String,
    val apkDownloadUrl: String,
    val releaseNotes: String = "",
    val sizeBytes: Long = 0L,
    val publishedAt: String = ""
)

sealed class UpdateCheckResult {
    object Idle : UpdateCheckResult()
    object Checking : UpdateCheckResult()
    data class UpdateAvailable(
        val version: String,
        val tagName: String,
        val releaseName: String,
        val apkUrl: String,
        val releaseNotes: String,
        val sizeBytes: Long,
        val isPrerelease: Boolean
    ) : UpdateCheckResult()
    data class UpToDate(
        val currentVersion: String,
        val checkedAt: String
    ) : UpdateCheckResult()
    data class Error(
        val message: String
    ) : UpdateCheckResult()
}

object UpdateChannelManager {
    const val GITHUB_RELEASES_API = "https://api.github.com/repos/djsusmith/hyperstream-android/releases"
    const val DEFAULT_FALLBACK_APK = "https://gh-proxy.com/https://github.com/djsusmith/hyperstream-android/releases/download/v0.6.0/HyperStream-v0.6.0-arm64.apk"

    /**
     * Resolves nearest high-speed CDN mirror for GitHub releases to bypass slow S3 routing.
     */
    fun resolveFastCdnUrl(url: String): String {
        return if (url.startsWith("https://github.com/")) {
            "https://gh-proxy.com/$url"
        } else {
            url
        }
    }

    /**
     * Launches the system browser or download manager to fetch and install the APK via nearest CDN.
     */
    fun startApkDownload(context: Context, apkUrl: String) {
        val raw = if (apkUrl.isNotBlank()) apkUrl else DEFAULT_FALLBACK_APK
        val target = resolveFastCdnUrl(raw)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Queries GitHub Releases API asynchronously and determines if an update is available
     * based on the selected update channel (Stable vs Nightly/Bug-Fix).
     */
    suspend fun checkForUpdates(
        currentVersion: String = "0.6.0",
        channel: UpdateChannel = UpdateChannel.STABLE
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_RELEASES_API)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "HyperStream-Android-Updater/0.6.0")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                instanceFollowRedirects = true
            }

            val code = conn.responseCode
            if (code != 200) {
                return@withContext UpdateCheckResult.Error("GitHub API returned HTTP $code")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val releasesArray = JSONArray(body)
            if (releasesArray.length() == 0) {
                return@withContext UpdateCheckResult.UpToDate(currentVersion, currentTimeStr())
            }

            val parsedList = mutableListOf<GithubReleaseDto>()
            for (i in 0 until releasesArray.length()) {
                val obj = releasesArray.getJSONObject(i)
                val isDraft = obj.optBoolean("draft", false)
                if (isDraft) continue

                val tag = obj.optString("tag_name", "")
                val name = obj.optString("name", tag)
                val isPrerelease = obj.optBoolean("prerelease", false)
                val notes = obj.optString("body", "")
                val published = obj.optString("published_at", "")

                // Find APK asset
                var apkUrl = ""
                var apkSize = 0L
                val assets = obj.optJSONArray("assets")
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val aName = asset.optString("name", "")
                        if (aName.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                if (apkUrl.isBlank() && tag.isNotBlank()) {
                    apkUrl = "https://github.com/djsusmith/hyperstream-android/releases/download/$tag/HyperStream-$tag-arm64.apk"
                }

                parsedList.add(
                    GithubReleaseDto(
                        tagName = tag,
                        name = name,
                        prerelease = isPrerelease,
                        upstreamCommit = "",
                        apkDownloadUrl = apkUrl,
                        releaseNotes = notes,
                        sizeBytes = apkSize,
                        publishedAt = published
                    )
                )
            }

            when (channel) {
                UpdateChannel.STABLE -> {
                    val latestStable = parsedList.firstOrNull { !it.prerelease }
                    if (latestStable == null) {
                        return@withContext UpdateCheckResult.UpToDate(currentVersion, currentTimeStr())
                    }

                    val remoteSemver = latestStable.tagName.removePrefix("v").trim()
                    val localSemver = currentVersion.removePrefix("v").trim()

                    if (compareSemver(remoteSemver, localSemver) > 0) {
                        UpdateCheckResult.UpdateAvailable(
                            version = remoteSemver,
                            tagName = latestStable.tagName,
                            releaseName = latestStable.name,
                            apkUrl = latestStable.apkDownloadUrl,
                            releaseNotes = latestStable.releaseNotes,
                            sizeBytes = latestStable.sizeBytes,
                            isPrerelease = false
                        )
                    } else {
                        UpdateCheckResult.UpToDate(currentVersion, currentTimeStr())
                    }
                }
                UpdateChannel.NIGHTLY_BUGFIX -> {
                    val latestNightly = parsedList.firstOrNull { it.prerelease || it.tagName == "nightly" }
                        ?: parsedList.firstOrNull()

                    if (latestNightly != null) {
                        UpdateCheckResult.UpdateAvailable(
                            version = latestNightly.tagName,
                            tagName = latestNightly.tagName,
                            releaseName = latestNightly.name,
                            apkUrl = latestNightly.apkDownloadUrl,
                            releaseNotes = latestNightly.releaseNotes,
                            sizeBytes = latestNightly.sizeBytes,
                            isPrerelease = true
                        )
                    } else {
                        UpdateCheckResult.UpToDate(currentVersion, currentTimeStr())
                    }
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Network error while checking updates")
        }
    }


    fun compareSemver(a: String, b: String): Int {
        val partsA = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val partsB = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    private fun currentTimeStr(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}
