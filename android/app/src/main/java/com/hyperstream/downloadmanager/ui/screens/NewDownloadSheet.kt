package com.hyperstream.downloadmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperstream.downloadmanager.browser.LinkKind
import com.hyperstream.downloadmanager.browser.LinkParser
import com.hyperstream.downloadmanager.core.MdmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

data class ProbedLinkMetadata(
    val resolvedUrl: String,
    val fileName: String,
    val extension: String,
    val category: String,
    val sizeBytes: Long,
    val supportsResume: Boolean,
    val isSocialVideo: Boolean = false,
    val socialQualityLabel: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDownloadSheet(
    defaultConnections: Int,
    defaultCategory: String,
    initialRequest: com.hyperstream.downloadmanager.browser.ExternalDownloadRequest? = null,
    onDismiss: () -> Unit,
    onStartDownload: (url: String, connections: Int, category: String, checksum: String?) -> Unit,
    onStartDownloadWithFileName: ((url: String, fileName: String?, connections: Int, category: String) -> Unit)? = null,
    onStartDownloadWithHeaders: ((url: String, fileName: String?, connections: Int, category: String, userAgent: String?, referer: String?, cookie: String?) -> Unit)? = null,
    onOpenFormatPicker: ((url: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    var urlInput by remember(initialRequest?.url) { mutableStateOf(initialRequest?.url.orEmpty()) }
    var autoFilledFromClipboard by remember { mutableStateOf(initialRequest != null) }
    var checksumInput by remember { mutableStateOf("") }
    var showChecksum by remember { mutableStateOf(false) }
    val connectionChoices = listOf(16, 32, 64, 128)
    var selectedConnections by remember {
        mutableIntStateOf(connectionChoices.minByOrNull { kotlin.math.abs(it - defaultConnections) } ?: 128)
    }

    var isProbing by remember { mutableStateOf(false) }
    var probedMeta by remember { mutableStateOf<ProbedLinkMetadata?>(null) }

    // 1. Auto-detect valid URL in clipboard as soon as user taps '+'
    LaunchedEffect(Unit) {
        val clipText = clipboard.getText()?.text?.trim()
        val parsed = LinkParser.extractDownloadableTarget(clipText)
        if (parsed != null && urlInput.isBlank()) {
            urlInput = parsed.uri
            autoFilledFromClipboard = true
        }
    }

    val trimmedUrl = urlInput.trim()
    val validTarget = remember(trimmedUrl) {
        LinkParser.extractDownloadableTarget(trimmedUrl)
    }

    // 2. Automatically probe the server (or social video extractor) when a valid URL is present
    LaunchedEffect(trimmedUrl) {
        probedMeta = null
        val target = validTarget ?: return@LaunchedEffect
        if (!target.uri.startsWith("http://", ignoreCase = true) &&
            !target.uri.startsWith("https://", ignoreCase = true)
        ) {
            return@LaunchedEffect
        }

        if (!autoFilledFromClipboard) {
            delay(250) // debounce while typing manually
        }
        autoFilledFromClipboard = false

        isProbing = true
        probedMeta = withContext(Dispatchers.IO) {
            probeRemoteUrl(target.uri, target.kind, defaultCategory)
        }
        isProbing = false
    }

    val fallbackName = remember(trimmedUrl, initialRequest?.fileName) {
        initialRequest?.fileName?.takeIf { it.isNotBlank() }
            ?: run {
                val clean = trimmedUrl.substringBefore('?').substringBefore('#')
                clean.substringAfterLast('/').takeIf { it.isNotBlank() && it.contains('.') } ?: "download_file"
            }
    }
    val displayMeta = probedMeta
    val activeFileName = displayMeta?.fileName ?: fallbackName
    val activeExt = displayMeta?.extension ?: activeFileName.substringAfterLast('.', "FILE").uppercase().take(4)
    val activeCategory = displayMeta?.category ?: remember(activeExt, defaultCategory) {
        categoryForExt(activeExt, defaultCategory)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "New download",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Paste a link — HyperStream checks the file size and type",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // File link + Paste pill
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "File link",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://example.com/file.zip") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    trailingIcon = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable {
                                    clipboard.getText()?.text?.let { clip ->
                                        val extracted = LinkParser.extractFirstUrl(clip) ?: clip.trim()
                                        autoFilledFromClipboard = true
                                        urlInput = extracted
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Paste",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Live file preview card (auto-fetched when valid URL is present)
            if (validTarget != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isProbing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = activeExt,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isProbing) "Fetching file details..." else activeFileName,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sizeStr = when {
                                    isProbing -> "Checking file size & server headers..."
                                    displayMeta != null && displayMeta.sizeBytes > 0 ->
                                        "${formatBytes(displayMeta.sizeBytes)} · $activeCategory"
                                    displayMeta?.socialQualityLabel != null ->
                                        "${displayMeta.socialQualityLabel} · $activeCategory"
                                    else ->
                                        "${LinkParser.detectPlatformName(trimmedUrl)} · $activeCategory"
                                }
                                Text(
                                    text = sizeStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (displayMeta?.isSocialVideo == true && onOpenFormatPicker != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable {
                                        onDismiss()
                                        onOpenFormatPicker(trimmedUrl)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = "Formats",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Formats",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val resumable = displayMeta?.supportsResume ?: true
                            Icon(
                                imageVector = if (resumable) Icons.Default.Check else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (resumable) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    isProbing -> "Verifying server resume support..."
                                    resumable -> "Server supports resuming"
                                    else -> "Single-stream link (resume not advertised by server)"
                                },
                                color = if (resumable) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Connections segmented selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Connections",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        connectionChoices.forEach { count ->
                            val selected = selectedConnections == count
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedConnections = count }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save to folder card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showChecksum = !showChecksum }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save to",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Download/HyperStream/$activeCategory",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = if (showChecksum) "Hide hash" else "+ Checksum",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (showChecksum) {
                OutlinedTextField(
                    value = checksumInput,
                    onValueChange = { checksumInput = it },
                    label = { Text("Expected Checksum (sha256:..., md5:..., crc32:...)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Primary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val finalUrl = displayMeta?.resolvedUrl ?: validTarget?.uri ?: trimmedUrl
                        val finalName = initialRequest?.fileName?.takeIf { it.isNotBlank() }
                            ?: displayMeta?.fileName?.takeIf { it.isNotBlank() }
                        if (finalUrl.isNotBlank()) {
                            if (onStartDownloadWithHeaders != null) {
                                onStartDownloadWithHeaders(
                                    finalUrl,
                                    finalName,
                                    selectedConnections,
                                    activeCategory,
                                    initialRequest?.userAgent,
                                    initialRequest?.referer,
                                    initialRequest?.cookie
                                )
                            } else if (onStartDownloadWithFileName != null) {
                                onStartDownloadWithFileName(
                                    finalUrl,
                                    finalName,
                                    selectedConnections,
                                    activeCategory
                                )
                            } else {
                                onStartDownload(
                                    finalUrl,
                                    selectedConnections,
                                    activeCategory,
                                    checksumInput.takeIf { it.isNotBlank() }
                                )
                            }
                            onDismiss()
                        }
                    },
                    enabled = validTarget != null && !isProbing,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download now", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(52.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private suspend fun probeRemoteUrl(
    url: String,
    kind: LinkKind,
    defaultCategory: String
): ProbedLinkMetadata? {
    // 1. If it's a social video link (YouTube, TikTok, Instagram, Facebook, etc.), use the media extractor probe
    if (kind == LinkKind.VIDEO_STREAM_PAGE) {
        val probe = runCatching { MdmRepository.shared.probeVideo(url) }.getOrNull()
        val best = probe?.streams?.firstOrNull()
        if (probe != null && probe.ok && best != null) {
            val safeTitle = probe.title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80).ifBlank { "video" }
            val ext = best.ext.lowercase().ifBlank { "mp4" }
            val fName = best.fileName.ifBlank {
                if (safeTitle.endsWith(".$ext", ignoreCase = true)) safeTitle else "$safeTitle.$ext"
            }
            return ProbedLinkMetadata(
                resolvedUrl = best.url.ifBlank { url },
                fileName = fName,
                extension = ext.uppercase().take(4),
                category = best.category.ifBlank { "Video" },
                sizeBytes = best.sizeBytes,
                supportsResume = true,
                isSocialVideo = true,
                socialQualityLabel = "${probe.platform} • ${best.label.ifBlank { best.quality }}"
            )
        }
    }

    // 2. Direct HTTP/HTTPS probe (Range GET bytes=0-0 checks both total size and byte-range resumability in 1 round-trip)
    return runCatching {
        var currentUrl = url
        var redirects = 0
        var conn: HttpURLConnection
        while (true) {
            conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Range", "bytes=0-0")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 6) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                currentUrl = URL(URL(currentUrl), loc).toString()
                redirects++
                continue
            }
            break
        }

        val code = conn.responseCode
        val contentDisp = conn.getHeaderField("Content-Disposition")
        val contentType = conn.getHeaderField("Content-Type")?.substringBefore(';')?.trim()?.lowercase() ?: ""
        val acceptRanges = conn.getHeaderField("Accept-Ranges")?.lowercase() ?: ""
        val contentRange = conn.getHeaderField("Content-Range") ?: ""

        // Parse total size from Content-Range: bytes 0-0/123456 or Content-Length
        val totalSize = when {
            contentRange.contains('/') -> contentRange.substringAfterLast('/').trim().toLongOrNull() ?: -1L
            else -> conn.contentLengthLong
        }.coerceAtLeast(0L)

        val supportsResume = code == 206 || acceptRanges.contains("bytes")
        conn.disconnect()

        val nameFromDisp = parseContentDispositionFileName(contentDisp)
        val nameFromPath = runCatching {
            URLDecoder.decode(
                currentUrl.substringBefore('?').substringBefore('#').substringAfterLast('/'),
                "UTF-8"
            )
        }.getOrDefault("")

        val mimeExt = extensionFromMime(contentType)
        val rawName = when {
            !nameFromDisp.isNullOrBlank() -> nameFromDisp
            nameFromPath.isNotBlank() && nameFromPath.contains('.') -> nameFromPath
            nameFromPath.isNotBlank() && mimeExt != null -> "$nameFromPath.$mimeExt"
            mimeExt != null -> "download.$mimeExt"
            else -> "download_file"
        }

        val ext = rawName.substringAfterLast('.', mimeExt ?: "FILE").uppercase().take(4)
        val cat = categoryForExt(ext, defaultCategory)

        ProbedLinkMetadata(
            resolvedUrl = currentUrl,
            fileName = rawName,
            extension = ext,
            category = cat,
            sizeBytes = totalSize,
            supportsResume = supportsResume
        )
    }.getOrNull()
}

private fun parseContentDispositionFileName(header: String?): String? {
    if (header.isNullOrBlank()) return null
    val utf8Match = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(header)
    if (utf8Match != null) {
        return runCatching { URLDecoder.decode(utf8Match.groupValues[1].trim('"', ' '), "UTF-8") }.getOrNull()
    }
    val stdMatch = Regex("""filename=["']?([^"';]+)["']?""", RegexOption.IGNORE_CASE).find(header)
    return stdMatch?.groupValues?.getOrNull(1)?.trim()
}

private fun extensionFromMime(mime: String): String? = when (mime) {
    "application/vnd.android.package-archive" -> "apk"
    "application/zip", "application/x-zip-compressed" -> "zip"
    "application/x-rar-compressed", "application/vnd.rar" -> "rar"
    "application/x-7z-compressed" -> "7z"
    "application/gzip", "application/x-gzip" -> "gz"
    "application/x-iso9660-image", "application/octet-stream-iso" -> "iso"
    "application/pdf" -> "pdf"
    "video/mp4" -> "mp4"
    "video/x-matroska" -> "mkv"
    "video/webm" -> "webm"
    "audio/mpeg", "audio/mp3" -> "mp3"
    "audio/flac" -> "flac"
    "audio/mp4", "audio/x-m4a" -> "m4a"
    else -> null
}

private fun categoryForExt(ext: String, defaultCategory: String): String = when {
    ext in listOf("MP4", "MKV", "WEBM", "MOV", "AVI") -> "Video"
    ext in listOf("MP3", "FLAC", "AAC", "WAV", "OGG", "M4A") -> "Music"
    ext in listOf("ZIP", "RAR", "7Z", "TAR", "GZ", "ISO", "XZ") -> "Compressed"
    ext in listOf("PDF", "DOC", "DOCX", "EPUB", "TXT") -> "Documents"
    ext in listOf("APK", "XAPK", "EXE", "DEB", "MSI") -> "Programs"
    defaultCategory !in listOf("All", "Unfinished", "Finished", "Downloading", "Completed", "Queued") -> defaultCategory
    else -> "General"
}
