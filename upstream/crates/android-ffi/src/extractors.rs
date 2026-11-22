use std::collections::HashSet;
use std::path::Path;
use std::time::Duration;

use serde::{Deserialize, Serialize};
use url::Url;

pub const DEFAULT_OTA_URL: &str =
    "https://raw.githubusercontent.com/djsusmith/hyperstream-android/main/extractor_rules.json";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractorRules {
    pub version: String,
    pub ota_url: String,
    pub youtube_android_version: String,
    pub youtube_android_sdk: u32,
    pub youtube_android_ua: String,
    pub youtube_ios_version: String,
    pub youtube_ios_ua: String,
    pub tiktok_api_url: String,
    pub cobalt_instances: Vec<String>,
}

impl Default for ExtractorRules {
    fn default() -> Self {
        Self {
            version: "2026.11.15-r2".to_string(),
            ota_url: DEFAULT_OTA_URL.to_string(),
            youtube_android_version: "20.03.38".to_string(),
            youtube_android_sdk: 35,
            youtube_android_ua: "com.google.android.youtube/20.03.38 (Linux; U; Android 15) gzip"
                .to_string(),
            youtube_ios_version: "20.03.02".to_string(),
            youtube_ios_ua:
                "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
                    .to_string(),
            tiktok_api_url: "https://www.tikwm.com/api/".to_string(),
            cobalt_instances: vec![
                "https://api.cobalt.tools/".to_string(),
                "https://cobalt-api.kwiatekmiki.com/".to_string(),
            ],
        }
    }
}

impl ExtractorRules {
    pub fn load_or_default(storage_dir: &Path) -> Self {
        let path = storage_dir.join("extractor_rules.json");
        std::fs::read_to_string(&path)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    }

    pub fn save(&self, storage_dir: &Path) {
        let path = storage_dir.join("extractor_rules.json");
        if let Ok(bytes) = serde_json::to_vec_pretty(self) {
            let _ = craft_atomic::atomic_write(&path, &bytes);
        }
    }
}

pub async fn update_extractor_rules(storage_dir: &Path) -> Result<ExtractorRules, String> {
    let current = ExtractorRules::load_or_default(storage_dir);
    let client = mdm_engine::tls::configure(
        reqwest::Client::builder().timeout(Duration::from_secs(12)),
    )
    .build()
    .map_err(|e| format!("TLS client error: {e}"))?;

    match client.get(&current.ota_url).send().await {
        Ok(resp) if resp.status().is_success() => {
            if let Ok(bytes) = resp.bytes().await
                && let Ok(updated) = serde_json::from_slice::<ExtractorRules>(&bytes)
            {
                updated.save(storage_dir);
                return Ok(updated);
            }
            current.save(storage_dir);
            Ok(current)
        }
        _ => {
            current.save(storage_dir);
            Ok(current)
        }
    }
}

pub fn detect_platform(url: &str) -> &'static str {
    let lower = url.to_ascii_lowercase();
    if lower.contains("youtube.com")
        || lower.contains("youtu.be")
        || lower.contains("youtube-nocookie.com")
    {
        "YouTube"
    } else if lower.contains("tiktok.com") {
        "TikTok"
    } else if lower.contains("instagram.com") || lower.contains("instagr.am") {
        "Instagram"
    } else if lower.contains("facebook.com")
        || lower.contains("fb.watch")
        || lower.contains("fb.com")
    {
        "Facebook"
    } else if lower.contains("twitter.com") || lower.contains("x.com") {
        "X"
    } else if lower.contains("vimeo.com")
        || lower.contains("dailymotion.com")
        || lower.contains("twitch.tv")
        || lower.contains("reddit.com")
    {
        "SocialVideo"
    } else {
        "Direct"
    }
}

pub fn extract_youtube_video_id(url: &str) -> Option<String> {
    let parsed = Url::parse(url).ok()?;
    let host = parsed.host_str()?.to_ascii_lowercase();
    if host == "youtu.be" {
        return parsed
            .path_segments()?
            .next()
            .filter(|s| !s.is_empty())
            .map(|s| s.to_string());
    }
    if host.ends_with("youtube.com") || host.ends_with("youtube-nocookie.com") {
        if let Some((_, v)) = parsed.query_pairs().find(|(k, _)| k == "v")
            && !v.is_empty()
        {
            return Some(v.into_owned());
        }
        let segments: Vec<&str> = parsed.path_segments()?.collect();
        if segments.len() >= 2
            && matches!(segments[0], "shorts" | "embed" | "live" | "v")
            && !segments[1].is_empty()
        {
            return Some(segments[1].to_string());
        }
    }
    None
}

pub fn sanitize_filename(raw: &str) -> String {
    let cleaned: String = raw
        .chars()
        .map(|c| match c {
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' | '\n' | '\r' | '\t' => '_',
            _ => c,
        })
        .collect();
    let trimmed = cleaned.trim().trim_matches('.');
    if trimmed.is_empty() {
        "HyperStream_Media".to_string()
    } else {
        trimmed.chars().take(75).collect()
    }
}

pub fn infer_smart_filename(url: &str) -> String {
    if let Some(vid) = extract_youtube_video_id(url) {
        return format!("YouTube_{vid}.mp4");
    }
    let platform = detect_platform(url);
    if platform != "Direct" {
        let slug = Url::parse(url)
            .ok()
            .and_then(|u| {
                u.path_segments()
                    .and_then(|s| s.filter(|p| !p.is_empty()).next_back())
                    .map(sanitize_filename)
            })
            .unwrap_or_else(|| "Media".to_string());
        return format!("{platform}_{slug}.mp4");
    }
    Url::parse(url)
        .ok()
        .and_then(|u| {
            u.path_segments()
                .and_then(|mut s| s.next_back())
                .filter(|s| !s.is_empty() && *s != "watch" && *s != "videoplayback")
                .map(|s| percent_decode_str(s))
        })
        .unwrap_or_else(|| "download.bin".to_string())
}

