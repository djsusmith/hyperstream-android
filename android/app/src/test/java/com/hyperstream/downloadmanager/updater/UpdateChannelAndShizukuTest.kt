package com.hyperstream.downloadmanager.updater

import com.hyperstream.downloadmanager.shizuku.ShizukuManager
import com.hyperstream.downloadmanager.shizuku.ShizukuResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelAndShizukuTest {
    @Test
    fun detectsStableSemverAndNightlyUpstreamCommitUpdates() {
        val stableRel = GithubReleaseDto(
            tagName = "v0.5.3",
            prerelease = false,
            upstreamCommit = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            apkDownloadUrl = "https://github.com/djsusmith/hyperstream-android/releases/download/v0.5.3/HyperStream.apk"
        )
        assertTrue(
            UpdateChannelManager.isUpdateAvailable(
                "0.5.2",
                "1111111111111111111111111111111111111111",
                stableRel,
                UpdateChannel.STABLE
            )
        )
        assertFalse(
            UpdateChannelManager.isUpdateAvailable(
                "0.5.3",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                stableRel,
                UpdateChannel.STABLE
            )
        )

        val nightlyRel = GithubReleaseDto(
            tagName = "nightly",
            prerelease = true,
            upstreamCommit = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            apkDownloadUrl = "https://github.com/djsusmith/hyperstream-android/releases/download/nightly/HyperStream-nightly.apk"
        )
        assertTrue(
            UpdateChannelManager.isUpdateAvailable(
                "0.5.2",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                nightlyRel,
                UpdateChannel.NIGHTLY_BUGFIX
            )
        )
        assertFalse(
            UpdateChannelManager.isUpdateAvailable(
                "0.5.2",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                nightlyRel,
                UpdateChannel.NIGHTLY_BUGFIX
            )
        )
    }

    @Test
    fun shizukuGracefullyReportsUnavailableWhenDisabledOrMissing() {
        val mgr = ShizukuManager(enabled = false, commandRunner = { -1 to "not running" })
        val res = mgr.whitelistFromDoze()
        assertTrue(res is ShizukuResult.Unavailable)
    }

    @Test
    fun shizukuResolvesFallbackBinaryCandidatesAndExecutesActions() {
        val candidates = ShizukuManager.candidateRishPaths()
        assertTrue(candidates.contains("rish"))
        assertTrue(candidates.any { it.endsWith("/bin/rish") })

        val executed = mutableListOf<String>()
        val mgr = ShizukuManager(
            enabled = true,
            commandRunner = { cmd ->
                executed.add(cmd)
                0 to "Success\nuid=2000(shell)"
            }
        )
        assertTrue(mgr.whitelistFromDoze() is ShizukuResult.Success)
        assertTrue(mgr.silentInstallApk("/storage/emulated/0/Download/HyperStream-v0.5.2-arm64.apk") is ShizukuResult.Success)
        assertTrue(executed.any { it.contains("deviceidle whitelist") })
        assertTrue(executed.any { it.contains("pm install -r") })
    }
}
