package com.hyperstream.downloadmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.core.SettingsUi
import com.hyperstream.downloadmanager.shizuku.ShizukuManager
import com.hyperstream.downloadmanager.shizuku.ShizukuResult
import com.hyperstream.downloadmanager.ui.components.SmartFormatPickerSheet
import com.hyperstream.downloadmanager.ui.screens.CreditsScreen
import com.hyperstream.downloadmanager.ui.screens.DownloadsScreen
import com.hyperstream.downloadmanager.ui.screens.GrabberScreen
import com.hyperstream.downloadmanager.ui.screens.ProgressInspectorSheet
import com.hyperstream.downloadmanager.ui.screens.QueuesAndSchedulerScreen
import com.hyperstream.downloadmanager.ui.screens.SettingsAndUpdaterScreen
import com.hyperstream.downloadmanager.ui.screens.TorrentsAndRssScreen
import com.hyperstream.downloadmanager.ui.theme.HyperStreamTheme
import com.hyperstream.downloadmanager.updater.UpdateChannel
import com.hyperstream.downloadmanager.updater.UpdateChannelManager
import com.hyperstream.downloadmanager.updater.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HyperStreamApp(
    repository: MdmRepository = remember { MdmRepository.shared }
) {
    val state by repository.uiState.collectAsState()
    val pendingUrl by repository.pendingSharedUrl.collectAsState()
    val shizukuManager = remember(state.settings.shizukuEnabled) {
        ShizukuManager(enabled = state.settings.shizukuEnabled)
    }

    HyperStreamAppContent(
        state = state,
        repository = repository,
        pendingSharedUrl = pendingUrl,
        onClearPendingSharedUrl = { repository.setPendingSharedUrl(null) },
        onAddDownload = { url, fileName, conns, cat ->
            repository.addDownload(url = url, fileName = fileName, category = cat, connections = conns)
        },
        onTogglePauseResume = { repository.togglePauseResume(it) },
        onRetryDownload = { repository.retryDownload(it) },
        onCancelDownload = { id, del -> repository.cancelDownload(id, del) },
        onToggleQueue = { qName, running -> repository.toggleQueue(qName, running) },
        onAddMagnet = { src, seq -> repository.addTorrent(src, seq) },
        onUpdateSettings = { repository.updateSettings(it) },
        onWhitelistDoze = {
            when (val res = shizukuManager.whitelistFromDoze()) {
                is ShizukuResult.Success -> "Shizuku: Doze exemption applied (${res.output})"
                is ShizukuResult.Unavailable -> "Shizuku unavailable: ${res.reason}"
                is ShizukuResult.Failure -> "Shizuku error (${res.exitCode}): ${res.error}"
            }
        },
        onSilentSelfUpdate = {
            val apkPath = "/storage/emulated/0/Download/HyperStream-v${state.settings.upstreamVersion}-arm64.apk"
            when (val res = shizukuManager.silentInstallApk(apkPath)) {
                is ShizukuResult.Success -> "Shizuku: Installed $apkPath (${res.output})"
                is ShizukuResult.Unavailable -> "Shizuku unavailable: ${res.reason}"
                is ShizukuResult.Failure -> "Shizuku error (${res.exitCode}): ${res.error}"
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperStreamAppContent(
    state: MdmUiState,
    repository: MdmRepository = remember { MdmRepository.shared },
    initialTab: Int = 0,
    pendingSharedUrl: String? = null,
    onClearPendingSharedUrl: () -> Unit = {},
    onAddDownload: (url: String, fileName: String?, connections: Int, category: String) -> Unit = { _, _, _, _ -> },
    onTogglePauseResume: (Long) -> Unit = {},
    onRetryDownload: (Long) -> Unit = {},
    onCancelDownload: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleQueue: (String, Boolean) -> Unit = { _, _ -> },
    onAddMagnet: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateSettings: ((SettingsUi) -> SettingsUi) -> Unit = {},
    onWhitelistDoze: () -> String = { "Doze whitelist requested via Shizuku" },
    onSilentSelfUpdate: () -> String = { "Silent APK update triggered via Shizuku" }
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var selectedCategory by remember { mutableStateOf("All") }
    var inspectedDownloadId by remember { mutableStateOf<Long?>(null) }
    var activePickerUrl by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    var availableUpdate by remember { mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Automatic update check on app startup (GitHub Releases API)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val channel = if (state.settings.updateChannel.contains("Nightly", ignoreCase = true)) {
                UpdateChannel.NIGHTLY_BUGFIX
            } else {
                UpdateChannel.STABLE
            }
            val result = UpdateChannelManager.checkForUpdates(
                currentVersion = "0.6.0",
                channel = channel
            )
            if (result is UpdateCheckResult.UpdateAvailable) {
                availableUpdate = result
                showUpdateDialog = true
            }
        }
    }

    // When a link is shared into HyperStream from another app, open the smart format sheet
    val sheetUrl = pendingSharedUrl ?: activePickerUrl

    HyperStreamTheme(
        themeId = state.settings.themeId,
        darkModePref = state.settings.darkMode
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("HyperStream", fontWeight = FontWeight.Bold)
                            Text(
                                text = "128-Thread Multi-Engine • Ahnaf Hasan Susmith",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    actions = {
                        availableUpdate?.let { upd ->
                            Button(
                                onClick = { showUpdateDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "🚀 Update v${upd.version}",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(onClick = { selectedTab = 5 }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Credits & About"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                // 5 Bottom Tabs: Labels never wrap on any Android phone screen size
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                        label = { Text("Downloads", maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                        label = { Text("Grabber", maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Grain, contentDescription = null) },
                        label = { Text("Torrents", maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        label = { Text("Queues", maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings", maxLines = 1) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> DownloadsScreen(
                        state = state,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        onAddDownload = { url, conns, cat, _ ->
                            onAddDownload(url, null, conns, cat)
                        },
                        onTogglePauseResume = onTogglePauseResume,
                        onRetryDownload = onRetryDownload,
                        onCancelDownload = onCancelDownload,
                        onInspectDownload = { item ->
                            inspectedDownloadId = item.id
                        }
                    )
                    1 -> GrabberScreen(
                        repository = repository,
                        onOpenFormatPicker = { url ->
                            activePickerUrl = url
                        },
                        onQueueUrls = { urls ->
                            urls.forEach { u -> onAddDownload(u, null, 128, "Video") }
                            selectedTab = 0
                        }
                    )
                    2 -> TorrentsAndRssScreen(
                        state = state,
                        onAddMagnet = onAddMagnet
                    )
                    3 -> QueuesAndSchedulerScreen(
                        state = state,
                        onToggleQueue = onToggleQueue
                    )
                    4 -> SettingsAndUpdaterScreen(
                        state = state,
                        onUpdateSettings = onUpdateSettings,
                        onWhitelistDoze = onWhitelistDoze,
                        onSilentSelfUpdate = onSilentSelfUpdate,
                        onOpenCredits = { selectedTab = 5 }
                    )
                    5 -> CreditsScreen()
                }

                // ModalBottomSheet for 128-Thread Segment Inspector (Dismissible, never traps the user across tabs)
                inspectedDownloadId?.let { id ->
                    val liveItem = state.downloads.find { it.id == id }
                    if (liveItem != null) {
                        ProgressInspectorSheet(
                            item = liveItem,
                            onDismiss = { inspectedDownloadId = null },
                            onTogglePauseResume = { onTogglePauseResume(id) },
                            onRetry = { onRetryDownload(id) },
                            onDelete = { del -> onCancelDownload(id, del) }
                        )
                    } else {
                        inspectedDownloadId = null
                    }
                }

                // Smart Format & Quality Picker Sheet (YouTube, TikTok No-WM, Instagram, Facebook, MP3 choices)
                sheetUrl?.let { targetUrl ->
                    SmartFormatPickerSheet(
                        url = targetUrl,
                        repository = repository,
                        onDismiss = {
                            activePickerUrl = null
                            onClearPendingSharedUrl()
                        },
                        onDownloadSelected = { streamUrl, fileName, cat, conns ->
                            onAddDownload(streamUrl, fileName, conns, cat)
                            selectedTab = 0 // Switch to Downloads tab to see the live download
                            activePickerUrl = null
                            onClearPendingSharedUrl()
                        }
                    )
                }

                // In-App Update Alert Dialog (triggers when new GitHub release is detected)
                if (showUpdateDialog && availableUpdate != null) {
                    val update = availableUpdate!!
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = {
                            Text(
                                text = "🚀 Update Available: ${update.releaseName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "A new version of HyperStream is ready (${update.tagName}).",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (update.releaseNotes.isNotBlank()) {
                                    Text(
                                        text = update.releaseNotes.take(250) + if (update.releaseNotes.length > 250) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                showUpdateDialog = false
                                UpdateChannelManager.startApkDownload(context, update.apkUrl)
                            }) {
                                Text("Download APK")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text("Later")
                            }
                        }
                    )
                }
            }
        }
    }
}