pub const KNOWN_FILE_EXTENSIONS: &[&str] = &[
    "apk", "xapk", "apks", "aab", "obb", "dex", "jar",
    "mkv", "mp4", "webm", "avi", "mov", "flv", "m4v", "3gp", "ts", "m2ts", "vob", "ogv", "m3u8", "mpd",
    "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "aiff",
    "zip", "rar", "7z", "tar", "gz", "tgz", "xz", "txz", "bz2", "tbz2", "zst", "lz4", "cab",
    "iso", "img", "dmg", "exe", "msi", "deb", "rpm", "appimage", "bin", "dat", "rom", "nsp", "xci",
    "pdf", "epub", "mobi", "azw3", "djvu", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf",
    "txt", "csv", "tsv", "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "log", "md", "html", "htm",
    "jpg", "jpeg", "png", "gif", "webp", "avif", "svg", "bmp", "ico", "tiff", "heic",
    "torrent", "srt", "ass", "vtt", "sub", "ttf", "otf", "woff", "woff2", "sql", "db", "sqlite",
];

pub fn known_extension_of(name: &str) -> Option<String> {
    let trimmed = name
        .trim()
        .trim_matches(|c| c == '"' || c == '\'' || c == '/' || c == '\\');
    let (_, ext) = trimmed.rsplit_once('.')?;
    let lower = ext.to_ascii_lowercase();
    if KNOWN_FILE_EXTENSIONS.contains(&lower.as_str()) {
        Some(lower)
    } else {
        None
    }
}

pub fn has_known_extension(name: &str) -> bool {
    known_extension_of(name).is_some()
}

pub fn extension_from_mime(mime: &str) -> Option<&'static str> {
    let clean = mime
        .split(';')
        .next()
        .unwrap_or(mime)
        .trim()
        .to_ascii_lowercase();
    match clean.as_str() {
        "application/vnd.android.package-archive" => Some("apk"),
        "application/vnd.android.xapk" | "application/xapk-package-archive" => Some("xapk"),
        "application/x-apks" => Some("apks"),
        "video/x-matroska" | "application/x-matroska" => Some("mkv"),
        "video/mp4" | "application/mp4" => Some("mp4"),
        "video/webm" => Some("webm"),
        "video/x-msvideo" | "video/avi" => Some("avi"),
        "video/quicktime" => Some("mov"),
        "video/x-flv" => Some("flv"),
        "video/x-m4v" => Some("m4v"),
        "video/3gpp" => Some("3gp"),
        "video/mp2t" => Some("ts"),
        "application/vnd.apple.mpegurl" | "application/x-mpegurl" => Some("m3u8"),
        "application/dash+xml" => Some("mpd"),
        "audio/mpeg" | "audio/mp3" => Some("mp3"),
        "audio/mp4" | "audio/x-m4a" | "audio/m4a" => Some("m4a"),
        "audio/aac" => Some("aac"),
        "audio/flac" | "audio/x-flac" => Some("flac"),
        "audio/wav" | "audio/x-wav" | "audio/wave" => Some("wav"),
        "audio/ogg" | "application/ogg" => Some("ogg"),
        "audio/opus" => Some("opus"),
        "application/zip" | "application/x-zip-compressed" => Some("zip"),
        "application/vnd.rar" | "application/x-rar-compressed" => Some("rar"),
        "application/x-7z-compressed" => Some("7z"),
        "application/x-tar" => Some("tar"),
        "application/gzip" | "application/x-gzip" => Some("gz"),
        "application/x-xz" => Some("xz"),
        "application/x-bzip2" => Some("bz2"),
        "application/zstd" => Some("zst"),
        "application/x-iso9660-image" | "application/octet-stream+iso" => Some("iso"),
        "application/x-apple-diskimage" => Some("dmg"),
        "application/x-msdownload" | "application/x-dosexec" | "application/vnd.microsoft.portable-executable" => Some("exe"),
        "application/x-msi" => Some("msi"),
        "application/vnd.debian.binary-package" | "application/x-deb" => Some("deb"),
        "application/x-rpm" => Some("rpm"),
        "application/java-archive" => Some("jar"),
        "application/pdf" => Some("pdf"),
        "application/epub+zip" => Some("epub"),
        "application/x-mobipocket-ebook" => Some("mobi"),
        "application/msword" => Some("doc"),
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" => Some("docx"),
        "application/vnd.ms-excel" => Some("xls"),
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" => Some("xlsx"),
        "application/vnd.ms-powerpoint" => Some("ppt"),
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" => Some("pptx"),
        "application/rtf" => Some("rtf"),
        "text/plain" => Some("txt"),
        "text/csv" => Some("csv"),
        "application/json" => Some("json"),
        "application/xml" | "text/xml" => Some("xml"),
        "text/html" | "application/xhtml+xml" => Some("html"),
        "image/jpeg" => Some("jpg"),
        "image/png" => Some("png"),
        "image/gif" => Some("gif"),
        "image/webp" => Some("webp"),
        "image/avif" => Some("avif"),
        "image/svg+xml" => Some("svg"),
        "application/x-bittorrent" => Some("torrent"),
        "application/x-subrip" => Some("srt"),
        "text/vtt" => Some("vtt"),
        _ => None,
    }
}

fn percent_decode_str(input: &str) -> String {
    url::form_urlencoded::parse(input.as_bytes())
        .map(|(k, v)| {
            if v.is_empty() {
                k.into_owned()
            } else {
                format!("{k}={v}")
            }
        })
        .collect::<Vec<_>>()
        .join("&")
}

