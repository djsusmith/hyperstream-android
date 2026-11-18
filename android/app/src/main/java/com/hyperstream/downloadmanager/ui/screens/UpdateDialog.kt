package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.hyperstream.downloadmanager.BuildConfig
import com.hyperstream.downloadmanager.updater.ApkSelfUpdater
import com.hyperstream.downloadmanager.updater.SelfUpdateState
import com.hyperstream.downloadmanager.updater.UpdateCheckResult
import kotlinx.coroutines.launch

/**
 * In-app update dialog: downloads the APK inside HyperStream with live progress, verifies it and
 * launches the Android installer. The installer then offers "Open" to restart on the new version.
 */
@Composable
fun UpdateDialog(
    update: UpdateCheckResult.UpdateAvailable,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selfUpdate by ApkSelfUpdater.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // When returning from "Install unknown apps" settings, continue the install automatically.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ApkSelfUpdater.resumeAfterPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val busy = selfUpdate is SelfUpdateState.Downloading || selfUpdate is SelfUpdateState.Verifying

    fun start() {
        scope.launch {
            ApkSelfUpdater.downloadAndInstall(context, update.apkUrl, update.tagName, update.sha256)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(
                text = "Update available: v${update.version}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Installed: v${BuildConfig.VERSION_NAME}  →  New: v${update.version}",
                    style = MaterialTheme.typography.bodyMedium
                )
                when (val s = selfUpdate) {
                    is SelfUpdateState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { s.fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Downloading… ${formatMb(s.downloadedBytes)}" +
                                if (s.totalBytes > 0) " / ${formatMb(s.totalBytes)} (${(s.fraction * 100).toInt()}%)" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    SelfUpdateState.Verifying -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Verifying checksum and signature…", style = MaterialTheme.typography.bodySmall)
                    }
                    is SelfUpdateState.NeedsInstallPermission -> Text(
                        "Allow \"Install unknown apps\" for HyperStream, then come back — the install will continue automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is SelfUpdateState.Installing -> Text(
                        "Installer opened. Tap Update, then Open to restart HyperStream on v${update.version}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is SelfUpdateState.Failed -> Text(
                        "Update failed: ${s.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    SelfUpdateState.Idle -> Unit
                }
                if (update.releaseNotes.isNotBlank()) {
                    Text(
                        text = ApkSelfUpdater.plainReleaseNotes(update.releaseNotes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            when (val s = selfUpdate) {
                is SelfUpdateState.Downloading, SelfUpdateState.Verifying ->
                    Button(onClick = {}, enabled = false) { Text("Updating…") }
                is SelfUpdateState.NeedsInstallPermission ->
                    Button(onClick = { ApkSelfUpdater.openInstallPermissionSettings(context) }) { Text("Allow install") }
                is SelfUpdateState.Installing ->
                    Button(onClick = { ApkSelfUpdater.installOrRequestPermission(context, s.apk) }) { Text("Install") }
                is SelfUpdateState.Failed ->
                    Button(onClick = { start() }) { Text("Retry") }
                SelfUpdateState.Idle ->
                    Button(onClick = { start() }) { Text("Update now") }
            }
        },
        dismissButton = {
            if (!busy) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    )
}

private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)
