use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use tokio::runtime::Runtime;
use tokio_util::sync::CancellationToken;
use url::Url;

use crate::models::{
    AppSnapshotDto, DebridAccountDto, DownloadItemDto, QueueDto, RssFeedRuleDto, SegmentDto,
    SettingsDto, TorrentItemDto,
};

/// Maximum connections unlocked on Android (`1..=128`).
pub fn clamp_connections_unlocked(requested: u32) -> u32 {
    requested.clamp(1, mdm_engine::download::MAX_CONNECTIONS_UNLOCKED)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case")]
pub enum ControllerCommand {
    #[serde(alias = "add_download")]
    AddDownload {
        url: String,
        file_name: Option<String>,
        category: Option<String>,
        connections: Option<u32>,
        queue: Option<String>,
        checksum_expected: Option<String>,
        start_immediately: Option<bool>,
    },
    PauseDownload {
        id: u64,
    },
    ResumeDownload {
        id: u64,
    },
    CancelDownload {
        id: u64,
        delete_file: Option<bool>,
    },
    RefreshLink {
        id: u64,
        new_url: String,
    },
    AddTorrent {
        source: String,
        sequential: Option<bool>,
    },
    PauseTorrent {
        id: u64,
    },
    ResumeTorrent {
        id: u64,
    },
    SetTorrentSequential {
        id: u64,
        sequential: bool,
    },
    AddQueue {
        name: String,
        max_concurrent: u32,
        start_time: Option<String>,
        stop_time: Option<String>,
    },
    StartQueue {
        name: String,
    },
    StopQueue {
        name: String,
    },
    AddDebrid {
        provider: String,
        api_token: String,
        enabled: bool,
    },
    AddRssFeed {
        title: String,
        feed_url: String,
        include_regex: Option<String>,
        exclude_regex: Option<String>,
        category: Option<String>,
    },
    ExtractArchive {
        archive_path: String,
        dest_dir: String,
    },
    VerifyChecksum {
        id: u64,
        file_path: String,
        expected_hash: String,
    },
    ProbeVideo {
        url: String,
    },
    UpdateExtractors,
    RetryDownload {
        id: u64,
    },
    UpdateSettings {
        max_connections: Option<u32>,
        min_split_kb: Option<u32>,
        speed_limit_kbps: Option<u64>,
        dns_mode: Option<String>,
        custom_dns_server: Option<String>,
        proxy_url: Option<String>,
        vpn_interface_lock: Option<String>,
        vpn_kill_switch: Option<bool>,
        shizuku_enabled: Option<bool>,
        shizuku_auto_install_apk: Option<bool>,
        shizuku_doze_whitelist: Option<bool>,
        clipboard_monitor: Option<bool>,
        browser_catch_enabled: Option<bool>,
        cnl2_enabled: Option<bool>,
        remote_control_enabled: Option<bool>,
        remote_control_port: Option<u16>,
        theme_id: Option<String>,
        dark_mode: Option<String>,
        locale_code: Option<String>,
        update_channel: Option<String>,
    },
}

#[derive(Debug, Default, Serialize, Deserialize)]
struct PersistedState {
    next_id: u64,
    downloads: Vec<DownloadItemDto>,
    torrents: Vec<TorrentItemDto>,
    queues: Vec<QueueDto>,
    categories: Vec<String>,
    rss_feeds: Vec<RssFeedRuleDto>,
    settings: SettingsDto,
}

pub struct AndroidCoreController {
    storage_dir: PathBuf,
    downloads_dir: PathBuf,
    runtime: Arc<Runtime>,
    state: Arc<Mutex<PersistedState>>,
    active_tokens: Arc<Mutex<HashMap<u64, CancellationToken>>>,
    limiter: Arc<mdm_engine::limiter::Limiter>,
    hosts: Arc<mdm_engine::hosts::HostLimits>,
}

impl AndroidCoreController {
    pub fn new(storage_dir: PathBuf, downloads_dir: PathBuf) -> Result<Self, String> {
        std::fs::create_dir_all(&storage_dir).map_err(|e| e.to_string())?;
        std::fs::create_dir_all(&downloads_dir).map_err(|e| e.to_string())?;

        let runtime = Arc::new(
            tokio::runtime::Builder::new_multi_thread()
                .worker_threads(4)
                .enable_all()
                .build()
                .map_err(|e| e.to_string())?,
        );

        let state_file = storage_dir.join("hyperstream_state.json");
        let mut loaded: PersistedState = std::fs::read_to_string(&state_file)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_else(|| PersistedState {
                next_id: 1,
                downloads: Vec::new(),
                torrents: Vec::new(),
                queues: default_queues(),
                categories: vec![
                    "General".into(),
                    "Video".into(),
                    "Music".into(),
                    "Compressed".into(),
                    "Documents".into(),
                    "Programs".into(),
                ],
                rss_feeds: Vec::new(),
                settings: SettingsDto::default(),
            });

        // Clean up any legacy stuck 0-byte "watch" entries from raw YouTube watch URLs
        loaded
            .downloads
            .retain(|d| !(d.name == "watch" && d.downloaded_bytes == 0));
        for d in &mut loaded.downloads {
            if (d.status == "Downloading" || d.status == "Probing") && d.downloaded_bytes == 0 && d.total_bytes == 0 {
                d.status = "Paused".to_string();
            }
        }

        let initial_rate = (loaded.settings.speed_limit_kbps > 0)
            .then_some(loaded.settings.speed_limit_kbps * 1024);
        let limiter = Arc::new(mdm_engine::limiter::Limiter::new(initial_rate));

        Ok(Self {
            storage_dir,
            downloads_dir,
            runtime,
            state: Arc::new(Mutex::new(loaded)),
            active_tokens: Arc::new(Mutex::new(HashMap::new())),
            limiter,
            hosts: Arc::new(mdm_engine::hosts::HostLimits::default()),
        })
    }

    fn persist_locked(&self, st: &PersistedState) {
        let state_file = self.storage_dir.join("hyperstream_state.json");
        if let Ok(bytes) = serde_json::to_vec_pretty(st) {
            let _ = craft_atomic::atomic_write(&state_file, &bytes);
        }
    }

    pub fn handle_command_json(&self, json_str: &str) -> String {
        match self.execute_command_json(json_str) {
            Ok(resp) => resp,
            Err(err) => serde_json::json!({ "ok": false, "error": err }).to_string(),
        }
    }

    pub fn execute_command_json(&self, json_str: &str) -> Result<String, String> {
        let cmd: ControllerCommand =
            serde_json::from_str(json_str).map_err(|e| format!("invalid command JSON: {e}"))?;
        match cmd {
            ControllerCommand::AddDownload {
                url,
                file_name,
                category,
                connections,
                queue,
                checksum_expected,
                start_immediately,
            } => {
                let mut st = self.state.lock().unwrap();
                let id = st.next_id.max(1);
                st.next_id = id + 1;

                let default_conns = st.settings.max_connections;
                let clamped_conns =
                    clamp_connections_unlocked(connections.unwrap_or(default_conns));
                let inferred_name = file_name.unwrap_or_else(|| infer_filename(&url));
                let cat = category.unwrap_or_else(|| infer_category(&inferred_name));
                let save_dir = self.downloads_dir.join(&cat);
                let _ = std::fs::create_dir_all(&save_dir);
                let save_path = save_dir.join(&inferred_name).to_string_lossy().into_owned();

                if let Some(existing) = st.downloads.iter_mut().find(|d| {
                    (d.status == "Paused" || d.status == "Failed")
                        && (d.url == url || d.save_path == save_path)
                }) {
                    let existing_id = existing.id;
                    update_resume_state_url(&existing.save_path, &url);
                    existing.url = url;
                    existing.error_message = None;
                    existing.status = "Downloading".to_string();
                    self.persist_locked(&st);
                    drop(st);
                    self.spawn_engine_download(existing_id);
                    return Ok(serde_json::json!({
                        "ok": true,
                        "id": existing_id,
                        "status": "Downloading",
                        "refreshed_existing": true
                    }).to_string());
                }

                let queue_name = queue.unwrap_or_else(|| "Main".to_string());
                let should_start = start_immediately.unwrap_or(queue_name != "Overnight");
                let status = if should_start {
                    "Downloading".to_string()
                } else {
                    "Queued".to_string()
                };

                let segments = build_initial_segments(clamped_conns);

                let item = DownloadItemDto {
                    id,
                    name: inferred_name,
                    url,
                    category: cat,
                    queue: queue_name,
                    save_path,
                    total_bytes: 0,
                    downloaded_bytes: 0,
                    speed_bps: 0,
                    eta_seconds: None,
                    status,
                    resumable: true,
                    active_connections: 0,
                    max_connections: clamped_conns,
                    country_code: None,
                    country_name: None,
                    checksum_expected,
                    checksum_verified: None,
                    error_message: None,
                    segments,
                };
                st.downloads.insert(0, item);
                self.persist_locked(&st);
                drop(st);

                if should_start {
                    self.spawn_engine_download(id);
                }

                Ok(serde_json::json!({
                    "ok": true,
                    "id": id,
                    "max_connections": clamped_conns
                })
                .to_string())
            }
            ControllerCommand::PauseDownload { id } => {
                if let Some(tok) = self.active_tokens.lock().unwrap().remove(&id) {
                    tok.cancel();
                }
                let mut st = self.state.lock().unwrap();
                if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                    d.status = "Paused".to_string();
                    d.speed_bps = 0;
                    d.active_connections = 0;
                }
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "id": id, "status": "Paused" }).to_string())
            }
            ControllerCommand::ResumeDownload { id } => {
                {
                    let mut st = self.state.lock().unwrap();
                    if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                        d.status = "Downloading".to_string();
                        d.error_message = None;
                    }
                    self.persist_locked(&st);
                }
                self.spawn_engine_download(id);
                Ok(serde_json::json!({ "ok": true, "id": id, "status": "Downloading" }).to_string())
            }
            ControllerCommand::CancelDownload { id, delete_file } => {
                if let Some(tok) = self.active_tokens.lock().unwrap().remove(&id) {
                    tok.cancel();
                }
                let mut st = self.state.lock().unwrap();
                if let Some(pos) = st.downloads.iter().position(|d| d.id == id) {
                    let removed = st.downloads.remove(pos);
                    if delete_file.unwrap_or(false) {
                        let _ = std::fs::remove_file(&removed.save_path);
                        let _ = std::fs::remove_file(format!("{}.mdm.part", removed.save_path));
                    }
                }
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "id": id }).to_string())
            }
            ControllerCommand::RefreshLink { id, new_url } => {
                let mut st = self.state.lock().unwrap();
                let mut should_resume = false;
                if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                    update_resume_state_url(&d.save_path, &new_url);
                    d.url = new_url;
                    d.error_message = None;
                    if d.status == "Paused" || d.status == "Failed" {
                        d.status = "Downloading".to_string();
                        should_resume = true;
                    }
                }
                self.persist_locked(&st);
                drop(st);
                if should_resume {
                    self.spawn_engine_download(id);
                }
                Ok(serde_json::json!({ "ok": true, "id": id, "resumed": should_resume }).to_string())
            }
            ControllerCommand::AddTorrent { source, sequential } => {
                let mut st = self.state.lock().unwrap();
                let id = st.next_id.max(1);
                st.next_id = id + 1;
                let name = extract_magnet_display_name(&source)
                    .unwrap_or_else(|| format!("Torrent #{id}"));
                let info_hash = extract_magnet_btih(&source)
                    .unwrap_or_else(|| format!("btih-{id:08x}"));
                let item = TorrentItemDto {
                    id,
                    name,
                    info_hash,
                    magnet_or_source: source,
                    total_bytes: 0,
                    downloaded_bytes: 0,
                    uploaded_bytes: 0,
                    down_speed_bps: 0,
                    up_speed_bps: 0,
                    ratio: 0.0,
                    status: "Metadata".to_string(),
                    sequential: sequential.unwrap_or(false),
                    peers_connected: 0,
                    seeds_connected: 0,
                    files: Vec::new(),
                    peers: Vec::new(),
                    trackers: vec![
                        "udp://tracker.opentrackr.org:1337/announce".to_string(),
                        "udp://open.stealth.si:80/announce".to_string(),
                    ],
                    pieces_bitmap: vec![0; 16],
                };
                st.torrents.insert(0, item);
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "id": id }).to_string())
            }
            ControllerCommand::PauseTorrent { id } => {
                let mut st = self.state.lock().unwrap();
                if let Some(t) = st.torrents.iter_mut().find(|t| t.id == id) {
                    t.status = "Paused".to_string();
                    t.down_speed_bps = 0;
                    t.up_speed_bps = 0;
                }
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "id": id }).to_string())
            }
            ControllerCommand::ResumeTorrent { id } => {
                let mut st = self.state.lock().unwrap();
                if let Some(t) = st.torrents.iter_mut().find(|t| t.id == id) {
                    t.status = "Downloading".to_string();
                }
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "id": id }).to_string())
            }
            ControllerCommand::SetTorrentSequential { id, sequential } => {
                let mut st = self.state.lock().unwrap();
                if let Some(t) = st.torrents.iter_mut().find(|t| t.id == id) {
                    t.sequential = sequential;
                }
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "id": id, "sequential": sequential }).to_string())
            }
            ControllerCommand::AddQueue {
                name,
                max_concurrent,
                start_time,
                stop_time,
            } => {
                let mut st = self.state.lock().unwrap();
                if let Some(existing) = st.queues.iter_mut().find(|q| q.name == name) {
                    existing.max_concurrent = max_concurrent.clamp(1, 32);
                    existing.start_time = start_time;
                    existing.stop_time = stop_time;
                } else {
                    st.queues.push(QueueDto {
                        name: name.clone(),
                        running: false,
                        max_concurrent: max_concurrent.clamp(1, 32),
                        start_time,
                        stop_time,
                        days_of_week: vec![1, 2, 3, 4, 5, 6, 7],
                        stop_when_done: false,
                        item_ids: Vec::new(),
                    });
                }
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "queue": name }).to_string())
            }
            ControllerCommand::StartQueue { name } => {
                let ids_to_start: Vec<u64> = {
                    let mut st = self.state.lock().unwrap();
                    let max_c = st
                        .queues
                        .iter_mut()
                        .find(|q| q.name == name)
                        .map(|q| {
                            q.running = true;
                            q.max_concurrent as usize
                        })
                        .unwrap_or(2);
                    let mut started = Vec::new();
                    for d in st.downloads.iter_mut() {
                        if d.queue == name
                            && (d.status == "Queued" || d.status == "Paused")
                            && started.len() < max_c
                        {
                            d.status = "Downloading".to_string();
                            started.push(d.id);
                        }
                    }
                    self.persist_locked(&st);
                    started
                };
                for dl_id in ids_to_start {
                    self.spawn_engine_download(dl_id);
                }
                Ok(serde_json::json!({ "ok": true, "queue": name, "running": true }).to_string())
            }
            ControllerCommand::StopQueue { name } => {
                let ids_to_stop: Vec<u64> = {
                    let mut st = self.state.lock().unwrap();
                    if let Some(q) = st.queues.iter_mut().find(|q| q.name == name) {
                        q.running = false;
                    }
                    let mut stopped = Vec::new();
                    for d in st.downloads.iter_mut() {
                        if d.queue == name && d.status == "Downloading" {
                            d.status = "Queued".to_string();
                            d.speed_bps = 0;
                            stopped.push(d.id);
                        }
                    }
                    self.persist_locked(&st);
                    stopped
                };
                for dl_id in ids_to_stop {
                    if let Some(tok) = self.active_tokens.lock().unwrap().remove(&dl_id) {
                        tok.cancel();
                    }
                }
                Ok(serde_json::json!({ "ok": true, "queue": name, "running": false }).to_string())
            }
            ControllerCommand::AddDebrid {
                provider,
                api_token,
                enabled,
            } => {
                let mut st = self.state.lock().unwrap();
                st.settings
                    .debrid_accounts
                    .retain(|a| a.provider != provider);
                st.settings.debrid_accounts.push(DebridAccountDto {
                    provider: provider.clone(),
                    enabled,
                    api_token_masked: mask_token(&api_token),
                    status: if enabled {
                        "Active (Ready)".to_string()
                    } else {
                        "Disabled".to_string()
                    },
                });
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "provider": provider }).to_string())
            }
            ControllerCommand::AddRssFeed {
                title,
                feed_url,
                include_regex,
                exclude_regex,
                category,
            } => {
                let mut st = self.state.lock().unwrap();
                let id = st.next_id.max(1);
                st.next_id = id + 1;
                st.rss_feeds.push(RssFeedRuleDto {
                    id,
                    title,
                    feed_url,
                    include_regex: include_regex.unwrap_or_default(),
                    exclude_regex: exclude_regex.unwrap_or_default(),
                    category: category.unwrap_or_else(|| "General".to_string()),
                    enabled: true,
                    last_checked: Some("Just now".to_string()),
                });
                self.persist_locked(&st);
                Ok(serde_json::json!({ "ok": true, "id": id }).to_string())
            }
            ControllerCommand::ExtractArchive {
                archive_path,
                dest_dir,
            } => {
                let mut on_file = |_: &Path| {};
                let mut policy = mdm_extract::Policy {
                    dest: PathBuf::from(&dest_dir),
                    subfolder: mdm_extract::Subfolder::Smart,
                    password: None,
                    limits: mdm_extract::Limits::default(),
                    tool: None,
                    hatchzip: None,
                    in_place: false,
                    runs: None,
                    on_file: &mut on_file,
                    cancel: None,
                };
                let res = mdm_extract::extract(Path::new(&archive_path), &mut policy);
                match res {
                    Ok(report) => Ok(serde_json::json!({
                        "ok": true,
                        "dest": report.folder.to_string_lossy(),
                        "files": report.files
                    })
                    .to_string()),
                    Err(e) => Err(format!("archive extraction failed: {e}")),
                }
            }
            ControllerCommand::VerifyChecksum {
                id,
                file_path,
                expected_hash,
            } => {
                let parsed = mdm_engine::verify::Hash::parse(&expected_hash).ok_or_else(|| {
                    "unsupported checksum format (use CRC32/MD5/SHA-1/SHA-256)".to_string()
                })?;
                let matched = mdm_engine::verify::matches(Path::new(&file_path), &parsed)
                    .map_err(|e| format!("checksum read error: {e}"))?;
                let digest_bytes = mdm_engine::verify::digest(Path::new(&file_path), parsed.algo)
                    .map_err(|e| format!("checksum digest error: {e}"))?;
                let computed = hex::encode(digest_bytes);
                let mut st = self.state.lock().unwrap();
                if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                    d.checksum_verified = Some(matched);
                }
                self.persist_locked(&st);
                Ok(serde_json::json!({
                    "ok": true,
                    "matched": matched,
                    "computed": computed
                })
                .to_string())
            }
            ControllerCommand::ProbeVideo { url } => {
                let rules = crate::extractors::ExtractorRules::load_or_default(&self.storage_dir);
                let res = self
                    .runtime
                    .block_on(crate::extractors::probe_media_url(&url, &rules));
                match res {
                    Ok(val) => Ok(val.to_string()),
                    Err(e) => Err(e),
                }
            }
            ControllerCommand::UpdateExtractors => {
                let res = self
                    .runtime
                    .block_on(crate::extractors::update_extractor_rules(&self.storage_dir));
                match res {
                    Ok(rules) => Ok(serde_json::json!({
                        "ok": true,
                        "version": rules.version,
                        "ota_url": rules.ota_url
                    })
                    .to_string()),
                    Err(e) => Err(e),
                }
            }
            ControllerCommand::RetryDownload { id } => {
                {
                    let mut st = self.state.lock().unwrap();
                    if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                        d.status = "Downloading".to_string();
                        d.error_message = None;
                    }
                    self.persist_locked(&st);
                }
                self.spawn_engine_download(id);
                Ok(serde_json::json!({ "ok": true, "id": id, "status": "Downloading" }).to_string())
            }
            ControllerCommand::UpdateSettings {
                max_connections,
                min_split_kb,
                speed_limit_kbps,
                dns_mode,
                custom_dns_server,
                proxy_url,
                vpn_interface_lock,
                vpn_kill_switch,
                shizuku_enabled,
                shizuku_auto_install_apk,
                shizuku_doze_whitelist,
                clipboard_monitor,
                browser_catch_enabled,
                cnl2_enabled,
                remote_control_enabled,
                remote_control_port,
                theme_id,
                dark_mode,
                locale_code,
                update_channel,
            } => {
                let mut st = self.state.lock().unwrap();
                if let Some(mc) = max_connections {
                    st.settings.max_connections = clamp_connections_unlocked(mc);
                }
                if let Some(ms) = min_split_kb {
                    st.settings.min_split_kb = ms;
                }
                if let Some(sl) = speed_limit_kbps {
                    st.settings.speed_limit_kbps = sl;
                    if sl == 0 {
                        self.limiter.set(None);
                    } else {
                        self.limiter.set(Some(sl * 1024));
                    }
                }
                if let Some(dm) = dns_mode {
                    st.settings.dns_mode = dm;
                }
                if let Some(cds) = custom_dns_server {
                    st.settings.custom_dns_server = cds;
                }
                if let Some(pu) = proxy_url {
                    st.settings.proxy_url = pu;
                }
                if let Some(vil) = vpn_interface_lock {
                    st.settings.vpn_interface_lock = vil;
                }
                if let Some(vks) = vpn_kill_switch {
                    st.settings.vpn_kill_switch = vks;
                }
                if let Some(shz) = shizuku_enabled {
                    st.settings.shizuku_enabled = shz;
                }
                if let Some(saia) = shizuku_auto_install_apk {
                    st.settings.shizuku_auto_install_apk = saia;
                }
                if let Some(sdw) = shizuku_doze_whitelist {
                    st.settings.shizuku_doze_whitelist = sdw;
                }
                if let Some(cm) = clipboard_monitor {
                    st.settings.clipboard_monitor = cm;
                }
                if let Some(bce) = browser_catch_enabled {
                    st.settings.browser_catch_enabled = bce;
                }
                if let Some(cnl) = cnl2_enabled {
                    st.settings.cnl2_enabled = cnl;
                }
                if let Some(rce) = remote_control_enabled {
                    st.settings.remote_control_enabled = rce;
                }
                if let Some(rcp) = remote_control_port {
                    st.settings.remote_control_port = rcp;
                }
                if let Some(th) = theme_id {
                    st.settings.theme_id = th;
                }
                if let Some(dk) = dark_mode {
                    st.settings.dark_mode = dk;
                }
                if let Some(loc) = locale_code {
                    st.settings.locale_code = loc;
                }
                if let Some(ch) = update_channel {
                    st.settings.update_channel = ch;
                }
                self.persist_locked(&st);
                Ok(serde_json::json!({
                    "ok": true,
                    "settings": st.settings
                })
                .to_string())
            }
        }
    }

    fn spawn_engine_download(&self, id: u64) {
        let (url_str, save_path, max_conns) = {
            let st = self.state.lock().unwrap();
            let Some(item) = st.downloads.iter().find(|d| d.id == id) else {
                return;
            };
            if !url_str_is_network(&item.url) {
                return;
            }
            (item.url.clone(), item.save_path.clone(), item.max_connections)
        };

        let cancel_token = CancellationToken::new();
        self.active_tokens
            .lock()
            .unwrap()
            .insert(id, cancel_token.clone());

        let state_arc = self.state.clone();
        let tokens_arc = self.active_tokens.clone();
        let limiter = self.limiter.clone();
        let hosts = self.hosts.clone();
        let storage_dir = self.storage_dir.clone();
        let downloads_dir = self.downloads_dir.clone();

        self.runtime.spawn(async move {
            let mut effective_url = url_str.clone();
            let mut effective_save_path = save_path.clone();
            let rules = crate::extractors::ExtractorRules::load_or_default(&storage_dir);
            let mut custom_ua: Option<String> = None;

            // Resolve YouTube / TikTok / Instagram / Facebook / Social page URLs automatically
            let needs_resolution = crate::extractors::detect_platform(&effective_url) != "Direct"
                && !effective_url.contains("googlevideo.com")
                && !effective_url.contains("tikwm.com");

            if needs_resolution {
                {
                    let mut st = state_arc.lock().unwrap();
                    if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                        d.status = "Probing".to_string();
                        d.error_message = None;
                    }
                }
                match crate::extractors::resolve_best_media_stream(&effective_url, &rules).await {
                    Ok(resolved) => {
                        effective_url = resolved.stream_url.clone();
                        custom_ua = Some(resolved.user_agent);
                        let save_dir = downloads_dir.join(&resolved.category);
                        let _ = std::fs::create_dir_all(&save_dir);
                        effective_save_path = save_dir
                            .join(&resolved.file_name)
                            .to_string_lossy()
                            .into_owned();
                        let mut st = state_arc.lock().unwrap();
                        if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                            d.name = resolved.file_name;
                            d.category = resolved.category;
                            d.save_path = effective_save_path.clone();
                            d.status = "Downloading".to_string();
                            if resolved.size_bytes > 0 {
                                d.total_bytes = resolved.size_bytes;
                            }
                        }
                    }
                    Err(err_msg) => {
                        let mut st = state_arc.lock().unwrap();
                        if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                            d.status = "Failed".to_string();
                            d.error_message = Some(err_msg);
                            d.speed_bps = 0;
                            d.active_connections = 0;
                        }
                        if let Ok(bytes) = serde_json::to_vec_pretty(&*st) {
                            let _ = craft_atomic::atomic_write(
                                &storage_dir.join("hyperstream_state.json"),
                                &bytes,
                            );
                        }
                        tokens_arc.lock().unwrap().remove(&id);
                        return;
                    }
                }
            }

            let Ok(parsed_url) = Url::parse(&effective_url) else {
                return;
            };
            let mut opts = mdm_engine::Options::default();
            if let Some(ua) = custom_ua {
                opts.user_agent = ua;
            } else if effective_url.contains("googlevideo.com") {
                if effective_url.contains("c=IOS") {
                    opts.user_agent = rules.youtube_ios_ua;
                } else {
                    opts.user_agent = rules.youtube_android_ua;
                }
            }
            if effective_url.contains("googlevideo.com") {
                opts.max_connections = clamp_connections_unlocked(max_conns).min(16);
            } else {
                opts.max_connections = clamp_connections_unlocked(max_conns);
            }
            opts.global = Some(limiter);
            opts.host_limits = Some(hosts);

            let req = mdm_engine::Request {
                url: parsed_url,
                headers: reqwest::header::HeaderMap::new(),
                dest: mdm_engine::Destination::File(PathBuf::from(&effective_save_path)),
            };

            let dl = {
                let dest_path = std::path::PathBuf::from(&effective_save_path);
                let st_file = mdm_engine::state::state_path(&dest_path);
                let part_file = mdm_engine::naming::part_path(&dest_path);
                if st_file.exists() && part_file.exists() {
                    update_resume_state_url(&effective_save_path, &effective_url);
                    let resume_headers = req.headers.clone();
                    let resume_opts = opts.clone();
                    mdm_engine::Download::resume(st_file, resume_headers, resume_opts)
                        .unwrap_or_else(|_| mdm_engine::Download::start(req, opts))
                } else {
                    mdm_engine::Download::start(req, opts)
                }
            };
            let mut prev_bytes = 0u64;
            let mut prev_tick = std::time::Instant::now();

            loop {
                if cancel_token.is_cancelled() {
                    dl.cancel();
                    break;
                }
                let snap = dl.snapshot();
                let elapsed = prev_tick.elapsed().as_secs_f64().max(0.05);
                let speed = ((snap.downloaded.saturating_sub(prev_bytes)) as f64 / elapsed) as u64;
                prev_bytes = snap.downloaded;
                prev_tick = std::time::Instant::now();

                {
                    let mut st = state_arc.lock().unwrap();
                    if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                        if let Some(sz) = snap.size {
                            d.total_bytes = sz;
                        }
                        d.downloaded_bytes = snap.downloaded;
                        d.speed_bps = speed;
                        d.resumable = snap.resumable.unwrap_or(true);
                        if d.total_bytes > 0 {
                            let rem = d.total_bytes.saturating_sub(snap.downloaded);
                            d.eta_seconds = if speed > 0 { Some(rem / speed) } else { None };
                        }
                        if let Some(srv) = &snap.server
                            && let Some(ip) = srv.ip
                            && let Some(cc) = mdm_geo::country(ip)
                        {
                            let code = cc.code().to_string();
                            d.country_code = Some(code.clone());
                            d.country_name = Some(code);
                        }
                        if !snap.segments.is_empty() {
                            d.segments = snap
                                .segments
                                .iter()
                                .enumerate()
                                .map(|(idx, s)| SegmentDto {
                                    index: idx as u32,
                                    start: s.start,
                                    pos: s.cursor,
                                    end: s.end,
                                    active: s.cursor < s.end,
                                    speed_bps: 0,
                                })
                                .collect();
                        }
                        d.active_connections = snap
                            .connections
                            .iter()
                            .filter(|c| {
                                matches!(c.status, mdm_engine::download::ConnStatus::Receiving)
                            })
                            .count() as u32;
                    }
                }

                if matches!(
                    snap.state,
                    mdm_engine::download::State::Done
                        | mdm_engine::download::State::Failed
                        | mdm_engine::download::State::Cancelled
                ) {
                    break;
                }
                tokio::time::sleep(Duration::from_millis(150)).await;
            }

            let res = dl.wait().await;
            let mut st = state_arc.lock().unwrap();
            if let Some(d) = st.downloads.iter_mut().find(|d| d.id == id) {
                match res {
                    Ok(outcome) => {
                        d.status = "Completed".to_string();
                        d.downloaded_bytes = outcome.size;
                        d.total_bytes = outcome.size;
                        d.speed_bps = 0;
                        d.eta_seconds = Some(0);
                        d.active_connections = 0;
                    }
                    Err(err) => {
                        if d.status != "Paused" && d.status != "Queued" {
                            d.status = "Failed".to_string();
                            d.error_message = Some(err.to_string());
                        }
                        d.speed_bps = 0;
                        d.active_connections = 0;
                    }
                }
            }
            if let Ok(bytes) = serde_json::to_vec_pretty(&*st) {
                let _ = craft_atomic::atomic_write(
                    &storage_dir.join("hyperstream_state.json"),
                    &bytes,
                );
            }
            tokens_arc.lock().unwrap().remove(&id);
        });
    }

    pub fn snapshot(&self) -> AppSnapshotDto {
        let st = self.state.lock().unwrap();
        let total_dl_speed: u64 = st.downloads.iter().map(|d| d.speed_bps).sum::<u64>()
            + st.torrents.iter().map(|t| t.down_speed_bps).sum::<u64>();
        let total_ul_speed: u64 = st.torrents.iter().map(|t| t.up_speed_bps).sum();
        let active_threads_total: u32 = st
            .downloads
            .iter()
            .map(|d| d.active_connections)
            .sum();
        let wake_lock_active = st
            .downloads
            .iter()
            .any(|d| d.status == "Downloading" || d.status == "Probing")
            || st
                .torrents
                .iter()
                .any(|t| t.status == "Downloading" || t.status == "Metadata")
            || st.queues.iter().any(|q| q.running);

        let free_disk_bytes = mdm_engine::disk::volume_of(&self.downloads_dir)
            .map(|v| v.free)
            .unwrap_or(0);

        AppSnapshotDto {
            total_down_speed_bps: total_dl_speed,
            total_up_speed_bps: total_ul_speed,
            active_threads_total,
            wake_lock_active,
            free_disk_bytes,
            categories: st.categories.clone(),
            downloads: st.downloads.clone(),
            torrents: st.torrents.clone(),
            queues: st.queues.clone(),
            rss_feeds: st.rss_feeds.clone(),
            settings: st.settings.clone(),
        }
    }

    pub fn snapshot_json(&self) -> String {
        serde_json::to_string(&self.snapshot()).unwrap_or_else(|_| "{}".to_string())
    }
}