pub fn parse_content_disposition_filename(header: &str) -> Option<String> {
    for part in header.split(';') {
        let p = part.trim();
        let lower = p.to_ascii_lowercase();
        if lower.starts_with("filename*=") {
            let val = p["filename*=".len()..].trim().trim_matches('"').trim_matches('\'');
            let encoded = val
                .split_once("''")
                .map(|(_, rhs)| rhs)
                .unwrap_or(val);
            let decoded = percent_decode_str(encoded);
            let clean = decoded
                .rsplit(['/', '\\'])
                .next()
                .unwrap_or(&decoded)
                .trim()
                .to_string();
            if !clean.is_empty() {
                return Some(clean);
            }
        }
    }
    for part in header.split(';') {
        let p = part.trim();
        let lower = p.to_ascii_lowercase();
        if lower.starts_with("filename=") {
            let val = p["filename=".len()..]
                .trim()
                .trim_matches('"')
                .trim_matches('\'');
            let decoded = percent_decode_str(val);
            let clean = decoded
                .rsplit(['/', '\\'])
                .next()
                .unwrap_or(&decoded)
                .trim()
                .to_string();
            if !clean.is_empty() {
                return Some(clean);
            }
        }
    }
    None
}

pub fn extract_known_ext_from_url(raw_url: &str) -> Option<String> {
    let parsed = Url::parse(raw_url).ok()?;
    for (_, v) in parsed.query_pairs() {
        if let Some(ext) = known_extension_of(&v) {
            return Some(ext);
        }
    }
    let last_seg = parsed
        .path_segments()
        .and_then(|s| s.filter(|p| !p.is_empty()).next_back())?;
    known_extension_of(last_seg)
}

pub fn ensure_filename_has_known_extension(
    base_name: &str,
    url: &str,
    final_url: Option<&str>,
    content_disp: Option<&str>,
    content_type: Option<&str>,
) -> String {
    let mut candidate = base_name.trim().to_string();
    if let Some(cd) = content_disp.and_then(parse_content_disposition_filename) {
        if has_known_extension(&cd) {
            if candidate.is_empty()
                || candidate == "download.bin"
                || candidate == "download_file"
                || !has_known_extension(&candidate)
            {
                return cd;
            }
        } else if candidate.is_empty() || candidate == "download.bin" || candidate == "download_file" {
            candidate = cd;
        }
    }

    if has_known_extension(&candidate) {
        return candidate;
    }

    let resolved_ext = content_disp
        .and_then(parse_content_disposition_filename)
        .and_then(|n| known_extension_of(&n))
        .or_else(|| final_url.and_then(extract_known_ext_from_url))
        .or_else(|| extract_known_ext_from_url(url))
        .or_else(|| content_type.and_then(extension_from_mime).map(|s| s.to_string()));

    if candidate.is_empty() || candidate == "download.bin" || candidate == "download_file" {
        candidate = final_url
            .map(infer_smart_filename)
            .filter(|n| n != "download.bin")
            .unwrap_or_else(|| infer_smart_filename(url));
    }

    if has_known_extension(&candidate) {
        return candidate;
    }

    if let Some(ext) = resolved_ext {
        let stem = candidate.trim_end_matches('.');
        format!("{stem}.{ext}")
    } else {
        candidate
    }
}

pub fn build_mp3_variants(
    title: &str,
    audio_url: &str,
    approx_size: u64,
    user_agent: &str,
) -> Vec<serde_json::Value> {
    let safe_title = sanitize_filename(title);
    let bitrates = [
        (320u32, "MP3 320 kbps (Studio Quality)", 1.0f64),
        (192u32, "MP3 192 kbps (High Quality)", 0.65f64),
        (128u32, "MP3 128 kbps (Compact Audio)", 0.45f64),
    ];
    bitrates
        .iter()
        .map(|(br, label, scale)| {
            let scaled_size = if approx_size > 0 {
                ((approx_size as f64) * scale) as u64
            } else {
                0
            };
            serde_json::json!({
                "itag": *br as u64,
                "label": *label,
                "quality": format!("{br}kbps"),
                "format_kind": "mp3",
                "bitrate_kbps": *br,
                "watermark_free": true,
                "ext": "mp3",
                "category": "Music",
                "file_name": format!("{safe_title} ({br}kbps).mp3"),
                "size_bytes": scaled_size,
                "url": audio_url,
                "user_agent": user_agent
            })
        })
        .collect()
}

pub struct ResolvedMediaStream {
    pub file_name: String,
    pub category: String,
    pub stream_url: String,
    pub size_bytes: u64,
    pub user_agent: String,
}

pub async fn resolve_best_media_stream(
    url: &str,
    rules: &ExtractorRules,
) -> Result<ResolvedMediaStream, String> {
    let parsed = Url::parse(url).ok();
    let requested_itag: Option<u64> = parsed
        .as_ref()
        .and_then(|u| u.fragment().map(|f| f.to_string()))
        .and_then(|frag| {
            frag.split('&').find_map(|kv| {
                kv.strip_prefix("itag=")
                    .and_then(|s| s.parse::<u64>().ok())
            })
        });

    let probe = probe_media_url(url, rules).await?;
    let streams = probe
        .get("streams")
        .and_then(|s| s.as_array())
        .ok_or_else(|| "No playable streams found".to_string())?;
    if streams.is_empty() {
        return Err("No downloadable streams returned by source".to_string());
    }

    let chosen = if let Some(target_itag) = requested_itag {
        streams
            .iter()
            .find(|s| s.get("itag").and_then(|v| v.as_u64()) == Some(target_itag))
            .unwrap_or(&streams[0])
    } else {
        streams
            .iter()
            .find(|s| {
                s.get("format_kind").and_then(|v| v.as_str()) == Some("video")
                    && s.get("watermark_free").and_then(|v| v.as_bool()) == Some(true)
            })
            .or_else(|| {
                streams
                    .iter()
                    .find(|s| s.get("itag").and_then(|v| v.as_u64()) == Some(18))
            })
            .unwrap_or(&streams[0])
    };

    Ok(ResolvedMediaStream {
        file_name: chosen
            .get("file_name")
            .and_then(|v| v.as_str())
            .unwrap_or("video.mp4")
            .to_string(),
        category: chosen
            .get("category")
            .and_then(|v| v.as_str())
            .unwrap_or("Video")
            .to_string(),
        stream_url: chosen
            .get("url")
            .and_then(|v| v.as_str())
            .ok_or_else(|| "Missing stream URL".to_string())?
            .to_string(),
        size_bytes: chosen
            .get("size_bytes")
            .and_then(|v| v.as_u64())
            .unwrap_or(0),
        user_agent: chosen
            .get("user_agent")
            .and_then(|v| v.as_str())
            .unwrap_or(&rules.youtube_android_ua)
            .to_string(),
    })
}

