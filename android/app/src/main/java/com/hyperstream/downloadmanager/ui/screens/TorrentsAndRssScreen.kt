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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.MdmUiState

@Composable
fun TorrentsAndRssScreen(
    state: MdmUiState,
    onAddMagnet: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var magnetInput by remember { mutableStateOf("") }
    var sequential by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "BitTorrent Engine (DHT • PEX • LSD • UPnP/NAT-PMP • Sequential)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = magnetInput,
                        onValueChange = { magnetInput = it },
                        label = { Text("Magnet URI or .torrent URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = sequential, onCheckedChange = { sequential = it })
                            Text("Download in sequential order (streamable)")
                        }
                        Button(
                            onClick = {
                                if (magnetInput.isNotBlank()) {
                                    onAddMagnet(magnetInput.trim(), sequential)
                                    magnetInput = ""
                                }
                            }
                        ) {
                            Text("Add Torrent")
                        }
                    }
                }
            }
        }

        items(state.torrents, key = { it.id }) { t ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "InfoHash: ${t.infoHash} • ${t.status} • Seeds: ${t.seedsConnected} • Peers: ${t.peersConnected} • Ratio: ${t.ratio}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val prog = if (t.totalBytes > 0L) t.downloadedBytes.toFloat() / t.totalBytes.toFloat() else 0f
                    LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Down: ${formatSpeed(t.downSpeedBps)} • Up: ${formatSpeed(t.upSpeedBps)} • Sequential: ${if (t.sequential) "ON" else "OFF"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (t.files.isNotEmpty()) {
                        Text(
                            text = "Files (${t.files.size}): " + t.files.joinToString { "${it.name} (${formatBytes(it.sizeBytes)})" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (t.peers.isNotEmpty()) {
                        Text(
                            text = "Active Swarm Peers: " + t.peers.joinToString { "${it.addr} [${it.client}]" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "RSS Auto-Downloader Feeds (${state.rssFeeds.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    state.rssFeeds.forEach { feed ->
                        Text(
                            text = "• ${feed.title} (${feed.feedUrl}) — Match: `${feed.includeRegex}` • Checked: ${feed.lastChecked ?: "Never"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