fn mask_token(tok: &str) -> String {
    if tok.len() <= 4 {
        "****".to_string()
    } else {
        format!("****{}", &tok[tok.len() - 4..])
    }
}

fn url_str_is_network(u: &str) -> bool {
    !u.contains("example.com")
        && (u.starts_with("http://")
            || u.starts_with("https://")
            || u.starts_with("ftp://")
            || u.starts_with("ftps://"))
}

fn build_initial_segments(conns: u32) -> Vec<SegmentDto> {
    (0..conns)
        .map(|idx| SegmentDto {
            index: idx,
            start: 0,
            pos: 0,
            end: 0,
            active: false,
            speed_bps: 0,
        })
        .collect()
}

pub fn infer_filename(url: &str) -> String {
    crate::extractors::infer_smart_filename(url)
}

fn infer_category(file_name: &str) -> String {
    let ext = Path::new(file_name)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_ascii_lowercase();
    match ext.as_str() {
        "mp4" | "mkv" | "webm" | "avi" | "mov" | "flv" => "Video".to_string(),
        "mp3" | "flac" | "wav" | "m4a" | "ogg" | "opus" => "Music".to_string(),
        "zip" | "rar" | "7z" | "tar" | "gz" | "xz" | "bz2" | "zst" => "Compressed".to_string(),
        "pdf" | "doc" | "docx" | "xls" | "xlsx" | "ppt" | "pptx" | "txt" | "epub" => {
            "Documents".to_string()
        }
        "apk" | "xapk" | "apks" | "exe" | "msi" | "dmg" | "deb" | "rpm" | "iso" => {
            "Programs".to_string()
        }
        _ => "General".to_string(),
    }
}

