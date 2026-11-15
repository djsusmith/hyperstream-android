package com.hyperstream.downloadmanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.core.SettingsUi
import com.hyperstream.downloadmanager.ui.screens.DownloadsScreen
import com.hyperstream.downloadmanager.ui.screens.GrabberScreen
import com.hyperstream.downloadmanager.ui.screens.ProgressInspectorSheet
import com.hyperstream.downloadmanager.ui.screens.QueuesAndSchedulerScreen
import com.hyperstream.downloadmanager.ui.screens.SettingsAndUpdaterScreen
import com.hyperstream.downloadmanager.ui.screens.TorrentsAndRssScreen
import com.hyperstream.downloadmanager.ui.theme.HyperStreamTheme

@Composable
fun HyperStreamApp(
    repository: MdmRepository = remember { MdmRepository() }
) {
    val state by repository.uiState.collectAsState()
    HyperStreamAppContent(
        state = state,
        onAddDownload = { url, conns, cat, checksum ->
            repository.addDownload(url = url, category = cat, connections = conns, checksumExpected = checksum)
        },
        onTogglePauseResume = { repository.togglePauseResume(it) },
        onAddMagnet = { src, seq -> repository.addTorrent(src, seq) },
        onUpdateSettings = { repository.updateSettings(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperStreamAppContent(
    state: MdmUiState,
    initialTab: Int = 0,
    onAddDownload: (url: String, connections: Int, category: String, checksum: String?) -> Unit = { _, _, _, _ -> },
    onTogglePauseResume: (Long) -> Unit = {},
    onAddMagnet: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateSettings: ((SettingsUi) -> SettingsUi) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var selectedCategory by remember { mutableStateOf("All") }
    var inspectedDownload by remember { mutableStateOf<DownloadItemUi?>(null) }

    HyperStreamTheme(
        themeId = state.settings.themeId,
        darkModePref = state.settings.darkMode
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("HyperStream Download Manager", fontWeight = FontWeight.Bold)
                            Text(
                                text = "MDM v${state.settings.upstreamVersion} Core • 128-Thread Engine • Shizuku Ready",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                        label = { Text("Downloads") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Grain, contentDescription = null) },
                        label = { Text("Torrents") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                        label = { Text("Grabber") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        label = { Text("Queues") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                inspectedDownload?.let { item ->
                    ProgressInspectorSheet(item = item)
                }
                when (selectedTab) {
                    0 -> DownloadsScreen(
                        state = state,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        onAddDownload = onAddDownload,
                        onTogglePauseResume = onTogglePauseResume,
                        onInspectDownload = {
                            inspectedDownload = if (inspectedDownload?.id == it.id) null else it
                        }
                    )
                    1 -> TorrentsAndRssScreen(
                        state = state,
                        onAddMagnet = onAddMagnet
                    )
                    2 -> GrabberScreen(
                        onQueueUrls = { urls ->
                            urls.forEach { u -> onAddDownload(u, 128, "Video", null) }
                            selectedTab = 0
                        }
                    )
                    3 -> QueuesAndSchedulerScreen(state = state)
                    4 -> SettingsAndUpdaterScreen(
                        state = state,
                        onUpdateSettings = onUpdateSettings
                    )
                }
            }
        }
    }
}
