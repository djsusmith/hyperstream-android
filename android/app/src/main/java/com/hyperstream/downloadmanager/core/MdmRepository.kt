package com.hyperstream.downloadmanager.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

class MdmRepository(
    private val dataDir: String = "/storage/emulated/0/Download/HyperStream/.data",
    private val downloadsDir: String = "/storage/emulated/0/Download/HyperStream"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(sampleInitialState())
    val uiState: StateFlow<MdmUiState> = _uiState.asStateFlow()

    init {
        if (MdmNative.safeInit(dataDir, downloadsDir)) {
            refreshFromNative()
        }
    }

    fun refreshFromNative() {
        val raw = MdmNative.safeGetSnapshotJson() ?: return
        if (raw.isBlank() || raw == "{}") return
        runCatching {
            val parsed = json.decodeFromString<MdmUiState>(raw)
            if (parsed.downloads.isNotEmpty() || parsed.torrents.isNotEmpty()) {
                _uiState.value = parsed
            }
        }
    }

    fun addDownload(
        url: String,
        fileName: String? = null,
        category: String = "General",
        connections: Int = 128,
        queue: String = "Main",
        checksumExpected: String? = null
    ) {
        val clamped = connections.coerceIn(1, 128)
        val cmd = """{"op":"add_download","url":${json.encodeToString(kotlinx.serialization.serializer(), url)},"connections":$clamped,"category":${json.encodeToString(kotlinx.serialization.serializer(), category)},"queue":${json.encodeToString(kotlinx.serialization.serializer(), queue)}}"""
        MdmNative.safeExecuteCommand(cmd)
        val inferredName = fileName?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
        val newId = (_uiState.value.downloads.maxOfOrNull { it.id } ?: 0L) + 1L
        val segments = (0 until clamped).map { idx ->
            SegmentUi(index = idx, start = idx * 1024L, pos = idx * 1024L, end = (idx + 1) * 1024L, active = true)
        }
        val item = DownloadItemUi(
            id = newId,
            name = inferredName,
            url = url,
            category = category,
            queue = queue,
            savePath = "$downloadsDir/$category/$inferredName",
            totalBytes = 0L,
            downloadedBytes = 0L,
            speedBps = 0L,
            status = "Downloading",
            resumable = true,
            activeConnections = clamped,
            maxConnections = clamped,
            checksumExpected = checksumExpected,
            segments = segments
        )
        _uiState.update { state ->
            val updated = listOf(item) + state.downloads
            state.copy(
                downloads = updated,
                wakeLockActive = true,
                activeThreadsTotal = updated.filter { it.status == "Downloading" }.sumOf { it.activeConnections }
            )
        }
        refreshFromNative()
    }

    fun togglePauseResume(id: Long) {
        _uiState.update { state ->
            val updated = state.downloads.map { d ->
                if (d.id != id) d
                else if (d.status == "Downloading") {
                    MdmNative.safeExecuteCommand("""{"op":"pause_download","id":$id}""")
                    d.copy(status = "Paused", speedBps = 0L, activeConnections = 0)
                } else {
                    MdmNative.safeExecuteCommand("""{"op":"resume_download","id":$id}""")
                    d.copy(status = "Downloading", activeConnections = d.maxConnections)
                }
            }
            val anyActive = updated.any { it.status == "Downloading" || it.status == "Probing" }
            state.copy(
                downloads = updated,
                wakeLockActive = anyActive,
                activeThreadsTotal = updated.filter { it.status == "Downloading" }.sumOf { it.activeConnections }
            )
        }
    }

    fun addTorrent(source: String, sequential: Boolean = true) {
        MdmNative.safeExecuteCommand("""{"op":"add_torrent","source":${json.encodeToString(kotlinx.serialization.serializer(), source)},"sequential":$sequential}""")
        val newId = (_uiState.value.torrents.maxOfOrNull { it.id } ?: 0L) + 1L
        val display = source.substringAfter("dn=", "").substringBefore("&").ifBlank { "Torrent #$newId" }
        val torrent = TorrentItemUi(
            id = newId,
            name = display,
            infoHash = "btih-${newId.toString(16).padStart(8, '0')}",
            magnetOrSource = source,
            status = "Downloading",
            sequential = sequential,
            peersConnected = 18,
            seedsConnected = 7,
            piecesBitmap = List(16) { 0xAA }
        )
        _uiState.update { it.copy(torrents = listOf(torrent) + it.torrents, wakeLockActive = true) }
    }

    fun updateSettings(transform: (SettingsUi) -> SettingsUi) {
        _uiState.update { state ->
            val next = transform(state.settings).let {
                it.copy(maxConnections = it.maxConnections.coerceIn(1, 128))
            }
            MdmNative.safeExecuteCommand(
                """{"op":"update_settings","max_connections":${next.maxConnections},"speed_limit_kbps":${next.speedLimitKbps},"shizuku_enabled":${next.shizukuEnabled},"update_channel":"${next.updateChannel}","theme_id":"${next.themeId}","locale_code":"${next.localeCode}"}"""
            )
            state.copy(settings = next)
        }
    }

    companion object {
        fun sampleInitialState(): MdmUiState {
            val segments128 = (0 until 128).map { idx ->
                val chunk = 32L * 1024L * 1024L
                val start = idx * chunk
                val end = start + chunk
                val done = if (idx % 3 == 0) chunk else (chunk * 7) / 10
                SegmentUi(
                    index = idx,
                    start = start,
                    pos = start + done,
                    end = end,
                    active = done < chunk,
                    speedBps = if (done < chunk) 1_450_000L else 0L
                )
            }
            return MdmUiState(
                totalDownSpeedBps = 148_480_000L,
                totalUpSpeedBps = 4_200_000L,
                activeThreadsTotal = 128,
                wakeLockActive = true,
                freeDiskBytes = 92L * 1024L * 1024L * 1024L,
                downloads = listOf(
                    DownloadItemUi(
                        id = 1L,
                        name = "ubuntu-24.04.2-desktop-arm64.iso",
                        url = "https://releases.ubuntu.com/24.04/ubuntu-24.04.2-desktop-arm64.iso",
                        category = "Programs",
                        queue = "Main",
                        savePath = "/storage/emulated/0/Download/HyperStream/Programs/ubuntu-24.04.2-desktop-arm64.iso",
                        totalBytes = 4_294_967_296L,
                        downloadedBytes = 3_312_000_000L,
                        speedBps = 142_600_000L,
                        etaSeconds = 7L,
                        status = "Downloading",
                        resumable = true,
                        activeConnections = 128,
                        maxConnections = 128,
                        countryCode = "DE",
                        countryName = "Germany (Frankfurt CDN)",
                        checksumExpected = "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        checksumVerified = null,
                        segments = segments128
                    ),
                    DownloadItemUi(
                        id = 2L,
                        name = "Blender-4.3-Android-Assets.7z",
                        url = "https://download.blender.org/release/Blender4.3/Blender-4.3-Android-Assets.7z",
                        category = "Compressed",
                        queue = "Main",
                        savePath = "/storage/emulated/0/Download/HyperStream/Compressed/Blender-4.3-Android-Assets.7z",
                        totalBytes = 890_000_000L,
                        downloadedBytes = 890_000_000L,
                        speedBps = 0L,
                        etaSeconds = 0L,
                        status = "Completed",
                        resumable = true,
                        activeConnections = 0,
                        maxConnections = 64,
                        countryCode = "NL",
                        countryName = "Netherlands",
                        checksumExpected = "sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                        checksumVerified = true,
                        segments = emptyList()
                    ),
                    DownloadItemUi(
                        id = 3L,
                        name = "S25Ultra-Camera-RAW-Sample-8K60.mp4",
                        url = "https://cdn.hyperstream.dev/media/S25Ultra-Camera-RAW-Sample-8K60.mp4",
                        category = "Video",
                        queue = "Overnight",
                        savePath = "/storage/emulated/0/Download/HyperStream/Video/S25Ultra-Camera-RAW-Sample-8K60.mp4",
                        totalBytes = 2_150_000_000L,
                        downloadedBytes = 640_000_000L,
                        speedBps = 0L,
                        etaSeconds = null,
                        status = "Paused",
                        resumable = true,
                        activeConnections = 0,
                        maxConnections = 128,
                        countryCode = "US",
                        countryName = "United States",
                        segments = emptyList()
                    )
                ),
                torrents = listOf(
                    TorrentItemUi(
                        id = 101L,
                        name = "Debian-12.9.0-arm64-netinst.iso",
                        infoHash = "a1b2c3d4e5f60718293a4b5c6d7e8f9011223344",
                        magnetOrSource = "magnet:?xt=urn:btih:a1b2c3d4e5f60718293a4b5c6d7e8f9011223344&dn=Debian-12.9.0-arm64-netinst.iso",
                        totalBytes = 681_574_400L,
                        downloadedBytes = 512_000_000L,
                        uploadedBytes = 198_000_000L,
                        downSpeedBps = 5_880_000L,
                        upSpeedBps = 4_200_000L,
                        ratio = 0.38f,
                        status = "Downloading",
                        sequential = true,
                        peersConnected = 42,
                        seedsConnected = 19,
                        files = listOf(
                            TorrentFileUi(0, "debian-12.9.0-arm64-netinst.iso", 680_000_000L, 511_000_000L, true),
                            TorrentFileUi(1, "SHA256SUMS", 1_574_400L, 1_000_000L, true)
                        ),
                        peers = listOf(
                            TorrentPeerUi("185.125.190.36:51413", "rqbit 8.1 / MDM", 2_400_000L, 600_000L, 100f),
                            TorrentPeerUi("91.189.91.48:6881", "qBittorrent 5.0", 3_480_000L, 1_100_000L, 84.5f)
                        ),
                        trackers = listOf(
                            "http://bttracker.debian.org:6969/announce",
                            "udp://tracker.opentrackr.org:1337/announce"
                        ),
                        piecesBitmap = List(32) { idx -> if (idx < 24) 0xFF else 0x55 }
                    )
                ),
                queues = listOf(
                    QueueUi(
                        name = "Main",
                        running = true,
                        maxConcurrent = 4,
                        startTime = null,
                        stopTime = null,
                        daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                        stopWhenDone = false,
                        itemIds = listOf(1L, 2L)
                    ),
                    QueueUi(
                        name = "Overnight",
                        running = false,
                        maxConcurrent = 6,
                        startTime = "01:00",
                        stopTime = "07:00",
                        daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                        stopWhenDone = true,
                        itemIds = listOf(3L)
                    )
                ),
                rssFeeds = listOf(
                    RssFeedRuleUi(
                        id = 1L,
                        title = "Linux ARM64 ISO Releases",
                        feedUrl = "https://distrowatch.com/news/torrents.xml",
                        includeRegex = ".*(arm64|aarch64).*\\.iso",
                        excludeRegex = ".*beta.*",
                        category = "Programs",
                        enabled = true,
                        lastChecked = "5m ago"
                    )
                ),
                settings = SettingsUi(
                    maxConnections = 128,
                    minSplitKb = 64,
                    speedLimitKbps = 0L,
                    shizukuEnabled = true,
                    shizukuAutoInstallApk = true,
                    shizukuDozeWhitelist = true,
                    updateChannel = "stable"
                )
            )
        }
    }
}
