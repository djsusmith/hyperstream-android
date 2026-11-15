package com.hyperstream.downloadmanager.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SegmentUi(
    val index: Int = 0,
    val start: Long = 0L,
    val pos: Long = 0L,
    val end: Long = 0L,
    val active: Boolean = false,
    @SerialName("speed_bps") val speedBps: Long = 0L
)

@Serializable
data class DownloadItemUi(
    val id: Long = 0L,
    val name: String = "",
    val url: String = "",
    val category: String = "General",
    val queue: String = "Main",
    @SerialName("save_path") val savePath: String = "",
    @SerialName("total_bytes") val totalBytes: Long = 0L,
    @SerialName("downloaded_bytes") val downloadedBytes: Long = 0L,
    @SerialName("speed_bps") val speedBps: Long = 0L,
    @SerialName("eta_seconds") val etaSeconds: Long? = null,
    val status: String = "Queued",
    val resumable: Boolean = true,
    @SerialName("active_connections") val activeConnections: Int = 0,
    @SerialName("max_connections") val maxConnections: Int = 32,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    @SerialName("checksum_expected") val checksumExpected: String? = null,
    @SerialName("checksum_verified") val checksumVerified: Boolean? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    val segments: List<SegmentUi> = emptyList()
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

@Serializable
data class TorrentFileUi(
    val index: Int = 0,
    val name: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0L,
    @SerialName("downloaded_bytes") val downloadedBytes: Long = 0L,
    val selected: Boolean = true
)

@Serializable
data class TorrentPeerUi(
    val addr: String = "",
    val client: String = "",
    @SerialName("down_speed_bps") val downSpeedBps: Long = 0L,
    @SerialName("up_speed_bps") val upSpeedBps: Long = 0L,
    @SerialName("progress_pct") val progressPct: Float = 0f
)

@Serializable
data class TorrentItemUi(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("info_hash") val infoHash: String = "",
    @SerialName("magnet_or_source") val magnetOrSource: String = "",
    @SerialName("total_bytes") val totalBytes: Long = 0L,
    @SerialName("downloaded_bytes") val downloadedBytes: Long = 0L,
    @SerialName("uploaded_bytes") val uploadedBytes: Long = 0L,
    @SerialName("down_speed_bps") val downSpeedBps: Long = 0L,
    @SerialName("up_speed_bps") val upSpeedBps: Long = 0L,
    val ratio: Float = 0f,
    val status: String = "Downloading",
    val sequential: Boolean = false,
    @SerialName("peers_connected") val peersConnected: Int = 0,
    @SerialName("seeds_connected") val seedsConnected: Int = 0,
    val files: List<TorrentFileUi> = emptyList(),
    val peers: List<TorrentPeerUi> = emptyList(),
    val trackers: List<String> = emptyList(),
    @SerialName("pieces_bitmap") val piecesBitmap: List<Int> = emptyList()
)

@Serializable
data class RssFeedRuleUi(
    val id: Long = 0L,
    val title: String = "",
    @SerialName("feed_url") val feedUrl: String = "",
    @SerialName("include_regex") val includeRegex: String = "",
    @SerialName("exclude_regex") val excludeRegex: String = "",
    val category: String = "General",
    val enabled: Boolean = true,
    @SerialName("last_checked") val lastChecked: String? = null
)

@Serializable
data class QueueUi(
    val name: String = "Main",
    val running: Boolean = false,
    @SerialName("max_concurrent") val maxConcurrent: Int = 4,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("stop_time") val stopTime: String? = null,
    @SerialName("days_of_week") val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    @SerialName("stop_when_done") val stopWhenDone: Boolean = false,
    @SerialName("item_ids") val itemIds: List<Long> = emptyList()
)

@Serializable
data class DebridAccountUi(
    val provider: String = "",
    val enabled: Boolean = false,
    @SerialName("api_token_masked") val apiTokenMasked: String = "",
    val status: String = "Not configured"
)

@Serializable
data class SettingsUi(
    @SerialName("max_connections") val maxConnections: Int = 32,
    @SerialName("min_split_kb") val minSplitKb: Int = 64,
    @SerialName("speed_limit_kbps") val speedLimitKbps: Long = 0L,
    @SerialName("dns_mode") val dnsMode: String = "system",
    @SerialName("custom_dns_server") val customDnsServer: String = "https://cloudflare-dns.com/dns-query",
    @SerialName("proxy_url") val proxyUrl: String = "",
    @SerialName("proxy_pac_url") val proxyPacUrl: String = "",
    @SerialName("vpn_interface_lock") val vpnInterfaceLock: String = "",
    @SerialName("vpn_kill_switch") val vpnKillSwitch: Boolean = false,
    @SerialName("shizuku_enabled") val shizukuEnabled: Boolean = false,
    @SerialName("shizuku_auto_install_apk") val shizukuAutoInstallApk: Boolean = false,
    @SerialName("shizuku_doze_whitelist") val shizukuDozeWhitelist: Boolean = true,
    @SerialName("clipboard_monitor") val clipboardMonitor: Boolean = true,
    @SerialName("browser_catch_enabled") val browserCatchEnabled: Boolean = true,
    @SerialName("cnl2_enabled") val cnl2Enabled: Boolean = true,
    @SerialName("remote_control_enabled") val remoteControlEnabled: Boolean = false,
    @SerialName("remote_control_port") val remoteControlPort: Int = 8463,
    @SerialName("theme_id") val themeId: String = "classic",
    @SerialName("dark_mode") val darkMode: String = "system",
    @SerialName("locale_code") val localeCode: String = "en",
    @SerialName("update_channel") val updateChannel: String = "stable",
    @SerialName("upstream_version") val upstreamVersion: String = "0.5.2",
    @SerialName("upstream_commit") val upstreamCommit: String = "5032e73a68ba5ca05a8d1611aee2678a1423b24f",
    @SerialName("debrid_accounts") val debridAccounts: List<DebridAccountUi> = emptyList()
)

@Serializable
data class MdmUiState(
    @SerialName("total_down_speed_bps") val totalDownSpeedBps: Long = 0L,
    @SerialName("total_up_speed_bps") val totalUpSpeedBps: Long = 0L,
    @SerialName("active_threads_total") val activeThreadsTotal: Int = 0,
    @SerialName("wake_lock_active") val wakeLockActive: Boolean = false,
    @SerialName("free_disk_bytes") val freeDiskBytes: Long = 64L * 1024L * 1024L * 1024L,
    val categories: List<String> = listOf("All", "General", "Video", "Music", "Compressed", "Documents", "Programs"),
    val downloads: List<DownloadItemUi> = emptyList(),
    val torrents: List<TorrentItemUi> = emptyList(),
    val queues: List<QueueUi> = emptyList(),
    @SerialName("rss_feeds") val rssFeeds: List<RssFeedRuleUi> = emptyList(),
    val settings: SettingsUi = SettingsUi()
)

@Serializable
data class LocaleUi(
    val code: String,
    @SerialName("native_name") val nativeName: String,
    @SerialName("english_name") val englishName: String,
    val flag: String,
    val rtl: Boolean = false
)

/** One downloadable variant returned by the native `probe_video` command. */
@Serializable
data class ProbedStreamUi(
    val itag: Int = 0,
    val label: String = "",
    val quality: String = "",
    @SerialName("format_kind") val formatKind: String = "video",
    @SerialName("bitrate_kbps") val bitrateKbps: Int = 0,
    @SerialName("watermark_free") val watermarkFree: Boolean = true,
    val ext: String = "mp4",
    val category: String = "Video",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0L,
    val url: String = "",
    @SerialName("user_agent") val userAgent: String = ""
)

/** Result of probing a shared/pasted URL (YouTube, TikTok, Instagram, Facebook, X, direct). */
@Serializable
data class ProbeResultUi(
    val ok: Boolean = false,
    val platform: String = "Direct",
    val title: String = "",
    val author: String = "",
    @SerialName("watermark_free") val watermarkFree: Boolean = false,
    @SerialName("extractor_version") val extractorVersion: String = "",
    val streams: List<ProbedStreamUi> = emptyList(),
    val error: String? = null
)
