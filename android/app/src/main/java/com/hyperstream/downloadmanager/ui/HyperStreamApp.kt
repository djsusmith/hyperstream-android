package com.hyperstream.downloadmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
            }
        }
    }
}
