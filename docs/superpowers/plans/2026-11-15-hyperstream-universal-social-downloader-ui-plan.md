# HyperStream v0.6.0 — Universal Social Downloader, Hot-Updatable Extractors & Flagship UI Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build HyperStream v0.6.0 with Share-to-Open format & MP3 quality selection, watermark-free social video extraction (YouTube, TikTok, Instagram, Facebook, X/Twitter, Direct), hot-updatable extractor rules (`extractor_rules.json`), fixed Android HTTPS TLS roots, and a flagship Material 3 Expressive UI overhaul.

**Architecture:** The Rust JNI engine (`mdm-engine` + `mdm-android-ffi`) handles TLS root fallback (`webpki-root-certs` + native certs), multi-platform media probing (`ProbeVideo` command for YouTube InnerTube, TikTok No-Watermark API, Facebook/Instagram HD resolvers, and generic streams), MP3 bitrate-targeted audio downloads (`320k`/`192k`/`128k`), and OTA extractor rule updates (`UpdateExtractors`). On the Android side, `LinkCatcherActivity` routes shared links directly into `MainActivity` (`EXTRA_SHARED_URL`), which opens a flagship Material 3 **Smart Format & Quality Picker** and uses a unified `MdmRepository.shared` singleton with a dismissible `ModalBottomSheet` segment inspector and 5-tab navigation.

**Tech Stack:** Rust 1.98 (`mdm-engine`, `mdm-android-ffi`, `reqwest` + `rustls` + `webpki-root-certs`, `tokio`), Kotlin 2.1, Jetpack Compose Material 3 (`ModalBottomSheet`, `NavigationBar`), Android SDK 35 (`com.hyperstream.downloadmanager`).

**Spec:** `docs/superpowers/specs/2026-11-15-hyperstream-universal-social-downloader-ui-design.md`

## Global Constraints

- Target package ID: `com.hyperstream.downloadmanager`, version `0.6.0` (`versionCode = 6`).
- Repository & OTA rules source: `https://github.com/djsusmith/hyperstream-android` (`https://raw.githubusercontent.com/djsusmith/hyperstream-android/main/extractor_rules.json`).
- Maximum connections unlocked: `1..=128` (`mdm_engine::download::MAX_CONNECTIONS_UNLOCKED`).
- Never silently save raw `/watch` or social page URLs as 0-byte `"watch"` files; always resolve title, extension, and stream URL or open the format picker.
- Never render `ProgressInspectorSheet` inline above navigation tabs; it must be a dismissible `ModalBottomSheet` with a close button, live state lookup by `id`, and `Retry`/`Delete` actions.
- Bottom `NavigationBar` must have 5 tabs (`Downloads`, `Grabber`, `Torrents`, `Queues`, `Settings`) so labels never wrap (`"Download\ns"`), with `Credits` accessible from the top app bar and Settings.

## Review Focus

1. **Unquoted/mobile/shortened social URLs (`youtu.be/...`, `m.youtube.com/shorts/...`, `vm.tiktok.com/...`, `instagram.com/reel/...`, `fb.watch/...`) shared with surrounding caption text:** `LinkParser.extractFirstUrl` and `detectPlatform` must strip surrounding promotional text and classify all mobile/shortened domains as `VIDEO_STREAM_PAGE`.
2. **MP3 Audio quality selection (`320 kbps`, `192 kbps`, `128 kbps`):** Selecting an MP3 option must set category to `"Music"`, sanitize the filename to `<Title> (<Bitrate>kbps).mp3`, and download the best audio stream without failing on video-only containers.
3. **TikTok watermark removal:** Probing a TikTok link must return the watermark-free HD stream (`hdplay` / `play` without watermark) labeled `"No Watermark HD MP4"` alongside the MP3 audio option.
4. **Offline or blocked OTA extractor update:** Running `{"op":"update_extractors"}` when offline or rate-limited must fall back cleanly to built-in `ExtractorRules::default()` without crashing or wiping existing rules.
5. **Switching tabs while inspecting a download:** Opening the 128-thread segment inspector and dismissing it (or switching tabs) must never leave a permanent overlay blocking the `Grabber`, `Torrents`, `Queues`, `Settings`, or `Credits` screens.

---

### Task 1: Rust Engine Android TLS Root Fallback & Multi-Platform Social Extractor + OTA Rules (`mdm-engine` & `mdm-android-ffi`)

