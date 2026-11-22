package com.hyperstream.downloadmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
import com.hyperstream.downloadmanager.ui.screens.NotificationsScreen
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
    val pendingExternal by repository.pendingExternalDownload.collectAsState()
    val shizukuManager = remember(state.settings.shizukuEnabled) {
        ShizukuManager(enabled = state.settings.shizukuEnabled)
    }

    HyperStreamAppContent(
        state = state,
        repository = repository,
        pendingSharedUrl = pendingUrl,
        pendingExternalDownload = pendingExternal,
        onClearPendingSharedUrl = { repository.setPendingSharedUrl(null) },
        onClearPendingExternalDownload = { repository.setPendingExternalDownload(null) },
        onAddDownload = { url, fileName, conns, cat ->
            repository.addDownload(url = url, fileName = fileName, category = cat, connections = conns)
        },
        onAddDownloadWithHeaders = { url, fileName, conns, cat, ua, ref, cookie ->
            repository.addDownload(
                url = url,
                fileName = fileName,
                category = cat,
                connections = conns,
                userAgent = ua,
                referer = ref,
                cookie = cookie
            )
        },
        onTogglePauseResume = { repository.togglePauseResume(it) },
        onRetryDownload = { repository.retryDownload(it) },
        onRefreshLink = { id, newUrl -> repository.refreshLink(id, newUrl) },
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
    pendingExternalDownload: com.hyperstream.downloadmanager.browser.ExternalDownloadRequest? = null,
    onClearPendingSharedUrl: () -> Unit = {},
    onClearPendingExternalDownload: () -> Unit = {},
    onAddDownload: (url: String, fileName: String?, connections: Int, category: String) -> Unit = { _, _, _, _ -> },
    onAddDownloadWithHeaders: (url: String, fileName: String?, connections: Int, category: String, userAgent: String?, referer: String?, cookie: String?) -> Unit = { u, f, c, cat, _, _, _ -> },
    onTogglePauseResume: (Long) -> Unit = {},
    onRetryDownload: (Long) -> Unit = {},
    onRefreshLink: (Long, String) -> Unit = { _, _ -> },
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
    var showNewDownloadSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val context = LocalContext.current
    var availableUpdate by remember { mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val pendingOpenNotif by repository.pendingOpenNotificationsTab.collectAsState()

    LaunchedEffect(pendingOpenNotif) {
        if (pendingOpenNotif) {
            selectedTab = 2
            repository.setPendingOpenNotificationsTab(false)
        }
    }

    // Automatic update check on app startup (GitHub Releases API)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val channel = if (state.settings.updateChannel.contains("Nightly", ignoreCase = true)) {
                UpdateChannel.NIGHTLY_BUGFIX
            } else {
                UpdateChannel.STABLE
            }
            val result = UpdateChannelManager.checkForUpdates(
                currentVersion = com.hyperstream.downloadmanager.BuildConfig.VERSION_NAME,
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
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Column {
                            Text("HyperStream", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text(
                                text = "128-Thread Download Manager",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "v${upd.version}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        // PDM-style 3 rounded-square action buttons (+, Search, Grabber)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { showNewDownloadSheet = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "New Download",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (showSearchBar) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        selectedTab = 0
                                        showSearchBar = !showSearchBar
                                        if (!showSearchBar) searchQuery = ""
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Search Downloads",
                                        tint = if (showSearchBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedTab == 2) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedTab = 2 }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = "A2UI Notifications",
                                        tint = if (selectedTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedTab = 1 }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = "Media Grabber",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                // PDM-style Floating Pill Bottom Navigation Bar
                val activeCount = state.downloads.count { it.status == "Downloading" || it.status == "Probing" }
                val totalNotifCount = state.downloads.size
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            val itemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                colors = itemColors,
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (activeCount > 0) {
                                                Badge(containerColor = Color(0xFFEF4444)) {
                                                    Text(activeCount.toString(), color = Color.White)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                                    }
                                },
                                label = { Text("Downloads", maxLines = 1, fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                colors = itemColors,
                                icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                                label = { Text("Grabber", maxLines = 1, fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                colors = itemColors,
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (activeCount > 0) {
                                                Badge(containerColor = Color(0xFF3F5EFB)) {
                                                    Text(activeCount.toString(), color = Color.White)
                                                }
                                            } else if (totalNotifCount > 0) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                                    Text(
                                                        totalNotifCount.toString(),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null)
                                    }
                                },
                                label = { Text("Notify", maxLines = 1, fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                colors = itemColors,
                                icon = { Icon(Icons.Default.Grain, contentDescription = null) },
                                label = { Text("Torrents", maxLines = 1, fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 4,
                                onClick = { selectedTab = 4 },
                                colors = itemColors,
                                icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                label = { Text("Schedule", maxLines = 1, fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 5,
                                onClick = { selectedTab = 5 },
                                colors = itemColors,
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Settings", maxLines = 1, fontSize = 10.sp) }
                            )
                        }
                    }
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
                        onRefreshLink = onRefreshLink,
                        onCancelDownload = onCancelDownload,
                        onInspectDownload = { item ->
                            inspectedDownloadId = item.id
                        },
                        showSearchBar = showSearchBar,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onOpenNewDownloadSheet = { showNewDownloadSheet = true }
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
                    2 -> NotificationsScreen(
                        state = state,
                        onTogglePauseResume = onTogglePauseResume,
                        onRetryDownload = onRetryDownload,
                        onCancelDownload = onCancelDownload,
                        onInspectDownload = { item ->
                            inspectedDownloadId = item.id
                        }
                    )
                    3 -> TorrentsAndRssScreen(
                        state = state,
                        onAddMagnet = onAddMagnet
                    )
                    4 -> QueuesAndSchedulerScreen(
                        state = state,
                        onToggleQueue = onToggleQueue
                    )
                    5 -> SettingsAndUpdaterScreen(
                        state = state,
                        onUpdateSettings = onUpdateSettings,
                        onWhitelistDoze = onWhitelistDoze,
                        onSilentSelfUpdate = onSilentSelfUpdate,
                        onOpenCredits = { selectedTab = 6 }
                    )
                    6 -> CreditsScreen()
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
                            onDelete = { del -> onCancelDownload(id, del) },
                            onRefreshLink = { newUrl -> onRefreshLink(id, newUrl) }
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

                // PDM-style New Download Sheet (also opens automatically for Vivaldi/Quetta browser handoffs)
                if (showNewDownloadSheet || pendingExternalDownload != null) {
                    com.hyperstream.downloadmanager.ui.screens.NewDownloadSheet(
                        defaultConnections = state.settings.maxConnections,
                        defaultCategory = selectedCategory,
                        defaultDownloadDir = state.settings.defaultDownloadDir,
                        useCategoryFolders = state.settings.useCategoryFolders,
                        initialRequest = pendingExternalDownload,
                        onDismiss = {
                            showNewDownloadSheet = false
                            onClearPendingExternalDownload()
                        },
                        onStartDownload = { url, conns, cat, _ ->
                            onAddDownload(url, null, conns, cat)
                            selectedTab = 0
                        },
                        onStartDownloadWithFileName = { url, fileName, conns, cat ->
                            onAddDownload(url, fileName, conns, cat)
                            selectedTab = 0
                        },
                        onStartDownloadWithHeaders = { url, fileName, conns, cat, ua, ref, cookie ->
                            onAddDownloadWithHeaders(url, fileName, conns, cat, ua, ref, cookie)
                            selectedTab = 0
                        },
                        onOpenFormatPicker = { url ->
                            onClearPendingExternalDownload()
                            activePickerUrl = url
                        }
                    )
                }

                // In-App Update Dialog: downloads, verifies and installs the APK inside HyperStream
                if (showUpdateDialog && availableUpdate != null) {
                    com.hyperstream.downloadmanager.ui.screens.UpdateDialog(
                        update = availableUpdate!!,
                        onDismiss = { showUpdateDialog = false }
                    )
                }
            }
        }
    }
}