pub async fn probe_media_url(
    url: &str,
    rules: &ExtractorRules,
) -> Result<serde_json::Value, String> {
    let clean_url = url.split('#').next().unwrap_or(url);
    let platform = detect_platform(clean_url);
    let client = mdm_engine::tls::configure(
        reqwest::Client::builder().timeout(Duration::from_secs(15)),
    )
    .build()
    .map_err(|e| format!("TLS client error: {e}"))?;

    if let Some(video_id) = extract_youtube_video_id(clean_url) {
        return probe_youtube(&client, &video_id, rules).await;
    }

    if platform == "TikTok" {
        if let Ok(val) = probe_tiktok(&client, clean_url, rules).await {
            return Ok(val);
        }
    }

    if matches!(platform, "Instagram" | "Facebook" | "X" | "SocialVideo" | "TikTok") {
        if let Ok(val) = probe_social_embed_or_cobalt(&client, clean_url, platform, rules).await {
            return Ok(val);
        }
    }

    probe_direct_url(&client, clean_url, platform, rules).await
}

pub const VISIONOS_UA: &str = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15";

async fn fetch_youtube_visitor_data(client: &reqwest::Client) -> String {
    let body = serde_json::json!({
        "context": {
            "client": {
                "clientName": "VISIONOS",
                "clientVersion": "1.02"
            }
        }
    });
    let Ok(resp) = client
        .post("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
        .header(reqwest::header::USER_AGENT, VISIONOS_UA)
        .header(reqwest::header::CONTENT_TYPE, "application/json")
        .body(body.to_string())
        .send()
        .await
    else {
        return String::new();
    };
    let Ok(bytes) = resp.bytes().await else {
        return String::new();
    };
    serde_json::from_slice::<serde_json::Value>(&bytes)
        .ok()
        .and_then(|v| {
            v.pointer("/responseContext/visitorData")
                .and_then(|s| s.as_str())
                .map(str::to_owned)
        })
        .unwrap_or_default()
}

async fn probe_youtube(
    client: &reqwest::Client,
    video_id: &str,
    rules: &ExtractorRules,
) -> Result<serde_json::Value, String> {
    let visitor_data = fetch_youtube_visitor_data(client).await;
    let pico_vr_ua = "com.google.android.apps.youtube.vr.pico/1.73.21 (Linux; U; Android 10; A8110 Build/QKQ1) gzip";

    // Primary: visionOS client (from grapedjuice/ytm-rs) returns unciphered adaptive URLs
    // that serve all byte ranges (0..100%, HTTP 206) without requiring a PO token or JS cipher.
    let clients: [(&str, Option<(&str, &str)>, serde_json::Value); 4] = [
        (
            VISIONOS_UA,
            Some(("101", "1.02")),
            serde_json::json!({
                "videoId": video_id,
                "contentCheckOk": true,
                "racyCheckOk": true,
                "context": {
                    "client": {
                        "clientName": "VISIONOS",
                        "clientVersion": "1.02",
                        "deviceMake": "Apple",
                        "deviceModel": "RealityDevice17,1",
                        "userAgent": VISIONOS_UA,
                        "osName": "visionOS",
                        "osVersion": "26.5.23O471",
                        "hl": "en",
                        "gl": "US",
                        "visitorData": visitor_data
                    }
                }
            }),
        ),
        (
            pico_vr_ua,
            Some(("28", "1.73.21")),
            serde_json::json!({
                "videoId": video_id,
                "contentCheckOk": true,
                "racyCheckOk": true,
                "context": {
                    "client": {
                        "clientName": "ANDROID_VR",
                        "clientVersion": "1.73.21",
                        "deviceMake": "Pico",
                        "deviceModel": "A8110",
                        "osName": "Android",
                        "osVersion": "10",
                        "androidSdkVersion": 29,
                        "hl": "en",
                        "gl": "US",
                        "visitorData": visitor_data
                    }
                }
            }),
        ),
        (
            rules.youtube_android_ua.as_str(),
            None,
            serde_json::json!({
                "videoId": video_id,
                "contentCheckOk": true,
                "racyCheckOk": true,
                "context": {
                    "client": {
                        "clientName": "ANDROID",
                        "clientVersion": rules.youtube_android_version,
                        "androidSdkVersion": rules.youtube_android_sdk,
                        "osName": "Android",
                        "osVersion": "15",
                        "hl": "en",
                        "gl": "US"
                    }
                }
            }),
        ),
        (
            rules.youtube_ios_ua.as_str(),
            None,
            serde_json::json!({
                "videoId": video_id,
                "contentCheckOk": true,
                "racyCheckOk": true,
                "context": {
                    "client": {
                        "clientName": "IOS",
                        "clientVersion": rules.youtube_ios_version,
                        "deviceMake": "Apple",
                        "deviceModel": "iPhone16,2",
                        "osName": "iPhone",
                        "osVersion": "18.2.1.22C161",
                        "hl": "en",
                        "gl": "US"
                    }
                }
            }),
        ),
    ];

    for (ua, client_hdr, payload) in clients {
        let Ok(body_bytes) = serde_json::to_vec(&payload) else {
            continue;
        };
        let mut req_builder = client
            .post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header(reqwest::header::USER_AGENT, ua)
            .header(reqwest::header::CONTENT_TYPE, "application/json")
            .header(reqwest::header::ORIGIN, "https://www.youtube.com");
        if let Some((c_name, c_ver)) = client_hdr {
            req_builder = req_builder
                .header("X-YouTube-Client-Name", c_name)
                .header("X-YouTube-Client-Version", c_ver);
        }
        if !visitor_data.is_empty() {
            req_builder = req_builder.header("X-Goog-Visitor-Id", &visitor_data);
        }
        let resp = req_builder.body(body_bytes).send().await;
        let Ok(resp) = resp else { continue };
        let Ok(resp_bytes) = resp.bytes().await else {
            continue;
        };
        let Ok(data) = serde_json::from_slice::<serde_json::Value>(&resp_bytes) else {
            continue;
        };
        let status = data
            .pointer("/playabilityStatus/status")
            .and_then(|v| v.as_str())
            .unwrap_or("");
        if status != "OK" {
            continue;
        }
        let raw_title = data
            .pointer("/videoDetails/title")
            .and_then(|v| v.as_str())
            .unwrap_or(video_id);
        let safe_title = sanitize_filename(raw_title);
        let author = data
            .pointer("/videoDetails/author")
            .and_then(|v| v.as_str())
            .unwrap_or("YouTube");

        let mut out_streams = Vec::new();
        let mut seen_labels = HashSet::new();
        let mut best_audio_url: Option<(String, u64)> = None;

        if let Some(fmts) = data.pointer("/streamingData/formats").and_then(|v| v.as_array()) {
            for f in fmts {
                let Some(s_url) = f.get("url").and_then(|v| v.as_str()) else {
                    continue;
                };
                let itag = f.get("itag").and_then(|v| v.as_u64()).unwrap_or(18);
                let q = f
                    .get("qualityLabel")
                    .and_then(|v| v.as_str())
                    .unwrap_or("360p");
                let clen = f
                    .get("contentLength")
                    .and_then(|v| v.as_str())
                    .and_then(|s| s.parse::<u64>().ok())
                    .unwrap_or(0);
                let label = format!("{q} MP4 (Video + Audio • Fast)");
                if seen_labels.insert(label.clone()) {
                    out_streams.push(serde_json::json!({
                        "itag": itag,
                        "label": label,
                        "quality": q,
                        "format_kind": "video",
                        "bitrate_kbps": 0,
                        "watermark_free": true,
                        "ext": "mp4",
                        "category": "Video",
                        "file_name": format!("{safe_title}_{q}.mp4"),
                        "size_bytes": clen,
                        "url": s_url,
                        "user_agent": ua
                    }));
                }
            }
        }

        if let Some(afmts) = data
            .pointer("/streamingData/adaptiveFormats")
            .and_then(|v| v.as_array())
        {
            // First pass: locate the primary AAC audio/mp4 stream (itag 140) so video-only MP4
            // streams can automatically download and mux their companion audio track.
            for f in afmts {
                let Some(s_url) = f.get("url").and_then(|v| v.as_str()) else {
                    continue;
                };
                let itag = f.get("itag").and_then(|v| v.as_u64()).unwrap_or(0);
                let mime = f.get("mimeType").and_then(|v| v.as_str()).unwrap_or("");
                let clen = f
                    .get("contentLength")
                    .and_then(|v| v.as_str())
                    .and_then(|s| s.parse::<u64>().ok())
                    .unwrap_or(0);
                if itag == 140 || mime.starts_with("audio/mp4") {
                    best_audio_url = Some((s_url.to_string(), clen));
                    if itag == 140 {
                        break;
                    }
                } else if best_audio_url.is_none() && mime.starts_with("audio/webm") {
                    best_audio_url = Some((s_url.to_string(), clen));
                }
            }

            let companion_m4a = best_audio_url
                .as_ref()
                .filter(|(u, _)| u.contains("mime=audio%2Fmp4") || u.contains("itag=140"))
                .cloned();

            for f in afmts {
                let Some(s_url) = f.get("url").and_then(|v| v.as_str()) else {
                    continue;
                };
                let itag = f.get("itag").and_then(|v| v.as_u64()).unwrap_or(0);
                let mime = f.get("mimeType").and_then(|v| v.as_str()).unwrap_or("");
                let clen = f
                    .get("contentLength")
                    .and_then(|v| v.as_str())
                    .and_then(|s| s.parse::<u64>().ok())
                    .unwrap_or(0);

                if mime.starts_with("video/mp4") {
                    let q = f
                        .get("qualityLabel")
                        .and_then(|v| v.as_str())
                        .unwrap_or("HD");
                    let codec = if mime.contains("avc1") {
                        "H.264"
                    } else if mime.contains("av01") {
                        "AV1 HDR"
                    } else {
                        "MP4"
                    };
                    let label = format!("{q} MP4 ({codec})");
                    if seen_labels.insert(label.clone()) {
                        let (combined_url, combined_size) = if let Some((ref aud_url, aud_sz)) = companion_m4a {
                            let encoded_aud: String = url::form_urlencoded::byte_serialize(aud_url.as_bytes()).collect();
                            (
                                format!("{s_url}#yt_audio={encoded_aud}"),
                                clen.saturating_add(aud_sz),
                            )
                        } else {
                            (s_url.to_string(), clen)
                        };
                        out_streams.push(serde_json::json!({
                            "itag": itag,
                            "label": label,
                            "quality": q,
                            "format_kind": "video",
                            "bitrate_kbps": 0,
                            "watermark_free": true,
                            "ext": "mp4",
                            "category": "Video",
                            "file_name": format!("{safe_title}_{q}.mp4"),
                            "size_bytes": combined_size,
                            "url": combined_url,
                            "user_agent": ua
                        }));
                    }
                }
            }
        }

        if let Some((audio_url, audio_clen)) = best_audio_url.or_else(|| {
            out_streams.first().and_then(|s| {
                Some((
                    s.get("url")?.as_str()?.to_string(),
                    s.get("size_bytes")?.as_u64().unwrap_or(0),
                ))
            })
        }) {
            out_streams.extend(build_mp3_variants(raw_title, &audio_url, audio_clen, ua));
        }

        if !out_streams.is_empty() {
            return Ok(serde_json::json!({
                "ok": true,
                "platform": "YouTube",
                "title": raw_title,
                "author": author,
                "watermark_free": true,
                "extractor_version": rules.version,
                "streams": out_streams
            }));
        }
    }
    Err("Unable to extract streams for this YouTube video (try tapping Update Extractors)".to_string())
}

async fn probe_tiktok(
    client: &reqwest::Client,
    url: &str,
    rules: &ExtractorRules,
) -> Result<serde_json::Value, String> {
    let encoded: String = url::form_urlencoded::Serializer::new(String::new())
        .append_pair("url", url)
        .append_pair("hd", "1")
        .finish();
    let api_url = format!("{}?{}", rules.tiktok_api_url.trim_end_matches('?'), encoded);
    let ua = "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    let resp = client
        .get(&api_url)
        .header(reqwest::header::USER_AGENT, ua)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let bytes = resp.bytes().await.map_err(|e| e.to_string())?;
    let json: serde_json::Value = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
    let data = json.get("data").ok_or_else(|| "No TikTok data".to_string())?;

    let raw_title = data
        .get("title")
        .and_then(|v| v.as_str())
        .filter(|s| !s.is_empty())
        .unwrap_or("TikTok Video");
    let safe_title = sanitize_filename(raw_title);
    let author = data
        .pointer("/author/unique_id")
        .or_else(|| data.pointer("/author/nickname"))
        .and_then(|v| v.as_str())
        .unwrap_or("TikTok Creator");

    let mut streams = Vec::new();
    if let Some(hd_url) = data.get("hdplay").and_then(|v| v.as_str()).filter(|s| !s.is_empty()) {
        let hd_size = data.get("hd_size").and_then(|v| v.as_u64()).unwrap_or(0);
        streams.push(serde_json::json!({
            "itag": 1080,
            "label": "1080p Full HD MP4 (No Watermark)",
            "quality": "1080p No-WM",
            "format_kind": "video",
            "bitrate_kbps": 0,
            "watermark_free": true,
            "ext": "mp4",
            "category": "Video",
            "file_name": format!("{safe_title}_NoWatermark_HD.mp4"),
            "size_bytes": hd_size,
            "url": hd_url,
            "user_agent": ua
        }));
    }
    if let Some(play_url) = data.get("play").and_then(|v| v.as_str()).filter(|s| !s.is_empty()) {
        let sz = data.get("size").and_then(|v| v.as_u64()).unwrap_or(0);
        streams.push(serde_json::json!({
            "itag": 720,
            "label": "720p HD MP4 (No Watermark)",
            "quality": "720p No-WM",
            "format_kind": "video",
            "bitrate_kbps": 0,
            "watermark_free": true,
            "ext": "mp4",
            "category": "Video",
            "file_name": format!("{safe_title}_NoWatermark.mp4"),
            "size_bytes": sz,
            "url": play_url,
            "user_agent": ua
        }));
    }
    let music_url = data
        .get("music")
        .and_then(|v| v.as_str())
        .filter(|s| !s.is_empty())
        .or_else(|| data.get("play").and_then(|v| v.as_str()));
    if let Some(m_url) = music_url {
        streams.extend(build_mp3_variants(raw_title, m_url, 4_500_000, ua));
    }
    if streams.is_empty() {
        return Err("No TikTok streams found".to_string());
    }
    Ok(serde_json::json!({
        "ok": true,
        "platform": "TikTok",
        "title": raw_title,
        "author": format!("@{author}"),
        "watermark_free": true,
        "extractor_version": rules.version,
        "streams": streams
    }))
}

async fn probe_social_embed_or_cobalt(
    client: &reqwest::Client,
    url: &str,
    platform: &str,
    rules: &ExtractorRules,
) -> Result<serde_json::Value, String> {
    let ua = "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    // 1. Try Cobalt API instances for clean no-watermark social extraction
    for instance in &rules.cobalt_instances {
        let payload = serde_json::json!({
            "url": url,
            "videoQuality": "1080",
            "audioFormat": "mp3",
            "audioBitrate": "320"
        });
        if let Ok(body_bytes) = serde_json::to_vec(&payload)
            && let Ok(resp) = client
                .post(instance)
                .header(reqwest::header::ACCEPT, "application/json")
                .header(reqwest::header::CONTENT_TYPE, "application/json")
                .header(reqwest::header::USER_AGENT, ua)
                .body(body_bytes)
                .send()
                .await
            && let Ok(bytes) = resp.bytes().await
            && let Ok(json) = serde_json::from_slice::<serde_json::Value>(&bytes)
            && let Some(dl_url) = json.get("url").and_then(|v| v.as_str())
        {
            let title = json
                .get("filename")
                .and_then(|v| v.as_str())
                .map(|s| s.trim_end_matches(".mp4"))
                .unwrap_or(platform);
            let safe_title = sanitize_filename(title);
            let mut streams = vec![serde_json::json!({
                "itag": 1080,
                "label": format!("1080p HD MP4 ({platform} • No Watermark)"),
                "quality": "1080p HD",
                "format_kind": "video",
                "bitrate_kbps": 0,
                "watermark_free": true,
                "ext": "mp4",
                "category": "Video",
                "file_name": format!("{safe_title}_HD.mp4"),
                "size_bytes": 0,
                "url": dl_url,
                "user_agent": ua
            })];
            streams.extend(build_mp3_variants(title, dl_url, 0, ua));
            return Ok(serde_json::json!({
                "ok": true,
                "platform": platform,
                "title": title,
                "author": platform,
                "watermark_free": true,
                "extractor_version": rules.version,
                "streams": streams
            }));
        }
    }

    // 2. Scrape OpenGraph / embedded JSON video URLs directly from the social page.
    // Desktop Chrome + Sec-Fetch-Mode: navigate prevents Facebook from returning a 3 KB fb:// app redirect stub.
    let desktop_ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    let resp = client
        .get(url)
        .header(reqwest::header::USER_AGENT, desktop_ua)
        .header(reqwest::header::ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header(reqwest::header::ACCEPT_LANGUAGE, "en-US,en;q=0.9")
        .header("Sec-Fetch-Mode", "navigate")
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let html_bytes = resp.bytes().await.map_err(|e| e.to_string())?;
    let html = String::from_utf8_lossy(&html_bytes);

    let extracted_video_url = extract_meta_or_json_url(
        &html,
        &[
            r#""browser_native_hd_url":""#,
            r#""playable_url_quality_hd":""#,
            r#""hd_src":""#,
            r#"property="og:video:secure_url" content=""#,
            r#"property="og:video" content=""#,
            r#""browser_native_sd_url":""#,
            r#""playable_url":""#,
            r#""video_url":""#,
            r#""sd_src":""#,
        ],
    );

    if let Some(v_url) = extracted_video_url {
        let title = extract_meta_or_json_url(&html, &[r#"property="og:title" content=""#])
            .unwrap_or_else(|| format!("{platform} Video"));
        let safe_title = sanitize_filename(&title);
        let mut streams = vec![serde_json::json!({
            "itag": 1080,
            "label": format!("HD MP4 ({platform} • No Watermark)"),
            "quality": "HD MP4",
            "format_kind": "video",
            "bitrate_kbps": 0,
            "watermark_free": true,
            "ext": "mp4",
            "category": "Video",
            "file_name": format!("{safe_title}.mp4"),
            "size_bytes": 0,
            "url": v_url,
            "user_agent": ua
        })];
        streams.extend(build_mp3_variants(&title, &v_url, 0, ua));
        return Ok(serde_json::json!({
            "ok": true,
            "platform": platform,
            "title": title,
            "author": platform,
            "watermark_free": true,
            "extractor_version": rules.version,
            "streams": streams
        }));
    }

    Err(format!("No direct video stream found on {platform} page"))
}

fn extract_meta_or_json_url(html: &str, markers: &[&str]) -> Option<String> {
    for marker in markers {
        if let Some(idx) = html.find(marker) {
            let rest = &html[idx + marker.len()..];
            if let Some(end) = rest.find('"') {
                let raw = &rest[..end];
                let unescaped = raw
                    .replace(r"\/", "/")
                    .replace(r"\u0026", "&")
                    .replace("&amp;", "&");
                if unescaped.starts_with("http://") || unescaped.starts_with("https://") {
                    return Some(unescaped);
                }
            }
        }
    }
    None
}

async fn probe_direct_url(
    client: &reqwest::Client,
    url: &str,
    platform: &str,
    rules: &ExtractorRules,
) -> Result<serde_json::Value, String> {
    let ua = "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    let mut clen = 0u64;
    let mut content_disp: Option<String> = None;
    let mut content_type: Option<String> = None;
    let mut final_url: Option<String> = None;

    if let Ok(resp) = client
        .head(url)
        .header(reqwest::header::USER_AGENT, ua)
        .send()
        .await
    {
        if resp.status().is_success() {
            final_url = Some(resp.url().to_string());
            clen = resp
                .headers()
                .get(reqwest::header::CONTENT_LENGTH)
                .and_then(|v| v.to_str().ok())
                .and_then(|s| s.parse::<u64>().ok())
                .unwrap_or(0);
            content_disp = resp
                .headers()
                .get(reqwest::header::CONTENT_DISPOSITION)
                .and_then(|v| v.to_str().ok())
                .map(|s| s.to_string());
            content_type = resp
                .headers()
                .get(reqwest::header::CONTENT_TYPE)
                .and_then(|v| v.to_str().ok())
                .map(|s| s.to_string());
        }
    }

    if clen == 0 && content_disp.is_none() && !url.contains("googlevideo.com") {
        if let Ok(resp) = client
            .get(url)
            .header(reqwest::header::USER_AGENT, ua)
            .header(reqwest::header::RANGE, "bytes=0-0")
            .send()
            .await
        {
            if resp.status().is_success() || resp.status().as_u16() == 206 {
                final_url = Some(resp.url().to_string());
                let cr_total = resp
                    .headers()
                    .get(reqwest::header::CONTENT_RANGE)
                    .and_then(|v| v.to_str().ok())
                    .and_then(|s| s.rsplit('/').next())
                    .and_then(|s| s.parse::<u64>().ok());
                clen = cr_total
                    .or_else(|| {
                        resp.headers()
                            .get(reqwest::header::CONTENT_LENGTH)
                            .and_then(|v| v.to_str().ok())
                            .and_then(|s| s.parse::<u64>().ok())
                            .filter(|&v| v > 1)
                    })
                    .unwrap_or(clen);
                if content_disp.is_none() {
                    content_disp = resp
                        .headers()
                        .get(reqwest::header::CONTENT_DISPOSITION)
                        .and_then(|v| v.to_str().ok())
                        .map(|s| s.to_string());
                }
                if content_type.is_none() {
                    content_type = resp
                        .headers()
                        .get(reqwest::header::CONTENT_TYPE)
                        .and_then(|v| v.to_str().ok())
                        .map(|s| s.to_string());
                }
            }
        }
    }

    let clean_url_buf;
    let effective_url = if url.contains("googlevideo.com") {
        clean_url_buf = normalize_googlevideo_url(url);
        clean_url_buf.as_str()
    } else {
        url
    };

    let (clen, ua, fname, ext, label_override) = if effective_url.contains("googlevideo.com") {
        let parsed = reqwest::Url::parse(effective_url).ok();
        let query_param = |key: &str| -> Option<String> {
            parsed.as_ref().and_then(|u| {
                u.query_pairs()
                    .find(|(k, _)| k == key)
                    .map(|(_, v)| v.into_owned())
            })
        };
        let yt_clen = query_param("clen")
            .and_then(|s| s.parse::<u64>().ok())
            .filter(|&v| v > 0)
            .unwrap_or(clen);
        let mime = query_param("mime").unwrap_or_default().to_ascii_lowercase();
        let yt_ext = if mime.contains("audio/mp4") {
            "m4a"
        } else if mime.contains("webm") {
            "webm"
        } else {
            "mp4"
        };
        let itag = query_param("itag").unwrap_or_default();
        let kind_label = if mime.starts_with("audio/") {
            "Audio Stream"
        } else {
            "Video Stream"
        };
        let label = if itag.is_empty() {
            format!("YouTube {kind_label} ({})", yt_ext.to_uppercase())
        } else {
            format!("YouTube {kind_label} #{itag} ({})", yt_ext.to_uppercase())
        };
        let name = format!("YouTube_Stream_{}.{}", if itag.is_empty() { "hd" } else { &itag }, yt_ext);
        let yt_ua = youtube_ua_for_playback_url(effective_url, rules);
        (yt_clen, yt_ua, name, yt_ext.to_string(), Some(label))
    } else {
        let base_fname = infer_smart_filename(effective_url);
        let fname = ensure_filename_has_known_extension(
            &base_fname,
            effective_url,
            final_url.as_deref(),
            content_disp.as_deref(),
            content_type.as_deref(),
        );
        let ext = known_extension_of(&fname)
            .or_else(|| content_type.as_deref().and_then(extension_from_mime).map(|s| s.to_string()))
            .unwrap_or_else(|| {
                if platform == "Direct" {
                    "bin".to_string()
                } else {
                    "mp4".to_string()
                }
            });
        (clen, ua.to_string(), fname, ext, None)
    };

    let cat = match ext.as_str() {
        "mp4" | "mkv" | "webm" | "avi" | "mov" | "flv" | "m4v" | "3gp" | "ts" | "m3u8" | "mpd" => "Video",
        "mp3" | "m4a" | "aac" | "flac" | "wav" | "ogg" | "opus" => "Music",
        "zip" | "rar" | "7z" | "tar" | "gz" | "tgz" | "xz" | "bz2" | "zst" => "Compressed",
        "apk" | "xapk" | "apks" | "aab" | "obb" | "iso" | "dmg" | "exe" | "msi" | "deb" | "rpm" => "Programs",
        "pdf" | "epub" | "mobi" | "doc" | "docx" | "xls" | "xlsx" | "ppt" | "pptx" | "txt" | "csv" => "Documents",
        _ => "General",
    };

    let format_kind = if cat == "Music" { "audio" } else { "video" };
    let mut streams = vec![serde_json::json!({
        "itag": 1,
        "label": label_override.unwrap_or_else(|| format!("Original File ({})", ext.to_uppercase())),
        "quality": ext.to_uppercase(),
        "format_kind": format_kind,
        "bitrate_kbps": 0,
        "watermark_free": true,
        "ext": ext,
        "category": cat,
        "file_name": fname,
        "size_bytes": clen,
        "url": effective_url,
        "user_agent": ua
    })];

    if matches!(cat, "Video" | "Music") || platform != "Direct" {
        streams.extend(build_mp3_variants(&fname, effective_url, clen, &ua));
    }

    Ok(serde_json::json!({
        "ok": true,
        "platform": if effective_url.contains("googlevideo.com") { "YouTube" } else { platform },
        "title": fname,
        "author": if effective_url.contains("googlevideo.com") { "YouTube" } else { platform },
        "watermark_free": true,
        "extractor_version": rules.version,
        "streams": streams
    }))
}

pub fn split_companion_audio(raw: &str) -> (String, Option<String>) {
    if let Some((base, frag)) = raw.split_once("#yt_audio=") {
        let decoded: String = url::form_urlencoded::parse(frag.as_bytes())
            .map(|(k, v)| if v.is_empty() { k.into_owned() } else { format!("{k}={v}") })
            .collect();
        let clean_aud = if decoded.starts_with("http") {
            Some(normalize_googlevideo_url(&decoded))
        } else {
            None
        };
        (base.to_string(), clean_aud)
    } else {
        (raw.to_string(), None)
    }
}

pub fn normalize_googlevideo_url(raw: &str) -> String {
    let (without_frag, companion) = raw
        .split_once("#yt_audio=")
        .map(|(b, f)| (b, Some(f)))
        .unwrap_or((raw, None));
    let Ok(mut parsed) = reqwest::Url::parse(without_frag) else {
        return raw.to_string();
    };
    parsed.set_fragment(None);
    let drop_keys = ["range", "rn", "rbuf", "ump", "srfvp", "alr"];
    let kept: Vec<(String, String)> = parsed
        .query_pairs()
        .filter(|(k, _)| !drop_keys.contains(&k.as_ref()))
        .map(|(k, v)| (k.into_owned(), v.into_owned()))
        .collect();
    if kept.len() != parsed.query_pairs().count() {
        let mut pairs = parsed.query_pairs_mut();
        pairs.clear();
        for (k, v) in kept {
            pairs.append_pair(&k, &v);
        }
    }
    let clean = parsed.to_string();
    match companion {
        Some(f) => format!("{clean}#yt_audio={f}"),
        None => clean,
    }
}

pub fn youtube_ua_for_playback_url(url: &str, rules: &ExtractorRules) -> String {
    if url.contains("c=VISIONOS") {
        VISIONOS_UA.to_string()
    } else if url.contains("c=IOS") {
        rules.youtube_ios_ua.clone()
    } else if url.contains("c=ANDROID_VR") {
        "com.google.android.apps.youtube.vr.pico/1.73.21 (Linux; U; Android 10; A8110 Build/QKQ1) gzip".to_string()
    } else if url.contains("c=MWEB") || url.contains("c=WEB") || url.contains("c=TV") {
        "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36".to_string()
    } else {
        rules.youtube_android_ua.clone()
    }
}