**Files:**
- Modify: `upstream/crates/engine/Cargo.toml`
- Modify: `upstream/crates/engine/src/tls.rs`
- Modify: `upstream/crates/engine/src/ftp.rs`
- Create: `upstream/crates/android-ffi/src/extractors.rs`
- Modify: `upstream/crates/android-ffi/src/controller.rs`
- Modify: `upstream/crates/android-ffi/src/lib.rs`
- Create: `extractor_rules.json`

**Interfaces:**
- Produces in `upstream/crates/android-ffi/src/extractors.rs`:
  - `pub struct ExtractorRules` (serializable JSON config with `version: String`, `youtube_android_version: String`, `youtube_android_ua: String`, `youtube_ios_version: String`, `youtube_ios_ua: String`, `tiktok_api_url: String`, `cobalt_instances: Vec<String>`)
  - `pub fn detect_platform(url: &str) -> &'static str` (`"YouTube"`, `"TikTok"`, `"Instagram"`, `"Facebook"`, `"X"`, `"Direct"`)
  - `pub fn extract_youtube_video_id(url: &str) -> Option<String>`
  - `pub fn infer_smart_filename(url: &str) -> String`
  - `pub async fn probe_media_url(url: &str, rules: &ExtractorRules) -> Result<serde_json::Value, String>` (returns `{ ok, platform, title, author, watermark_free, extractor_version, streams: [{ id, itag, label, quality, format_kind, bitrate_kbps, watermark_free, ext, category, file_name, size_bytes, url, user_agent }] }`, always including Video resolutions + `MP3 320 kbps`, `MP3 192 kbps`, `MP3 128 kbps` options)
  - `pub async fn update_extractor_rules(storage_dir: &Path) -> Result<ExtractorRules, String>`
- Produces in `ControllerCommand` (`controller.rs`):
  - `ProbeVideo { url: String }`
  - `UpdateExtractors`
  - `RetryDownload { id: u64 }`

- [ ] **Step 1: Write failing unit tests in `upstream/crates/android-ffi/src/lib.rs` for multi-platform detection, YouTube/TikTok/IG/FB filename inference, MP3 stream options, `Main` queue auto-start, and `UpdateExtractors` fallback**

```rust
#[test]
fn detects_social_platforms_and_infers_clean_filenames() {
    assert_eq!(extractors::detect_platform("https://youtube.com/watch?v=paZ0RGJ0cj0"), "YouTube");
    assert_eq!(extractors::detect_platform("https://vm.tiktok.com/ZM12345/"), "TikTok");
    assert_eq!(extractors::detect_platform("https://www.instagram.com/reel/C9abc/"), "Instagram");
    assert_eq!(extractors::detect_platform("https://fb.watch/xyz123/"), "Facebook");
    assert_eq!(
        extractors::infer_smart_filename("https://youtube.com/watch?v=paZ0RGJ0cj0&is=123"),
        "YouTube_paZ0RGJ0cj0.mp4"
    );
}

#[test]
fn builds_mp3_quality_variants_for_probed_media() {
    let mp3s = extractors::build_mp3_variants("Trillion Dollar Scandal", "https://cdn.example.com/audio.m4a", 10_000_000, "TestUA");
    assert_eq!(mp3s.len(), 3);
    assert_eq!(mp3s[0]["bitrate_kbps"], 320);
    assert_eq!(mp3s[0]["ext"], "mp3");
    assert_eq!(mp3s[0]["category"], "Music");
    assert!(mp3s[0]["file_name"].as_str().unwrap().ends_with("(320kbps).mp3"));
    assert_eq!(mp3s[1]["bitrate_kbps"], 192);
    assert_eq!(mp3s[2]["bitrate_kbps"], 128);
}
```

- [ ] **Step 2: Run `cargo test -p mdm-android-ffi` to verify RED failure**

Run: `cargo test --manifest-path upstream/Cargo.toml -p mdm-android-ffi`
Expected: FAIL (`extractors` module and functions not defined, `reqwest::RequestBuilder::json` missing feature or body serialization).

- [ ] **Step 3: Implement `upstream/crates/android-ffi/src/extractors.rs`, wire `ControllerCommand::{ProbeVideo, UpdateExtractors, RetryDownload}` in `controller.rs`, and create root `extractor_rules.json`**

Use `.body(serde_json::to_vec(&payload)?)` instead of `.json(&payload)` on `reqwest::RequestBuilder` (since workspace `reqwest` does not enable the optional `json` feature). Support:
- YouTube (`ANDROID` + `IOS` InnerTube player API, returning muxed MP4, 4K/1440p/1080p/720p/480p/360p video streams, plus 320k/192k/128k MP3 options).
- TikTok (`https://www.tikwm.com/api/` + page JSON fallback returning watermark-free `hdplay`/`play` `"No Watermark HD MP4"` stream + original MP3 320k/192k/128k options).
- Instagram & Facebook (embed/GraphQL + Cobalt/direct OpenGraph `og:video` extraction returning HD MP4 without watermark + MP3 320k/192k/128k options).
- Direct URLs (`HEAD` / range probe).

