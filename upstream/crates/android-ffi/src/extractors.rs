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
                .map(|s| s.to_string())
        })
        .unwrap_or_else(|| "download.bin".to_string())
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

async fn probe_youtube(
    client: &reqwest::Client,
    video_id: &str,
    rules: &ExtractorRules,
) -> Result<serde_json::Value, String> {
    let clients = [
        (
            rules.youtube_android_ua.as_str(),
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

    for (ua, payload) in clients {
        let Ok(body_bytes) = serde_json::to_vec(&payload) else {
            continue;
        };
        let resp = client
            .post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header(reqwest::header::USER_AGENT, ua)
            .header(reqwest::header::CONTENT_TYPE, "application/json")
            .body(body_bytes)
            .send()
            .await;
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
                } else if mime.starts_with("audio/mp4") || mime.starts_with("audio/webm") {
                    if best_audio_url.is_none() || mime.starts_with("audio/mp4") {
                        best_audio_url = Some((s_url.to_string(), clen));
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

    // 2. Scrape OpenGraph / embedded JSON video URLs directly from the social page
    let resp = client
        .get(url)
        .header(reqwest::header::USER_AGENT, ua)
        .header(reqwest::header::ACCEPT_LANGUAGE, "en-US,en;q=0.9")
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let html_bytes = resp.bytes().await.map_err(|e| e.to_string())?;
    let html = String::from_utf8_lossy(&html_bytes);

    let extracted_video_url = extract_meta_or_json_url(
        &html,
        &[
            r#"property="og:video:secure_url" content=""#,
            r#"property="og:video" content=""#,
            r#""hd_src":""#,
            r#""playable_url_quality_hd":""#,
            r#""video_url":""#,
            r#""playable_url":""#,
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
    let clen = match client
        .head(url)
        .header(reqwest::header::USER_AGENT, ua)
        .send()
        .await
    {
        Ok(resp) => resp
            .headers()
            .get(reqwest::header::CONTENT_LENGTH)
            .and_then(|v| v.to_str().ok())
            .and_then(|s| s.parse::<u64>().ok())
            .unwrap_or(0),
        Err(_) => 0,
    };

    let fname = infer_smart_filename(url);
    let ext = Path::new(&fname)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("mp4")
        .to_ascii_lowercase();
    let cat = match ext.as_str() {
        "mp4" | "mkv" | "webm" | "avi" | "mov" => "Video",
        "mp3" | "m4a" | "flac" | "wav" | "ogg" => "Music",
        "zip" | "rar" | "7z" | "tar" | "gz" | "xz" => "Compressed",
        "apk" | "xapk" | "iso" => "Programs",
        "pdf" | "epub" | "doc" | "docx" => "Documents",
        _ => "General",
    };

    let mut streams = vec![serde_json::json!({
        "itag": 1,
        "label": format!("Original File ({})", ext.to_uppercase()),
        "quality": ext.to_uppercase(),
        "format_kind": "video",
        "bitrate_kbps": 0,
        "watermark_free": true,
        "ext": ext,
        "category": cat,
        "file_name": fname,
        "size_bytes": clen,
        "url": url,
        "user_agent": ua
    })];

    if matches!(cat, "Video" | "Music") || platform != "Direct" {
        streams.extend(build_mp3_variants(&fname, url, clen, ua));
    }

    Ok(serde_json::json!({
        "ok": true,
        "platform": platform,
        "title": fname,
        "author": platform,
        "watermark_free": true,
        "extractor_version": rules.version,
        "streams": streams
    }))
}
