package com.hyperstream.downloadmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.core.ProbeResultUi
import com.hyperstream.downloadmanager.core.ProbedStreamUi
import com.hyperstream.downloadmanager.ui.screens.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFormatPickerSheet(
    url: String,
    repository: MdmRepository,
    onDismiss: () -> Unit,
    onDownloadSelected: (streamUrl: String, fileName: String, category: String, connections: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var probeResult by remember { mutableStateOf<ProbeResultUi?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Video, 1: Audio / MP3
    var selectedStream by remember { mutableStateOf<ProbedStreamUi?>(null) }
    var selectedConnections by remember { mutableIntStateOf(128) }

    fun runProbe() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val res = repository.probeVideo(url)
                probeResult = res
                if (res.ok && res.streams.isNotEmpty()) {
                    selectedStream = res.streams.firstOrNull { it.formatKind == "video" }
                        ?: res.streams.first()
                } else {
                    errorMessage = res.error ?: "Failed to extract streams for this link"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to probe URL"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(url) {
        runProbe()
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val platformName = probeResult?.platform ?: "Media"
                    val platformColor = when (platformName.lowercase()) {
                        "youtube" -> Color(0xFFFF0000)
                        "tiktok" -> Color(0xFF00F2FE)
                        "instagram" -> Color(0xFFE1306C)
                        "facebook" -> Color(0xFF1877F2)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(platformColor)
                    )
                    Text(
                        text = "$platformName Grabber",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val hasNoWatermark = probeResult?.watermarkFree == true || probeResult?.streams?.any { it.watermarkFree } == true
                    if (hasNoWatermark) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "No Watermark",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Analyzing link and resolving available streams...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Extraction Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { runProbe() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry")
                            }
                            Button(
                                onClick = {
                                    // Fallback: direct download
                                    onDownloadSelected(url, "download.bin", "General", 128)
                                    onDismiss()
                                }
                            ) {
                                Text("Download As Direct File")
                            }
                        }
                    }
                }
            } else {
                val res = probeResult
                if (res != null) {
                    // Video Title & Author
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = res.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = res.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Format Kind Tabs (Video vs Audio / MP3)
                    val videoStreams = res.streams.filter { it.formatKind == "video" }
                    val audioStreams = res.streams.filter { it.formatKind == "mp3" || it.category == "Music" }

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                selectedStream = videoStreams.firstOrNull() ?: res.streams.firstOrNull()
                            },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Video (${videoStreams.size})")
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                selectedStream = audioStreams.firstOrNull() ?: res.streams.firstOrNull()
                            },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Audio / MP3 (${audioStreams.size})")
                                }
                            }
                        )
                    }

                    // Stream List with clean borders, resolution, codec details, watermark pill & file size badge
                    val activeStreams = if (selectedTab == 0) videoStreams else audioStreams
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeStreams) { stream ->
                            val isSelected = selectedStream?.url == stream.url && selectedStream?.label == stream.label
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedStream = stream }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val displayLabel = when {
                                            stream.label.contains("AV1", ignoreCase = true) || stream.label.contains("H.264", ignoreCase = true) || stream.label.contains("Fast", ignoreCase = true) -> stream.label
                                            stream.label.startsWith("2160p", ignoreCase = true) || stream.label.startsWith("1440p", ignoreCase = true) -> "${stream.label} (AV1 HDR)"
                                            stream.label.startsWith("1080p", ignoreCase = true) -> "${stream.label} (H.264)"
                                            stream.label.startsWith("360p", ignoreCase = true) -> "${stream.label} (Video + Audio - Fast)"
                                            else -> stream.label
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = displayLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (stream.watermarkFree && stream.formatKind == "video") {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF00E676).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "No Watermark",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF00E676),
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        val sizeStr = if (stream.sizeBytes > 0) formatBytes(stream.sizeBytes) else "Dynamic Stream"
                                        val detailText = if (stream.formatKind == "mp3" && stream.bitrateKbps > 0) {
                                            "${stream.ext.uppercase()} • ${stream.bitrateKbps} kbps • $sizeStr"
                                        } else {
                                            "${stream.ext.uppercase()} • $sizeStr"
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = detailText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Engine Acceleration selector row with connection pills: [16T], [32T], [64T], [128T]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Engine Acceleration:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(16, 32, 64, 128).forEach { conns ->
                                val active = selectedConnections == conns
                                FilterChip(
                                    selected = active,
                                    onClick = { selectedConnections = conns },
                                    label = { Text("${conns}T", fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = BorderStroke(
                                        width = if (active) 1.5.dp else 1.dp,
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }

                    // Primary Action Button: Download icon + Start Download (<format> • <N> Threads)
                    Button(
                        onClick = {
                            val target = selectedStream ?: activeStreams.firstOrNull()
                            if (target != null) {
                                onDownloadSelected(
                                    target.url,
                                    target.fileName,
                                    target.category,
                                    selectedConnections
                                )
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val optText = when {
                            !selectedStream?.quality.isNullOrBlank() -> selectedStream?.quality
                            selectedStream?.label?.contains("360p", ignoreCase = true) == true -> "360p"
                            selectedStream?.label?.contains("720p", ignoreCase = true) == true -> "720p"
                            selectedStream?.label?.contains("1080p", ignoreCase = true) == true -> "1080p"
                            selectedStream?.label?.contains("1440p", ignoreCase = true) == true -> "1440p"
                            selectedStream?.label?.contains("2160p", ignoreCase = true) == true -> "2160p"
                            selectedStream?.formatKind == "mp3" -> "MP3"
                            else -> selectedStream?.ext?.uppercase() ?: "MP4"
                        }
                        Text("Start Download ($optText • ${selectedConnections} Threads)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