- [ ] **Step 4: Run `cargo test` on `mdm-engine` and `mdm-android-ffi` and build `aarch64-linux-android` `.so` to verify GREEN**

Run:
- `cargo test --manifest-path upstream/Cargo.toml -p mdm-engine --no-default-features --lib tls`
- `cargo test --manifest-path upstream/Cargo.toml -p mdm-android-ffi`
- `bash scripts/build-rust-android.sh`
Expected: All tests PASS and `android/app/src/main/jniLibs/arm64-v8a/libmdm_android.so` builds cleanly.

---

### Task 2: Unified `MdmRepository`, Share-to-Open `LinkCatcherActivity` → `MainActivity`, and Foreground Service Throttling

**Files:**
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/browser/LinkParser.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/browser/LinkCatcherActivity.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/MainActivity.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/core/Models.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/core/MdmRepository.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/service/HyperStreamDownloadService.kt`
- Modify: `android/app/src/test/java/com/hyperstream/downloadmanager/core/CoreModelsTest.kt`

**Interfaces:**
- Produces in `Models.kt`:
  - `data class ProbedStreamUi(val itag: Int, val label: String, val quality: String, val formatKind: String, val bitrateKbps: Int, val watermarkFree: Boolean, val ext: String, val category: String, val fileName: String, val sizeBytes: Long, val url: String, val userAgent: String)`
  - `data class ProbeResultUi(val ok: Boolean, val platform: String, val title: String, val author: String, val watermarkFree: Boolean, val extractorVersion: String, val streams: List<ProbedStreamUi>, val error: String? = null)`
- Produces in `MdmRepository`:
  - `MdmRepository.getOrCreate(dataDir: String, downloadsDir: String): MdmRepository` (unified singleton used by `MainActivity`, `LinkCatcherActivity`, and `HyperStreamDownloadService`, starting with an empty download list instead of fake sample ISO items)
  - `suspend fun probeVideo(url: String): ProbeResultUi`
  - `suspend fun updateExtractors(): String`
  - `fun retryDownload(id: Long)`
  - `val pendingSharedUrl: StateFlow<String?>` and `fun setPendingSharedUrl(url: String?)`

- [ ] **Step 1: Write failing Kotlin unit tests in `CoreModelsTest.kt` for social URL extraction from share text, platform classification (`TikTok`, `Instagram`, `Facebook`, `YouTube`), and `ProbeResultUi` JSON parsing**

```kotlin
@Test
fun linkParser_extractsSocialUrlsFromSharedTextAndDetectsPlatform() {
    val raw = "Check out this TikTok! https://vm.tiktok.com/ZM8abc123/ #fyp"
    val extracted = LinkParser.extractFirstUrl(raw)
    assertEquals("https://vm.tiktok.com/ZM8abc123/", extracted)
    assertEquals(LinkKind.VIDEO_STREAM_PAGE, LinkParser.classifyUrl(extracted!!))
    assertEquals("TikTok", LinkParser.detectPlatformName(extracted))
}

