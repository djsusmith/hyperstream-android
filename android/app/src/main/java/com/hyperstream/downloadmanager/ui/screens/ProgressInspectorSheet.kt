package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.ui.components.SegmentMapBar

@Composable
fun ProgressInspectorSheet(
    item: DownloadItemUi,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "128-Thread Segment Inspector — ${item.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "URL: ${item.url}\nSave Path: ${item.savePath}\nServer Location: ${countryFlagEmoji(item.countryCode)} ${item.countryName ?: "Unknown"} • Resumable: ${if (item.resumable) "Yes (206 Partial Content)" else "No"}",
                style = MaterialTheme.typography.bodySmall
            )
            SegmentMapBar(
                segments = item.segments,
                maxConnections = item.maxConnections,
                activeConnections = item.activeConnections
            )
            item.checksumExpected?.let { hash ->
                Text(
                    text = "Integrity Checksum: $hash (${if (item.checksumVerified == true) "Verified OK" else "Pending completion"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            HorizontalDivider()
            Text(
                text = "Active Worker Connections (${item.activeConnections} / ${item.maxConnections})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(item.segments.take(32), key = { it.index }) { seg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Conn #${seg.index + 1} [${formatBytes(seg.start)} → ${formatBytes(seg.end)}]",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (seg.active) formatSpeed(seg.speedBps) else "Completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
