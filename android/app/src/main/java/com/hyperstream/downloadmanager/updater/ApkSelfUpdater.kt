package com.hyperstream.downloadmanager.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Progress of the in-app self-update flow. */
sealed class SelfUpdateState {
    object Idle : SelfUpdateState()
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : SelfUpdateState() {
        val fraction: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    object Verifying : SelfUpdateState()
    /** APK downloaded and verified, waiting for the user to allow "Install unknown apps". */
    data class NeedsInstallPermission(val apk: File) : SelfUpdateState()
    /** System installer has been launched. */
    data class Installing(val apk: File) : SelfUpdateState()
    data class Failed(val message: String) : SelfUpdateState()
}

/**
 * Downloads a release APK inside the app, verifies it (SHA-256 digest from GitHub + package name +
 * newer versionCode) and hands it to the Android package installer.
 *
 * Android never allows a normal app to replace itself silently; the system installer shows a
 * confirmation and then offers "Open" to relaunch HyperStream on the new version.
 */
object ApkSelfUpdater {
    private val _state = MutableStateFlow<SelfUpdateState>(SelfUpdateState.Idle)
    val state: StateFlow<SelfUpdateState> = _state.asStateFlow()

    private const val UPDATE_DIR = "updates"

    fun reset() {
        _state.value = SelfUpdateState.Idle
    }

    /**
     * Downloads [apkUrl] (GitHub direct first, CDN mirror as fallback), verifies it and launches
     * the installer. Safe to call from any coroutine; runs network/disk work on [Dispatchers.IO].
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        tagName: String,
        expectedSha256: String = ""
    ) {
        val appContext = context.applicationContext
        val current = _state.value
        if (current is SelfUpdateState.Downloading || current is SelfUpdateState.Verifying) return

        val dir = File(appContext.cacheDir, UPDATE_DIR).apply { mkdirs() }
        // Remove stale APKs from previous updates
        dir.listFiles()?.forEach { it.delete() }
        val safeTag = tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, "HyperStream-$safeTag.apk")
        val partial = File(dir, "HyperStream-$safeTag.apk.part")

        val raw = apkUrl.ifBlank { UpdateChannelManager.DEFAULT_FALLBACK_APK }
        val direct = raw.removePrefix("https://gh-proxy.com/")
        val candidates = listOf(direct, UpdateChannelManager.resolveFastCdnUrl(direct)).distinct()

        val result = withContext(Dispatchers.IO) {
            var lastError: String? = null
            for (url in candidates) {
                try {
                    downloadTo(url, partial)
                    _state.value = SelfUpdateState.Verifying
                    verify(appContext, partial, expectedSha256)
                    if (target.exists()) target.delete()
                    if (!partial.renameTo(target)) error("Could not finalize downloaded APK")
                    return@withContext null
                } catch (e: Exception) {
                    partial.delete()
                    lastError = e.message ?: e.javaClass.simpleName
                }
            }
            lastError ?: "Download failed"
        }

        if (result != null) {
            _state.value = SelfUpdateState.Failed(result)
            return
        }
        installOrRequestPermission(appContext, target)
    }

    /** Called when the user returns from the "Install unknown apps" settings screen. */
    fun resumeAfterPermission(context: Context) {
        val s = _state.value
        if (s is SelfUpdateState.NeedsInstallPermission && canInstall(context)) {
            launchInstaller(context.applicationContext, s.apk)
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun installOrRequestPermission(context: Context, apk: File) {
        if (canInstall(context)) {
            launchInstaller(context, apk)
        } else {
            _state.value = SelfUpdateState.NeedsInstallPermission(apk)
            openInstallPermissionSettings(context)
        }
    }

    private fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun launchInstaller(context: Context, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _state.value = SelfUpdateState.Installing(apk)
        } catch (e: Exception) {
            _state.value = SelfUpdateState.Failed("Could not open installer: ${e.message}")
        }
    }

    private fun downloadTo(url: String, out: File) {
        var conn = URL(url).openConnection() as HttpURLConnection
        var redirects = 0
        // Follow cross-host redirects (github.com -> objects.githubusercontent.com) manually
        while (true) {
            conn.apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "HyperStream-Android-Updater")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 6) {
                val loc = conn.getHeaderField("Location") ?: error("Redirect without location")
                conn.disconnect()
                conn = URL(URL(url), loc).openConnection() as HttpURLConnection
                redirects++
                continue
            }
            if (code != 200) error("HTTP $code")
            break
        }

        val total = conn.contentLengthLong
        var downloaded = 0L
        _state.value = SelfUpdateState.Downloading(0L, total)
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var lastEmit = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    downloaded += n
                    if (downloaded - lastEmit >= 256 * 1024) {
                        lastEmit = downloaded
                        _state.value = SelfUpdateState.Downloading(downloaded, total)
                    }
                }
            }
        }
        conn.disconnect()
        if (total > 0 && downloaded != total) error("Incomplete download ($downloaded / $total bytes)")
        _state.value = SelfUpdateState.Downloading(downloaded, if (total > 0) total else downloaded)
    }

    private fun verify(context: Context, apk: File, expectedSha256: String) {
        val expected = expectedSha256.removePrefix("sha256:").trim().lowercase()
        if (expected.isNotEmpty()) {
            val actual = sha256(apk)
            if (actual != expected) error("Checksum mismatch — download corrupted or tampered")
        }

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: error("Downloaded file is not a valid APK")
        if (info.packageName != context.packageName) {
            error("APK package mismatch (${info.packageName})")
        }
        val installed = try {
            @Suppress("DEPRECATION")
            val cur = pm.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cur.longVersionCode else cur.versionCode.toLong()
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
        val incoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        if (incoming < installed) error("Downloaded APK is older than the installed version")
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Converts GitHub markdown release notes into readable plain text for dialogs. */
    fun plainReleaseNotes(markdown: String): String =
        markdown.lines()
            .map { line ->
                line.replace(Regex("^#{1,6}\\s*"), "")
                    .replace("**", "")
                    .replace("`", "")
                    .replace(Regex("^\\s*[-*]\\s+"), "• ")
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
}