@Test
fun parseProbeResultJson_parsesWatermarkFreeAndMp3Streams() {
    val json = """{"ok":true,"platform":"TikTok","title":"Dance Clip","author":"@creator","watermark_free":true,"extractor_version":"2026.11.15","streams":[{"itag":1,"label":"1080p HD MP4 (No Watermark)","quality":"1080p","format_kind":"video","bitrate_kbps":0,"watermark_free":true,"ext":"mp4","category":"Video","file_name":"Dance_Clip_NoWM.mp4","size_bytes":15000000,"url":"https://cdn.tikwm.com/hd.mp4","user_agent":"Mozilla/5.0"},{"itag":320,"label":"MP3 320 kbps (Studio Audio)","quality":"320kbps","format_kind":"mp3","bitrate_kbps":320,"watermark_free":true,"ext":"mp3","category":"Music","file_name":"Dance_Clip (320kbps).mp3","size_bytes":3200000,"url":"https://cdn.tikwm.com/music.mp3","user_agent":"Mozilla/5.0"}]}"""
    val res = MdmRepository.parseProbeResultJson(json)
    assertTrue(res.ok)
    assertEquals("TikTok", res.platform)
    assertTrue(res.watermarkFree)
    assertEquals(2, res.streams.size)
    assertEquals("mp3", res.streams[1].formatKind)
    assertEquals(320, res.streams[1].bitrateKbps)
}
```

- [ ] **Step 2: Run `:app:testDebugUnitTest` to verify RED failure**

Run: `bash android/gradlew -p android :app:testDebugUnitTest`
Expected: FAIL (`extractFirstUrl`, `detectPlatformName`, `parseProbeResultJson` not defined).

- [ ] **Step 3: Implement `LinkParser.kt`, `LinkCatcherActivity.kt` (opens `MainActivity` with `EXTRA_SHARED_URL`), `MainActivity.kt` (`onNewIntent` + unified `MdmRepository.getOrCreate`), `Models.kt`, `MdmRepository.kt`, and 1 Hz notification throttling in `HyperStreamDownloadService.kt`**

- [ ] **Step 4: Run `:app:testDebugUnitTest` to verify GREEN**

Run: `bash android/gradlew -p android :app:testDebugUnitTest`
Expected: PASS.

---

### Task 3: Flagship Material 3 Expressive UI Overhaul, Smart Media Picker & Dismissible `ModalBottomSheet` Inspector

**Files:**
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/theme/Theme.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/HyperStreamApp.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/DownloadsScreen.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/GrabberScreen.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/ProgressInspectorSheet.kt`
- Modify: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/SettingsScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**
- Consumes `MdmRepository` methods (`probeVideo`, `updateExtractors`, `retryDownload`, `cancelDownload`, `addDownload`, `pendingSharedUrl`).
- Produces:
  - 5-tab Bottom `NavigationBar` (`Downloads`, `Grabber`, `Torrents`, `Queues`, `Settings`) with single-line labels (`maxLines = 1`), plus top-bar `Update Extractors`, `Credits`, and `+ Add / Grab` actions.
  - Auto-opening **Smart Format & Quality Picker** whenever `pendingSharedUrl` arrives from Share intent or when the user taps **Fetch Formats** in Grabber, with:
    - Platform badge (`YouTube`, `TikTok • No Watermark`, `Instagram`, `Facebook`, `Direct`), video title & author, and `Watermark Removed` pill.
    - Segmented tabs: **Video Formats (4K / 1080p / 720p / 360p / No-Watermark HD)** and **MP3 Audio (320 kbps / 192 kbps / 128 kbps)**.
    - Thread count slider/chips (`16T`, `32T`, `64T`, `128T`) and one-tap **Download Now** button that switches straight to `Downloads` tab and starts downloading immediately.
  - Dismissible Material 3 `ModalBottomSheet` for `ProgressInspectorSheet` looking up the live `DownloadItemUi` by `inspectedDownloadId`, with Close (`X`), `Pause`/`Resume`, `Retry`, and `Delete` buttons, and accurate segment state labels (`Probing...`, `Receiving`, `Completed`, `Failed`).

- [ ] **Step 1: Implement the redesigned `Theme.kt`, `HyperStreamApp.kt`, `DownloadsScreen.kt`, `GrabberScreen.kt`, `ProgressInspectorSheet.kt`, and `SettingsScreen.kt`, and bump version to `0.6.0` in `build.gradle.kts`**

- [ ] **Step 2: Run `:app:testDebugUnitTest` and `:app:assembleDebug` to verify compilation and unit tests**

Run: `bash android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Device Installation, End-to-End Share & Download Verification on Galaxy S25 Ultra, and GitHub Release (`v0.6.0` & `nightly`)

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Uninstall old app state/APK and install `HyperStream-v0.6.0-arm64.apk` on `SM-S938B` via `rish`**

Run:
- `cp android/app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/Download/HyperStream-v0.6.0-arm64.apk`
- `cp android/app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/Documents/HyperStream-v0.6.0-arm64.apk`
- `rish -c "pm install -r /storage/emulated/0/Download/HyperStream-v0.6.0-arm64.apk"`

- [ ] **Step 2: Verify Share-to-Open Intent & Live Probing on Device via `rish`**

Run:
- `rish -c "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'https://youtube.com/watch?v=paZ0RGJ0cj0' -n com.hyperstream.downloadmanager/.browser.LinkCatcherActivity"`
- Check `logcat` to verify `MainActivity` launches to the foreground with the shared URL, probes streams without TLS errors, and logs zero `NotificationManager` rate-limit warnings.

- [ ] **Step 3: Commit all changes, push to `https://github.com/djsusmith/hyperstream-android` (`main`), and publish `v0.6.0` + `nightly` GitHub Releases**