fn extract_magnet_display_name(src: &str) -> Option<String> {
    Url::parse(src).ok().and_then(|u| {
        u.query_pairs()
            .find(|(k, _)| k == "dn")
            .map(|(_, v)| v.into_owned())
    })
}

fn extract_magnet_btih(src: &str) -> Option<String> {
    Url::parse(src).ok().and_then(|u| {
        u.query_pairs()
            .find(|(k, v)| k == "xt" && v.starts_with("urn:btih:"))
            .map(|(_, v)| v.trim_start_matches("urn:btih:").to_string())
    })
}

fn default_queues() -> Vec<QueueDto> {
    vec![
        QueueDto {
            name: "Main".to_string(),
            running: true,
            max_concurrent: 4,
            start_time: None,
            stop_time: None,
            days_of_week: vec![1, 2, 3, 4, 5, 6, 7],
            stop_when_done: false,
            item_ids: Vec::new(),
        },
        QueueDto {
            name: "Overnight".to_string(),
            running: false,
            max_concurrent: 6,
            start_time: Some("01:00".to_string()),
            stop_time: Some("07:00".to_string()),
            days_of_week: vec![1, 2, 3, 4, 5, 6, 7],
            stop_when_done: true,
            item_ids: Vec::new(),
        },
    ]
}

fn update_resume_state_url(save_path: &str, new_url: &str) {
    let dest = std::path::PathBuf::from(save_path);
    let st_path = mdm_engine::state::state_path(&dest);
    if st_path.exists() {
        if let Ok(mut saved) = mdm_engine::state::ResumeState::load(&st_path) {
            if let Ok(parsed) = reqwest::Url::parse(new_url) {
                if saved.url != parsed {
                    saved.validators.etag = None;
                }
                saved.url = parsed;
                let _ = saved.write(&st_path);
            }
        }
    }
}
