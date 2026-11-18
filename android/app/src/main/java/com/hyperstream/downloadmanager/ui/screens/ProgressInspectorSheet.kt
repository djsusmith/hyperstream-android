package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.ui.components.SegmentMapBar
import com.hyperstream.downloadmanager.util.FileOpener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressInspectorSheet(
    item: DownloadItemUi,
    onDismiss: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: (deleteFile: Boolean) -> Unit,
    onRefreshLink: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var alsoDeleteFromStorage by remember { mutableStateOf(true) }
    var showRefreshLinkDialog by remember { mutableStateOf(false) }
    var refreshUrlInput by remember(item.url) { mutableStateOf(item.url) }

    if (showRefreshLinkDialog) {
        AlertDialog(
            onDismissRequest = { showRefreshLinkDialog = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Refresh Download Link", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Update expired streaming or CDN tokens (YouTube, TikTok, file hosts) and seamlessly resume existing chunks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = refreshUrlInput,
                        onValueChange = { refreshUrlInput = it },
                        label = { Text("New Download URL") },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = refreshUrlInput.trim()
                        if (trimmed.isNotBlank()) {
                            onRefreshLink?.invoke(trimmed)
                            showRefreshLinkDialog = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("Refresh & Resume")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefreshLinkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Download?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you sure you want to remove \"${item.name}\"?",
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
                        showDeleteConfirmDialog = false
                        if (alsoDeleteFromStorage) {
                            FileOpener.deletePhysicalFiles(item)
                        }
                        onDelete(alsoDeleteFromStorage)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val statusColor = when (item.status) {
                        "Downloading" -> Color(0xFF00E676)
                        "Completed" -> MaterialTheme.colorScheme.primary
                        "Probing" -> Color(0xFFFFB300)
                        "Failed" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = "128-Thread Segment Inspector",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Inspector")
                }
            }

            // File Name & Details
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val flag = countryFlagEmoji(item.countryCode)
                val locStr = item.countryName ?: "Direct CDN"
                Text(
                    text = "Status: ${item.status} • $flag $locStr • Queue: ${item.queue} • Max ${item.maxConnections}T",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val resolvedFile = FileOpener.resolveFile(item)
                Text(
                    text = "File: ${resolvedFile.absolutePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Error display if failed
            if (item.status == "Failed" && item.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = item.errorMessage ?: "Download failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Live Segment Map Bar
            SegmentMapBar(
                segments = item.segments,
                maxConnections = item.maxConnections,
                activeConnections = item.activeConnections
            )

            // Telemetry: Size & Speed
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val downloadedStr = formatBytes(item.downloadedBytes)
                    val totalStr = if (item.totalBytes > 0) formatBytes(item.totalBytes) else "Unknown"
                    Text(
                        text = "$downloadedStr / $totalStr (${(item.progressFraction * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatSpeed(item.speedBps),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider()

            // Per-Connection Workers (up to 32 shown)
            Text(
                text = "Worker Connections (${item.activeConnections} Active / ${item.maxConnections} Max)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(item.segments.take(32), key = { it.index }) { seg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val rangeText = if (seg.end > 0) {
                            "Conn #${seg.index + 1} [${formatBytes(seg.pos)} → ${formatBytes(seg.end)}]"
                        } else {
                            "Conn #${seg.index + 1} [Awaiting range probe]"
                        }
                        Text(
                            text = rangeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val statusText = when {
                            seg.active -> formatSpeed(seg.speedBps)
                            seg.end > 0 && seg.pos >= seg.end -> "Finished"
                            item.status == "Downloading" -> "Connecting..."
                            else -> item.status
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (seg.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontWeight = if (seg.active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            HorizontalDivider()

            // Action Buttons (Open File, Pause/Resume, Retry, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.status == "Completed") {
                    Button(
                        onClick = {
                            FileOpener.openFile(context, item)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open File", fontWeight = FontWeight.Bold)
                    }
                } else if (item.status == "Failed") {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Download")
                    }
                    if (onRefreshLink != null) {
                        OutlinedButton(
                            onClick = { showRefreshLinkDialog = true }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh Link")
                        }
                    }
                } else {
                    Button(
                        onClick = onTogglePauseResume,
                        modifier = Modifier.weight(1f)
                    ) {
                        val icon = if (item.status == "Downloading" || item.status == "Probing") {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.status == "Downloading") "Pause" else "Resume")
                    }
                    if (item.status == "Paused" && onRefreshLink != null) {
                        OutlinedButton(
                            onClick = { showRefreshLinkDialog = true }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh Link")
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}
