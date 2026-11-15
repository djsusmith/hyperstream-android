package com.hyperstream.downloadmanager.updater

enum class UpdateChannel(val id: String, val displayName: String) {
    STABLE("stable", "Stable Release (Upstream Tags)"),
    NIGHTLY_BUGFIX("nightly", "Bug-Fix / Nightly (6h Upstream Sync)")
}

data class GithubReleaseDto(
    val tagName: String,
    val prerelease: Boolean,
    val upstreamCommit: String,
    val apkDownloadUrl: String,
    val releaseNotes: String = ""
)

object UpdateChannelManager {
    const val GITHUB_RELEASES_API = "https://api.github.com/repos/djsusmith/hyperstream-android/releases"
    const val UPSTREAM_REPO = "https://github.com/anefzaoui/misterdownloadmanager"

    fun isUpdateAvailable(
        currentVersion: String,
        currentUpstreamSha: String,
        release: GithubReleaseDto,
        channel: UpdateChannel
    ): Boolean {
        return when (channel) {
            UpdateChannel.STABLE -> {
                if (release.prerelease) return false
                val cleanCurrent = currentVersion.removePrefix("v").trim()
                val cleanRemote = release.tagName.removePrefix("v").trim()
                compareSemver(cleanRemote, cleanCurrent) > 0
            }
            UpdateChannel.NIGHTLY_BUGFIX -> {
                val cleanCurrentSha = currentUpstreamSha.trim().lowercase()
                val cleanRemoteSha = release.upstreamCommit.trim().lowercase()
                cleanRemoteSha.isNotEmpty() && cleanRemoteSha != cleanCurrentSha
            }
        }
    }

    private fun compareSemver(a: String, b: String): Int {
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
}
