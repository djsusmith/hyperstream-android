package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.browser.LinkParser
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.ui.components.SegmentMapBar
import com.hyperstream.downloadmanager.util.FileOpener

@Composable
fun DownloadsScreen(
    state: MdmUiState,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onAddDownload: (url: String, connections: Int, category: String, checksum: String?) -> Unit,
    onTogglePauseResume: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit = {},
    onCancelDownload: (Long, Boolean) -> Unit = { _, _ -> },
    onInspectDownload: (DownloadItemUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddPanel by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf("") }
    var newChecksum by remember { mutableStateOf("") }
    var newConnections by remember { mutableFloatStateOf(state.settings.maxConnections.toFloat()) }
    var itemPendingDelete by remember { mutableStateOf<DownloadItemUi?>(null) }
    var alsoDeleteFromStorage by remember { mutableStateOf(true) }

    // Permanent Deletion Confirmation Dialog
    itemPendingDelete?.let { targetItem ->
        AlertDialog(
            onDismissRequest = { itemPendingDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Download?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you sure you want to remove \"${targetItem.name}\"?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoDeleteFromStorage = !alsoDeleteFromStorage }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = alsoDeleteFromStorage,
                            onCheckedChange = { alsoDeleteFromStorage = it }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Permanently delete file from storage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (alsoDeleteFromStorage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val del = alsoDeleteFromStorage
                        itemPendingDelete = null
                        if (del) {
                            FileOpener.deletePhysicalFiles(targetItem)
                        }
                        onCancelDownload(targetItem.id, del)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

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
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = formatSpeed(state.totalDownSpeedBps) + " ↓  •  " + formatSpeed(state.totalUpSpeedBps) + " ↑",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.activeThreadsTotal} Active Threads (Max 128) • Free ${formatBytes(state.freeDiskBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (state.wakeLockActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (state.wakeLockActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.clickable { showAddPanel = !showAddPanel }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (state.wakeLockActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "WakeLock",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.wakeLockActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.wakeLockActive) "ON" else "IDLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.wakeLockActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        // Category Filter Rail
        val categories = listOf("All", "Unfinished", "Finished", "Video", "Music", "Compressed", "Documents", "Programs")
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
        if (filteredDownloads.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No transfers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap '+' Add URL (128T) or share a link from YouTube, TikTok, Facebook, or your browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDownloads, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onTogglePauseResume = { onTogglePauseResume(item.id) },
                        onRetry = { onRetryDownload(item.id) },
                        onDelete = { itemPendingDelete = item },
                        onInspect = { onInspectDownload(item) },
                        onOpenFile = { FileOpener.openFile(context, item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItemUi,
    onTogglePauseResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onInspect: () -> Unit,
    onOpenFile: () -> Unit = {}
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val platform = LinkParser.detectPlatformName(item.url)
                        if (platform != "Direct") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = platform,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val flag = countryFlagEmoji(item.countryCode)
                    val etaStr = item.etaSeconds?.let { " • ETA ${formatEta(it)}" } ?: ""
                    Text(
                        text = "${item.category} • ${item.status}$etaStr • $flag ${item.countryName ?: "Direct"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (item.status == "Completed") {
                        Button(
                            onClick = onOpenFile,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open File", modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (item.status == "Failed") {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        IconButton(onClick = onTogglePauseResume) {
                            val icon = when (item.status) {
                                "Downloading", "Probing" -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            }
                            Icon(icon, contentDescription = item.status, tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Error display if failed
            if (item.status == "Failed" && item.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Text(
                            text = item.errorMessage ?: "Failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val downloadedStr = formatBytes(item.downloadedBytes)
                val totalStr = if (item.totalBytes > 0) formatBytes(item.totalBytes) else "—"
                Text(
                    text = "$downloadedStr / $totalStr (${(item.progressFraction * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${item.activeConnections}/${item.maxConnections}T • ${formatSpeed(item.speedBps)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Live 128-Connection Segment Map Bar
            if (item.status == "Downloading" || item.segments.isNotEmpty()) {
                SegmentMapBar(
                    segments = item.segments,
                    maxConnections = item.maxConnections,
                    activeConnections = item.activeConnections
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

fun formatEta(seconds: Long): String {
    if (seconds <= 0L) return "0s"
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
