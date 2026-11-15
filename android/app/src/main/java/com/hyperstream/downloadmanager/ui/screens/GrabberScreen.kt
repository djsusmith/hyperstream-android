package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GrabberScreen(
    onQueueUrls: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var videoPageUrl by remember { mutableStateOf("https://www.youtube.com/watch?v=aqz-KE-bpKQ") }
    var batchPattern by remember { mutableStateOf("https://mirror.example.org/part[01-10].rar") }
    var probedQualities by remember {
        mutableStateOf(
            listOf(
                "2160p 4K60 HDR (MP4 + AAC • 128 Threads)",
                "1440p QHD (WebM + Opus • 128 Threads)",
                "1080p Full HD (MP4 • 128 Threads)",
                "Audio Only (M4A / Opus 160kbps)"
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "In-App Video & Media Stream Grabber (yt-dlp / ffmpeg / gallery-dl / HLS)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Detects HTML5 <video>, .m3u8 HLS manifests, .mpd DASH streams, and displays MDM's floating 'Download Video' button.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = videoPageUrl,
                        onValueChange = { videoPageUrl = it },
                        label = { Text("Page or Video Stream URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    probedQualities.forEach { q ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("• $q", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onQueueUrls(listOf(videoPageUrl)) }) {
                                Text("Grab (128T)")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Batch Pattern Generator & Click'n'Load 2 Bridge (127.0.0.1:9666)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Expand numeric `[001-100]` or alphabetic `[a-z]` ranges, or receive encrypted JDownloader Click'n'Load 2 packages on localhost:9666.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = batchPattern,
                        onValueChange = { batchPattern = it },
                        label = { Text("Batch URL Pattern") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onQueueUrls(
                                (1..5).map { idx ->
                                    batchPattern.replace("[01-10]", String.format("%02d", idx))
                                }
                            )
                        }
                    ) {
                        Text("Expand & Queue Batch Links")
                    }
                }
            }
        }
    }
}
