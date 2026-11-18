package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperstream.downloadmanager.a2ui.A2uiSurfaceRenderer
import com.hyperstream.downloadmanager.a2ui.HyperStreamA2uiCatalog
import com.hyperstream.downloadmanager.browser.LinkParser
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.util.FileOpener

@Composable
fun DownloadsScreen(
    state: MdmUiState,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onAddDownload: (url: String, connections: Int, category: String, checksum: String?) -> Unit,
    onTogglePauseResume: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit = {},
    onRefreshLink: (Long, String) -> Unit = { _, _ -> },
    onCancelDownload: (Long, Boolean) -> Unit = { _, _ -> },
    onInspectDownload: (DownloadItemUi) -> Unit,
    showSearchBar: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onOpenNewDownloadSheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showFallbackAddSheet by remember { mutableStateOf(false) }
    var itemPendingDelete by remember { mutableStateOf<DownloadItemUi?>(null) }
    var alsoDeleteFromStorage by remember { mutableStateOf(true) }

    // A2UI Hero Surface interactive state
    var heroCollapsed by remember { mutableStateOf(false) }
    var showConnectionMap by remember { mutableStateOf(false) }
    var peakSpeedBps by remember { mutableLongStateOf(0L) }
    val speedHistory = remember { mutableStateListOf<Long>() }

    LaunchedEffect(state.totalDownSpeedBps) {
        if (state.totalDownSpeedBps > peakSpeedBps) {
            peakSpeedBps = state.totalDownSpeedBps
        }
        speedHistory.add(state.totalDownSpeedBps)
        if (speedHistory.size > 24) {
            speedHistory.removeAt(0)
        }
    }

    if (showFallbackAddSheet) {
        NewDownloadSheet(
            defaultConnections = state.settings.maxConnections,
            defaultCategory = selectedCategory,
            onDismiss = { showFallbackAddSheet = false },
            onStartDownload = onAddDownload
        )
    }

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

    val activeDownloads = remember(state.downloads) {
        state.downloads.filter { it.status == "Downloading" || it.status == "Probing" }
    }
    val completedDownloads = remember(state.downloads) {
        state.downloads.filter { it.status == "Completed" }
    }
    val queuedOrPausedDownloads = remember(state.downloads) {
        state.downloads.filter { it.status == "Paused" || it.status == "Queued" || it.status == "Failed" }
    }

    val filteredDownloads = remember(state.downloads, selectedCategory, searchQuery) {
        val byCat = when (selectedCategory) {
            "All" -> state.downloads
            "Downloading" -> state.downloads.filter { it.status == "Downloading" || it.status == "Probing" }
            "Completed", "Finished" -> state.downloads.filter { it.status == "Completed" }
            "Queued", "Unfinished" -> state.downloads.filter { it.status != "Completed" }
            else -> state.downloads.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) byCat
        else byCat.filter { it.name.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) }
    }

    val heroSurface = remember(
        state.totalDownSpeedBps,
        peakSpeedBps,
        activeDownloads.size,
        queuedOrPausedDownloads.size,
        completedDownloads.size,
        state.activeThreadsTotal,
        heroCollapsed,
        showConnectionMap,
        speedHistory.size
    ) {
        HyperStreamA2uiCatalog.buildDownloadsHeroSurface(
            speedBps = state.totalDownSpeedBps,
            peakBps = peakSpeedBps,
            activeCount = activeDownloads.size,
            queuedCount = queuedOrPausedDownloads.size,
            doneCount = completedDownloads.size,
            activeThreads = state.activeThreadsTotal,
            collapsed = heroCollapsed,
            showConnectionMap = showConnectionMap,
            speedHistory = speedHistory.toList()
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Optional inline search bar (toggled from top bar search icon)
        AnimatedVisibility(visible = showSearchBar) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search downloads by name or URL...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // PDM Speedometer Hero Card rendered via A2UI Surface Renderer
        A2uiSurfaceRenderer(
            surface = heroSurface,
            onAction = { action ->
                when (action.name) {
                    "toggle_collapse" -> heroCollapsed = !heroCollapsed
                    "toggle_dial_mode" -> showConnectionMap = !showConnectionMap
                    "pause_all" -> activeDownloads.forEach { onTogglePauseResume(it.id) }
                    "resume_all" -> state.downloads
                        .filter { it.status == "Paused" || it.status == "Queued" }
                        .forEach { onTogglePauseResume(it.id) }
                }
            }
        )

        // PDM-style Filter Pills with live counts
        val filterPills = remember(state.downloads.size, activeDownloads.size, completedDownloads.size, queuedOrPausedDownloads.size) {
            listOf(
                "All" to state.downloads.size,
                "Downloading" to activeDownloads.size,
                "Completed" to completedDownloads.size,
                "Queued" to queuedOrPausedDownloads.size,
                "Video" to state.downloads.count { it.category.equals("Video", ignoreCase = true) },
                "Music" to state.downloads.count { it.category.equals("Music", ignoreCase = true) },
                "Compressed" to state.downloads.count { it.category.equals("Compressed", ignoreCase = true) },
                "Documents" to state.downloads.count { it.category.equals("Documents", ignoreCase = true) },
                "Programs" to state.downloads.count { it.category.equals("Programs", ignoreCase = true) }
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterPills) { (label, count) ->
                val selected = selectedCategory == label
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier.clickable { onSelectCategory(label) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        if (count > 0 || label == "All") {
                            Text(
                                text = count.toString(),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                    text = "No downloads yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap '+' at the top or share a link from YouTube, TikTok, or your browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onOpenNewDownloadSheet?.invoke() ?: run { showFallbackAddSheet = true } },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New download")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDownloads, key = { it.id }) { item ->
                    PdmDownloadItemCard(
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
private fun PdmDownloadItemCard(
    item: DownloadItemUi,
    onTogglePauseResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onInspect: () -> Unit,
    onOpenFile: () -> Unit
) {
    val ext = remember(item.name) {
        item.name.substringAfterLast('.', "").uppercase().take(4).ifBlank { "FILE" }
    }
    val badgeColor = when (ext) {
        "MP4", "MKV", "WEBM", "MOV" -> Color(0xFF8B5CF6)
        "MP3", "FLAC", "AAC", "WAV" -> Color(0xFFEC4899)
        "ISO", "ZIP", "RAR", "7Z", "GZ" -> Color(0xFF3355FF)
        "APK", "EXE", "DEB" -> Color(0xFF10B981)
        "PDF", "DOC", "EPUB" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left File-Type Squircle Badge (PDM style)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = ext,
                            color = badgeColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                        val platform = LinkParser.detectPlatformName(item.url)
                        if (platform != "Direct") {
                            Text(
                                text = platform.take(4).uppercase(),
                                color = badgeColor.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            )
                        }
                    }
                }

                // Middle Filename + Speed Pill + Progress Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.status == "Downloading" && item.speedBps > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = formatSpeed(item.speedBps),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (item.status) {
                                    "Completed" -> Color(0xFF10B981).copy(alpha = 0.14f)
                                    "Failed" -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = item.status,
                                    color = when (item.status) {
                                        "Completed" -> Color(0xFF10B981)
                                        "Failed" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val pct = (item.progressFraction * 100).toInt()
                        val totalStr = if (item.totalBytes > 0) formatBytes(item.totalBytes) else formatBytes(item.downloadedBytes)
                        Text(
                            text = if (item.status == "Completed") totalStr else "$pct% of $totalStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right Circular Progress Ring Action Button (PDM signature)
                if (item.status == "Completed") {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(42.dp)
                            .clickable(onClick = onOpenFile)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open File",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    CircularProgressActionButton(
                        progress = item.progressFraction,
                        isActive = item.status == "Downloading" || item.status == "Probing",
                        isFailed = item.status == "Failed",
                        onClick = if (item.status == "Failed") onRetry else onTogglePauseResume
                    )
                }
            }

            // Error display if failed (with tap-to-refresh hint)
            if (item.status == "Failed" && item.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${item.errorMessage} • Tap card to Refresh Link",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularProgressActionButton(
    progress: Float,
    isActive: Boolean,
    isFailed: Boolean,
    onClick: () -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Icon(
            imageVector = when {
                isFailed -> Icons.Default.Refresh
                isActive -> Icons.Default.Pause
                else -> Icons.Default.PlayArrow
            },
            contentDescription = null,
            tint = activeColor,
            modifier = Modifier.size(18.dp)
        )
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
