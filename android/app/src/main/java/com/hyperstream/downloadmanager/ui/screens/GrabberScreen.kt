package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.browser.LinkParser
import com.hyperstream.downloadmanager.browser.VideoGrabberWebView
import com.hyperstream.downloadmanager.core.MdmRepository
import kotlinx.coroutines.launch

@Composable
fun GrabberScreen(
    repository: MdmRepository,
    onOpenFormatPicker: (url: String) -> Unit,
    onQueueUrls: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var videoPageUrl by remember { mutableStateOf("") }
    var batchPattern by remember { mutableStateOf("https://mirror.example.org/part[01-10].rar") }
    var showEmbeddedBrowser by remember { mutableStateOf(false) }
    var isUpdatingExtractors by remember { mutableStateOf(false) }
    var extractorStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Universal Media Grabber",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = {
                                if (!isUpdatingExtractors) {
                                    isUpdatingExtractors = true
                                    extractorStatus = null
                                    scope.launch {
                                        try {
                                            val ver = repository.updateExtractors()
                                            extractorStatus = "Extractors updated (v$ver)"
                                        } catch (e: Exception) {
                                            extractorStatus = "Update failed: ${e.message}"
                                        } finally {
                                            isUpdatingExtractors = false
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isUpdatingExtractors) {
                                CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.height(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("Update Extractors")
                        }
                    }

                    Text(
                        text = "Download from YouTube, TikTok (No Watermark), Instagram Reels, Facebook, X/Twitter, or any web video stream in full quality or as MP3.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    extractorStatus?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = videoPageUrl,
                        onValueChange = { videoPageUrl = it },
                        label = { Text("Paste Video or Social Media Link") },
                        placeholder = { Text("https://youtube.com/watch?v=... or tiktok.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clean = LinkParser.extractFirstUrl(videoPageUrl) ?: videoPageUrl.trim()
                                if (clean.isNotBlank()) {
                                    onOpenFormatPicker(clean)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Analyze & Choose Quality", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showEmbeddedBrowser = !showEmbeddedBrowser },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null)
                        }
                    }

                    if (showEmbeddedBrowser) {
                        Text(
                            text = "Built-In Browser Sniffer (Floats 'Download Video' when playing media):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        VideoGrabberWebView(
                            initialUrl = videoPageUrl.ifBlank { "https://m.youtube.com" },
                            onDownloadDetectedMedia = { url -> onOpenFormatPicker(url) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Batch Pattern Generator & Click'n'Load 2",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Expand numeric [01-10] or alphabetic ranges, or receive Click'n'Load 2 packages on 127.0.0.1:9666.",
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
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Queue Batch Links")
                    }
                }
            }
        }
    }
}
