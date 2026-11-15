package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.MdmUiState

@Composable
fun QueuesAndSchedulerScreen(
    state: MdmUiState,
    onToggleQueue: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Download Queues & Overnight Scheduler",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "While any queue has active or queued transfers, HyperStreamDownloadService holds PARTIAL_WAKE_LOCK + WIFI_MODE_FULL_HIGH_PERF so Android never suspends multi-gigabyte overnight transfers.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(state.queues, key = { it.name }) { q ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${q.name} Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Max Concurrent Files: ${q.maxConcurrent} • Items: ${q.itemIds.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val windowText = if (q.startTime != null && q.stopTime != null) {
                            "Scheduled Window: ${q.startTime} → ${q.stopTime}"
                        } else {
                            "Immediate Execution (24/7)"
                        }
                        Text(windowText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Switch(
                        checked = q.running,
                        onCheckedChange = { next -> onToggleQueue(q.name, next) }
                    )
                }
            }
        }
    }
}
