package com.hyperstream.downloadmanager.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MdmRepository(
    private val dataDir: String = "/storage/emulated/0/Download/HyperStream/.data",
    private val downloadsDir: String = "/storage/emulated/0/Download/HyperStream",
    private val autoPoll: Boolean = true,
    initialState: MdmUiState = MdmUiState()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<MdmUiState> = _uiState.asStateFlow()

    /** URL shared into HyperStream from another app, waiting for the format picker to open. */
    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl.asStateFlow()

    fun setPendingSharedUrl(url: String?) {
        _pendingSharedUrl.value = url
    }

    init {
        if (MdmNative.safeInit(dataDir, downloadsDir)) {
            refreshFromNative()
            if (autoPoll) {
                pollScope.launch {
                    while (isActive) {
                        refreshFromNative()
                        delay(250L)
                    }
                }
            }
        }
    }

    fun applySnapshotJson(raw: String): Boolean {
        if (raw.isBlank() || raw == "{}") return false
        return runCatching {
            val parsed = json.decodeFromString<MdmUiState>(raw)
            val currentSettings = _uiState.value.settings
            val effectiveSettings = if (currentSettings.themeId.isNotBlank() && currentSettings.themeId != "classic") {
                parsed.settings.copy(
                    themeId = currentSettings.themeId,
                    darkMode = currentSettings.darkMode
                )
            } else {
                parsed.settings.copy(
                    themeId = "obsidian",
                    darkMode = "dark"
                )
            }
            _uiState.value = parsed.copy(settings = effectiveSettings)
            true
        }.getOrDefault(false)
    }

    fun refreshFromNative() {
        val raw = MdmNative.safeGetSnapshotJson() ?: return
        applySnapshotJson(raw)
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
        val platform = com.hyperstream.downloadmanager.browser.LinkParser.detectPlatformName(url)
        MetaPixelTracker.trackStartDownload(fileName, platform, category, clamped)
        val nameField = fileName?.takeIf { it.isNotBlank() }
            ?.let { ""","file_name":${quote(it)}""" } ?: ""
        val cmd = """{"op":"add_download","url":${quote(url)},"connections":$clamped,"category":${quote(category)},"queue":${quote(queue)},"start_immediately":${queue != "Overnight"}$nameField}"""
        MdmNative.safeExecuteCommand(cmd)
        refreshFromNative()
    }

    /** Probes a page/media URL for downloadable variants (video resolutions, no-watermark, MP3 bitrates). */
    suspend fun probeVideo(url: String): ProbeResultUi = withContext(Dispatchers.IO) {
        val raw = MdmNative.safeExecuteCommand("""{"op":"probe_video","url":${quote(url)}}""")
        parseProbeResultJson(raw)
    }

    /** Pulls the latest extractor rules (client versions, endpoints) so blocked extractors recover without an APK update. */
    suspend fun updateExtractors(): String = withContext(Dispatchers.IO) {
        val raw = MdmNative.safeExecuteCommand("""{"op":"update_extractors"}""")
        runCatching {
            json.parseToJsonElement(raw).jsonObject["version"]?.jsonPrimitive?.content
        }.getOrNull() ?: "built-in"
    }

    fun refreshLink(id: Long, newUrl: String) {
        val trimmed = newUrl.trim()
        if (trimmed.isNotBlank()) {
            MdmNative.safeExecuteCommand("""{"op":"refresh_link","id":$id,"new_url":${quote(trimmed)}}""")
            refreshFromNative()
        }
    }

    fun retryDownload(id: Long) {
        MdmNative.safeExecuteCommand("""{"op":"retry_download","id":$id}""")
        refreshFromNative()
    }

    fun cancelDownload(id: Long, deleteFile: Boolean = false) {
        MdmNative.safeExecuteCommand("""{"op":"cancel_download","id":$id,"delete_file":$deleteFile}""")
        _uiState.update { s -> s.copy(downloads = s.downloads.filterNot { it.id == id }) }
        refreshFromNative()
    }

    private fun quote(s: String): String = json.encodeToString(kotlinx.serialization.serializer(), s)

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

    fun toggleQueue(queueName: String, running: Boolean) {
        val op = if (running) "start_queue" else "stop_queue"
        MdmNative.safeExecuteCommand("""{"op":"$op","name":${json.encodeToString(kotlinx.serialization.serializer(), queueName)}}""")
        _uiState.update { state ->
            val nextQueues = state.queues.map { q ->
                if (q.name == queueName) q.copy(running = running) else q
            }
            state.copy(queues = nextQueues)
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
        /** Public folder where finished files land (Video/, Music/, Programs/ …). */
        const val DEFAULT_DOWNLOADS_DIR = "/storage/emulated/0/Download/HyperStream"

        @Volatile private var instance: MdmRepository? = null

        /** The one repository shared by MainActivity, LinkCatcherActivity and HyperStreamDownloadService. */
        fun getOrCreate(dataDir: String, downloadsDir: String = DEFAULT_DOWNLOADS_DIR): MdmRepository =
            instance ?: synchronized(this) {
                instance ?: MdmRepository(dataDir = dataDir, downloadsDir = downloadsDir).also { instance = it }
            }

        /** Same instance, keyed on the app's private data folder (`files/hyperstream_data`). */
        fun forContext(context: android.content.Context): MdmRepository =
            getOrCreate(context.filesDir.resolve("hyperstream_data").absolutePath)

        /** The shared repository if the app already created it; otherwise a default-path instance. */
        val shared: MdmRepository
            get() = instance ?: synchronized(this) {
                instance ?: MdmRepository().also { instance = it }
            }

        private val probeJson = Json { ignoreUnknownKeys = true }

        fun parseProbeResultJson(raw: String?): ProbeResultUi {
            if (raw.isNullOrBlank()) return ProbeResultUi(ok = false, error = "Empty response from engine")
            return runCatching {
                val parsed = probeJson.decodeFromString<ProbeResultUi>(raw)
                if (!parsed.ok && parsed.error == null) {
                    val err = probeJson.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.content
                    parsed.copy(error = err ?: "Could not read this link")
                } else parsed
            }.getOrElse { ProbeResultUi(ok = false, error = "Could not read this link") }
        }

        /** Demo data for @Preview functions only — never used as runtime state. */
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
