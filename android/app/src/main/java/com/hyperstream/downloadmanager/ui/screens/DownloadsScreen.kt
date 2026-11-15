package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.ui.components.SegmentMapBar

@Composable
fun DownloadsScreen(
    state: MdmUiState,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onAddDownload: (url: String, connections: Int, category: String, checksum: String?) -> Unit,
    onTogglePauseResume: (Long) -> Unit,
    onInspectDownload: (DownloadItemUi) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddPanel by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf("") }
    var newChecksum by remember { mutableStateOf("") }
    var newConnections by remember { mutableFloatStateOf(state.settings.maxConnections.toFloat()) }

    val filteredDownloads = remember(state.downloads, selectedCategory) {
        when (selectedCategory) {
            "All" -> state.downloads
            "Unfinished" -> state.downloads.filter { it.status != "Completed" }
            "Finished" -> state.downloads.filter { it.status == "Completed" }
            else -> state.downloads.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Telemetry Banner (Speed, Active 128 Threads, WakeLock badge, Free Disk)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = formatSpeed(state.totalDownSpeedBps) + " ↓  •  " + formatSpeed(state.totalUpSpeedBps) + " ↑",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.activeThreadsTotal} Active Threads (Max 128) • Free ${formatBytes(state.freeDiskBytes)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                AssistChip(
                    onClick = { showAddPanel = !showAddPanel },
                    label = { Text(if (state.wakeLockActive) "WakeLock ON" else "Idle") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (state.wakeLockActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                )
            }
        }

        // Category Filter Rail
        val categories = listOf("All", "Unfinished", "Finished", "Video", "Music", "Programs", "Compressed", "Documents")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat) }
                )
            }
            item {
                Button(onClick = { showAddPanel = !showAddPanel }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Add URL (128T)")
                }
            }
        }

        if (showAddPanel) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Multi-Connection Download (HTTP/HTTPS/FTP/FTPS/GDrive/MediaFire)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("Download URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newChecksum,
                        onValueChange = { newChecksum = it },
                        label = { Text("Expected Checksum (Optional: sha256:..., md5:..., crc32:...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Max Simultaneous Connections: ${newConnections.toInt()} / 128")
                    Slider(
                        value = newConnections,
                        onValueChange = { newConnections = it },
                        valueRange = 1f..128f
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (newUrl.isNotBlank()) {
                                    onAddDownload(
                                        newUrl.trim(),
                                        newConnections.toInt(),
                                        selectedCategory.takeIf { it != "All" && it != "Unfinished" && it != "Finished" } ?: "General",
                                        newChecksum.takeIf { it.isNotBlank() }
                                    )
                                    newUrl = ""
                                    newChecksum = ""
                                    showAddPanel = false
                                }
                            }
                        ) {
                            Text("Start Download")
                        }
                    }
                }
            }
        }

        // Download List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredDownloads, key = { it.id }) { item ->
                DownloadItemCard(
                    item = item,
                    onTogglePauseResume = { onTogglePauseResume(item.id) },
                    onInspect = { onInspectDownload(item) }
                )
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItemUi,
    onTogglePauseResume: () -> Unit,
    onInspect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val flag = countryFlagEmoji(item.countryCode)
                    Text(
                        text = "${item.category} • Queue: ${item.queue} • $flag ${item.countryName ?: "Direct"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onTogglePauseResume) {
                    val icon = when (item.status) {
                        "Downloading" -> Icons.Default.Pause
                        "Completed" -> Icons.Default.CheckCircle
                        else -> Icons.Default.PlayArrow
                    }
                    Icon(icon, contentDescription = item.status, tint = MaterialTheme.colorScheme.primary)
                }
            }

            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            SegmentMapBar(
                segments = item.segments,
                maxConnections = item.maxConnections,
                activeConnections = item.activeConnections
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)} (${(item.progressFraction * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelMedium
                )
                val statusRight = if (item.status == "Downloading") {
                    "${formatSpeed(item.speedBps)} • ETA ${item.etaSeconds ?: 0}s"
                } else {
                    item.status + (if (item.checksumVerified == true) " ✓ Verified" else "")
                }
                Text(
                    text = statusRight,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun countryFlagEmoji(code: String?): String {
    if (code == null || code.length != 2) return "🌐"
    val first = Character.codePointAt(code.uppercase(), 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(code.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

fun formatSpeed(bps: Long): String {
    if (bps <= 0L) return "0 B/s"
    return "${formatBytes(bps)}/s"
}
