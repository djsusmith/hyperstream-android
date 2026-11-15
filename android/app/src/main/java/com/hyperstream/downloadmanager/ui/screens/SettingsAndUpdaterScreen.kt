package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.core.SettingsUi
import com.hyperstream.downloadmanager.ui.theme.MdmThemeStyle
import com.hyperstream.downloadmanager.updater.UpdateChannel
import com.hyperstream.downloadmanager.updater.UpdateChannelManager
import com.hyperstream.downloadmanager.updater.UpdateCheckResult
import kotlinx.coroutines.launch

@Composable
fun SettingsAndUpdaterScreen(
    state: MdmUiState,
    onUpdateSettings: ((SettingsUi) -> SettingsUi) -> Unit,
    onWhitelistDoze: () -> String = { "Doze whitelist requested via Shizuku" },
    onSilentSelfUpdate: () -> String = { "Silent APK update triggered via Shizuku" },
    onOpenCredits: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shizukuStatusMessage by remember { mutableStateOf<String?>(null) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult>(UpdateCheckResult.Idle) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 128-Thread Engine & Speed Limiter
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Engine Concurrency (Unlocked 1..128 Threads)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Default Connections per Download: ${s.maxConnections} / 128")
                    Slider(
                        value = s.maxConnections.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(maxConnections = v.toInt().coerceIn(1, 128)) } },
                        valueRange = 1f..128f
                    )
                    Text(
                        text = "Min Split Size: ${s.minSplitKb} KB • Global Speed Cap: ${if (s.speedLimitKbps == 0L) "Unlimited" else "${s.speedLimitKbps} KB/s"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 2. Upstream Sync & In-App Update Checker
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "App Updates & Upstream Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Current Installed Version: v0.6.0\nUpdate Channel determines which releases you receive from GitHub.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = s.updateChannel == "stable",
                            onClick = {
                                onUpdateSettings { it.copy(updateChannel = "stable") }
                                updateResult = UpdateCheckResult.Idle
                            },
                            label = { Text("Stable Channel") }
                        )
                        FilterChip(
                            selected = s.updateChannel == "nightly",
                            onClick = {
                                onUpdateSettings { it.copy(updateChannel = "nightly") }
                                updateResult = UpdateCheckResult.Idle
                            },
                            label = { Text("Nightly / Bug-Fix") }
                        )
                    }

                    // Check for Updates Action Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    updateResult = UpdateCheckResult.Checking
                                    val channel = if (s.updateChannel == "nightly") {
                                        UpdateChannel.NIGHTLY_BUGFIX
                                    } else {
                                        UpdateChannel.STABLE
                                    }
                                    updateResult = UpdateChannelManager.checkForUpdates(
                                        currentVersion = "0.6.0",
                                        channel = channel
                                    )
                                }
                            },
                            enabled = updateResult !is UpdateCheckResult.Checking
                        ) {
                            if (updateResult is UpdateCheckResult.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(" Checking...", modifier = Modifier.padding(start = 8.dp))
                            } else {
                                Text("Check for Updates")
                            }
                        }
                    }

                    // Update Result Display Banner
                    when (val res = updateResult) {
                        is UpdateCheckResult.Checking -> {
                            Text(
                                text = "Connecting to GitHub Releases API...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is UpdateCheckResult.UpToDate -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "✓ Up to date (v${res.currentVersion})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Checked at ${res.checkedAt}. No new updates available on ${s.updateChannel} channel.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        is UpdateCheckResult.UpdateAvailable -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "🚀 Update Available: ${res.releaseName}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Tag: ${res.tagName} ${if (res.isPrerelease) "(Pre-release)" else "(Stable)"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (res.releaseNotes.isNotBlank()) {
                                        Text(
                                            text = res.releaseNotes.take(200) + if (res.releaseNotes.length > 200) "..." else "",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            UpdateChannelManager.startApkDownload(context, res.apkUrl)
                                        }) {
                                            Text("Download APK")
                                        }
                                        if (s.shizukuEnabled) {
                                            OutlinedButton(onClick = {
                                                shizukuStatusMessage = onSilentSelfUpdate()
                                            }) {
                                                Text("Silent Install via Shizuku")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is UpdateCheckResult.Error -> {
                            Text(
                                text = "Update check failed: ${res.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        UpdateCheckResult.Idle -> {
                            // No status yet
                        }
                    }
                }
            }
        }

        // 3. Optional Shizuku Integration
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shizuku Enhanced Mode (Optional • uid=2000)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "1-tap Doze exemption, silent APK install (`pm install -r`), `/Android/data` access, and native helper execution.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = s.shizukuEnabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(shizukuEnabled = enabled) } }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { shizukuStatusMessage = onWhitelistDoze() },
                            enabled = s.shizukuEnabled
                        ) {
                            Text("Whitelist Doze")
                        }
                        Button(
                            onClick = { shizukuStatusMessage = onSilentSelfUpdate() },
                            enabled = s.shizukuEnabled
                        ) {
                            Text("Silent Self-Update")
                        }
                    }
                    shizukuStatusMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 4. MDM Themes, 86 Locales & Network / Debrid / Remote Control
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Themes, 86 Languages (RTL Ready), Debrid & Network Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MdmThemeStyle.entries.forEach { style ->
                            FilterChip(
                                selected = s.themeId == style.id,
                                onClick = { onUpdateSettings { it.copy(themeId = style.id) } },
                                label = { Text(style.title) }
                            )
                        }
                    }
                    Text(
                        text = "Active Locale: ${s.localeCode.uppercase()} (86 upstream TSV catalogs bundled, including RTL Arabic/Hebrew/Persian/Urdu) • DNS: ${s.dnsMode} (${s.customDnsServer}) • Remote Control HTTPS Port: ${s.remoteControlPort}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 5. Creator Credits Summary Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = HyperStreamCredits.HEADLINE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Contact: ${HyperStreamCredits.CREATOR_EMAIL}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = onOpenCredits) {
                        Text("Open Full Credits Page")
                    }
                }
            }
        }
    }
}
