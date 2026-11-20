//! Data Transfer Objects (DTOs) serialized across the Rust <-> Android Kotlin JNI boundary.

use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SegmentDto {
    pub index: u32,
    pub start: u64,
    pub pos: u64,
    pub end: u64,
    pub active: bool,
    pub speed_bps: u64,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct DownloadItemDto {
    pub id: u64,
    pub name: String,
    pub url: String,
    pub category: String,
    pub queue: String,
    pub save_path: String,
    pub total_bytes: u64,
    pub downloaded_bytes: u64,
    pub speed_bps: u64,
    pub eta_seconds: Option<u64>,
    pub status: String,
    pub resumable: bool,
    pub active_connections: u32,
    pub max_connections: u32,
    pub country_code: Option<String>,
    pub country_name: Option<String>,
    pub checksum_expected: Option<String>,
    pub checksum_verified: Option<bool>,
    pub error_message: Option<String>,
    pub segments: Vec<SegmentDto>,
    #[serde(default)]
    pub user_agent: Option<String>,
    #[serde(default)]
    pub referer: Option<String>,
    #[serde(default)]
    pub cookie: Option<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct TorrentFileDto {
    pub index: usize,
    pub name: String,
    pub size_bytes: u64,
    pub downloaded_bytes: u64,
    pub selected: bool,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct TorrentPeerDto {
    pub addr: String,
    pub client: String,
    pub down_speed_bps: u64,
    pub up_speed_bps: u64,
    pub progress_pct: f32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct TorrentItemDto {
    pub id: u64,
    pub name: String,
    pub info_hash: String,
    pub magnet_or_source: String,
    pub total_bytes: u64,
    pub downloaded_bytes: u64,
    pub uploaded_bytes: u64,
    pub down_speed_bps: u64,
    pub up_speed_bps: u64,
    pub ratio: f32,
    pub status: String,
    pub sequential: bool,
    pub peers_connected: u32,
    pub seeds_connected: u32,
    pub files: Vec<TorrentFileDto>,
    pub peers: Vec<TorrentPeerDto>,
    pub trackers: Vec<String>,
    pub pieces_bitmap: Vec<u8>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct RssFeedRuleDto {
    pub id: u64,
    pub title: String,
    pub feed_url: String,
    pub include_regex: String,
    pub exclude_regex: String,
    pub category: String,
    pub enabled: bool,
    pub last_checked: Option<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct QueueDto {
    pub name: String,
    pub running: bool,
    pub max_concurrent: u32,
    pub start_time: Option<String>,
    pub stop_time: Option<String>,
    pub days_of_week: Vec<u8>,
    pub stop_when_done: bool,
    pub item_ids: Vec<u64>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct DebridAccountDto {
    pub provider: String,
    pub enabled: bool,
    pub api_token_masked: String,
    pub status: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SettingsDto {
    pub max_connections: u32,
    pub min_split_kb: u32,
    pub speed_limit_kbps: u64,
    pub dns_mode: String,
    pub custom_dns_server: String,
    pub proxy_url: String,
    pub proxy_pac_url: String,
    pub vpn_interface_lock: String,
    pub vpn_kill_switch: bool,
    pub shizuku_enabled: bool,
    pub shizuku_auto_install_apk: bool,
    pub shizuku_doze_whitelist: bool,
    pub clipboard_monitor: bool,
    pub browser_catch_enabled: bool,
    pub cnl2_enabled: bool,
    pub remote_control_enabled: bool,
    pub remote_control_port: u16,
    pub theme_id: String,
    pub dark_mode: String,
    pub locale_code: String,
    pub update_channel: String,
    pub upstream_version: String,
    pub upstream_commit: String,
    pub debrid_accounts: Vec<DebridAccountDto>,
}

impl Default for SettingsDto {
    fn default() -> Self {
        Self {
            max_connections: 32,
            min_split_kb: 64,
            speed_limit_kbps: 0,
            dns_mode: "system".to_string(),
            custom_dns_server: "https://cloudflare-dns.com/dns-query".to_string(),
            proxy_url: String::new(),
            proxy_pac_url: String::new(),
            vpn_interface_lock: String::new(),
            vpn_kill_switch: false,
            shizuku_enabled: false,
            shizuku_auto_install_apk: false,
            shizuku_doze_whitelist: true,
            clipboard_monitor: true,
            browser_catch_enabled: true,
            cnl2_enabled: true,
            remote_control_enabled: false,
            remote_control_port: 8463,
            theme_id: "obsidian".to_string(),
            dark_mode: "dark".to_string(),
            locale_code: "en".to_string(),
            update_channel: "stable".to_string(),
            upstream_version: "0.5.2".to_string(),
            upstream_commit: "5032e73a68ba5ca05a8d1611aee2678a1423b24f".to_string(),
            debrid_accounts: vec![
                DebridAccountDto {
                    provider: "Real-Debrid".into(),
                    enabled: false,
                    api_token_masked: String::new(),
                    status: "Not configured".into(),
                },
                DebridAccountDto {
                    provider: "AllDebrid".into(),
                    enabled: false,
                    api_token_masked: String::new(),
                    status: "Not configured".into(),
                },
                DebridAccountDto {
                    provider: "Premiumize".into(),
                    enabled: false,
                    api_token_masked: String::new(),
                    status: "Not configured".into(),
                },
                DebridAccountDto {
                    provider: "TorBox".into(),
                    enabled: false,
                    api_token_masked: String::new(),
                    status: "Not configured".into(),
                },
                DebridAccountDto {
                    provider: "Mega-Debrid".into(),
                    enabled: false,
                    api_token_masked: String::new(),
                    status: "Not configured".into(),
                },
            ],
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AppSnapshotDto {
    pub total_down_speed_bps: u64,
    pub total_up_speed_bps: u64,
    pub active_threads_total: u32,
    pub wake_lock_active: bool,
    pub free_disk_bytes: u64,
    pub categories: Vec<String>,
    pub downloads: Vec<DownloadItemDto>,
    pub torrents: Vec<TorrentItemDto>,
    pub queues: Vec<QueueDto>,
    pub rss_feeds: Vec<RssFeedRuleDto>,
    pub settings: SettingsDto,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LocaleInfoDto {
    pub code: String,
    pub native_name: String,
    pub english_name: String,
    pub flag: String,
    pub rtl: bool,
}
