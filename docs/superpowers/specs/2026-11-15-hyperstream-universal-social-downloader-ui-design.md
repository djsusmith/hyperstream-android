# HyperStream v0.6.0 — Universal Social Downloader, Hot-Updatable Extractors & Flagship UI Overhaul

**Date:** 2026-11-15
**Target Package:** `com.hyperstream.downloadmanager`
**Repository:** `https://github.com/djsusmith/hyperstream-android`

---

## 1. Intent & Why

HyperStream needs to evolve from a raw file download manager into an all-in-one flagship Android download & social media suite. When a user taps **Share → HyperStream** inside YouTube, TikTok, Instagram, Facebook, X/Twitter, or any browser:
1. **Immediate App Launch & Stream Choice Sheet:** The app must open immediately to a rich **Media Format & Quality Sheet** instead of silently closing after a toast and saving a dead 0-byte file named `"watch"`.
2. **Universal Social Media + Watermark-Free + MP3 Quality Options:** Users need one-tap choices for:
   - **Video Resolutions:** `2160p 4K`, `1440p QHD`, `1080p Full HD`, `720p HD`, `480p`, `360p (Fast Muxed MP4)`
   - **Watermark-Free Video:** Automatic no-watermark HD MP4 stream selection for TikTok (`tikwm` / native playAddr HD), Instagram Reels, and Facebook Reels/Watch.
   - **MP3 Audio with Bitrate Selection:** Dedicated **Audio / MP3** mode with selectable bitrates (`320 kbps High`, `192 kbps Balanced`, `128 kbps Compact`) that downloads the best audio stream and writes a clean `.mp3` file into `Music/`.
3. **Hot-Updatable Extractor Engine:** Because YouTube, TikTok, Instagram, and Facebook frequently rotate client versions, signatures, and endpoints to block downloaders, HyperStream must include an **Over-The-Air (OTA) Extractor & App Updater** that fetches updated extractor configs (client versions, user-agents, regexes, API endpoints) from GitHub (`djsusmith/hyperstream-android` + upstream feeds) automatically and on-demand without requiring a full manual reinstall.
4. **Flagship Material 3 Expressive UI Overhaul & Bug Fixes:** Fix all 6 critical issues observed in `Screen_Recording_20261115_105040.mp4`:
   - Fix Rust Android HTTPS TLS root certificate verification (`webpki-root-certs` + native certs fallback when `rustls-platform-verifier` is uninitialized).
   - Fix `Main` queue downloads staying stuck in `"Queued"` forever (`should_start` bug).
   - Unify `MdmRepository.shared` singleton across `MainActivity`, `LinkCatcherActivity`, and `HyperStreamDownloadService`, and remove fake sample rows from production runtime.
   - Replace the broken inline `ProgressInspectorSheet` (which trapped the screen across all tabs with no close button and showed 128 fake `"Completed [0 B -> 0 B]"` segments) with a proper dismissible `ModalBottomSheet` with live state lookup, close/delete/retry actions, and accurate probing/error states.
   - Redesign the entire UI with a cohesive dark obsidian + electric cyan/violet glass aesthetic, platform badges (YouTube, TikTok, Instagram, Facebook, Direct, BitTorrent), rich progress cards, and a clean 5-tab Bottom `NavigationBar` (`Downloads`, `Grabber`, `Torrents`, `Queues`, `Settings`) so labels never wrap (`"Download\ns"`), with `Credits` accessible from the top app bar and Settings.
   - Throttle `HyperStreamDownloadService` foreground notifications to 1 Hz to eliminate Android `NotificationManager` rate-limit shedding.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- **Share-to-Open Experience:** Sharing any URL into `LinkCatcherActivity` launches `MainActivity` (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`) directly into the **Smart Media Grabber & Format Picker Modal**, auto-probing the shared URL immediately.
- **Multi-Platform Native + Cloud-Assisted Extractors:**
  - **YouTube (`youtube.com`, `youtu.be`, `shorts`):** Multi-client InnerTube resolver (`ANDROID`, `IOS`, `TV`, plus hot-updated client versions from extractor manifest) returning muxed MP4, 4K/1080p/720p/480p/360p video streams, and audio streams with matching client `User-Agent` headers so `googlevideo.com` responds with HTTP `206 Partial Content`.
  - **TikTok (`tiktok.com`, `vm.tiktok.com`, `vt.tiktok.com`):** Watermark-free HD video (`hdplay` / `play` without watermark) + original MP3 audio extraction.
  - **Instagram (`instagram.com/reel/`, `/p/`, `/tv/`) & Facebook (`facebook.com`, `fb.watch`):** Embedded GraphQL / OpenGraph / Cobalt-compatible multi-endpoint stream extraction for HD MP4 and MP3 audio.
  - **Any Direct or Web Media Link:** Automatic HTTP `HEAD`/`Range` and HTML5 `<video>`/`<audio>`/HLS stream sniffing.
- **MP3 Bitrate Selector (`320 kbps`, `192 kbps`, `128 kbps`):** Users can pick MP3 output with explicit quality (`320k`, `192k`, `128k`); the engine downloads the highest-fidelity audio stream, tags/transcodes or remuxes to `.mp3` with the chosen bitrate target, and saves to `/storage/emulated/0/Download/HyperStream/Music/<Title> (<Bitrate>kbps).mp3`.
- **Frequent Extractor & App Updates:**
  - Live `extractor_rules.json` hot-reload stored in `<dataDir>/extractor_rules.json` (overrides YouTube InnerTube client versions, User-Agents, TikTok/IG/FB API endpoints, and fallback instances without recompiling Rust).
  - One-tap **"Update Extractors Now"** + **"Check App Update"** in Grabber & Settings, plus automatic background refresh on app startup.

### Non-Goals
- Bypassing DRM-encrypted subscription streams (Netflix/Disney+ Widevine).
- Requiring root or external Termux installation from the end user.

### Anti-Goals
- Never silently drop a shared link as a dead file named `"watch"`.
- Never trap the user in an unclosable overlay across navigation tabs.
- Never show fake sample downloads (`ubuntu.iso`, `blender.tar.xz`) in production state.

---

## 3. Approaches Considered

### Approach A (Selected): Hybrid Native Rust Engine + Hot-Updatable Multi-Platform Extractor Layer + Flagship M3 UI
- **How it works:**
  1. `mdm-android-ffi` (`controller.rs`) implements a multi-provider `probe_video_streams` & `resolve_media_stream` pipeline driven by a hot-reloadable `ExtractorConfig` (`extractor_rules.json`).
  2. Built-in providers cover **YouTube** (InnerTube multi-client with hot-updatable client versions/UAs), **TikTok** (No-Watermark HD + Original Audio), **Facebook & Instagram** (mobile GraphQL/embed page parser + multi-instance Cobalt/social resolver fallback), and **Direct/Generic HTML5 Media**.
  3. When the user selects an **MP3 (`320 kbps` / `192 kbps` / `128 kbps`)** option, the download saves with `.mp3` extension and applies audio stream extraction/post-processing at the requested bitrate.
  4. `LinkCatcherActivity` forwards shared URLs to `MainActivity` via `EXTRA_SHARED_URL`, which immediately opens the **Grabber Format Picker BottomSheet** with live stream probing, thumbnail/platform badge, quality/MP3 tabs, watermark-free toggle, and connection count selector (`1..128`).
- **Trade-offs:** Slightly more code in Rust + Kotlin, but works out-of-the-box on Android without external binaries and survives platform API changes via JSON rule hot-updates.

### Approach B (Rejected): Static Hardcoded Rust Scrapers Only
- **Trade-offs:** Cannot adapt when YouTube bumps `clientVersion` or TikTok rotates endpoints without compiling and shipping a new `.so` binary.

---

## 4. Detailed Architecture & Builder Decisions (Our Calls)

1. **Share-to-Open Flow (`LinkCatcherActivity` → `MainActivity`):**
   - `LinkCatcherActivity` extracts the first HTTP/HTTPS/magnet URL from `Intent.ACTION_SEND` or `Intent.ACTION_VIEW`.
   - Instead of calling `addDownload` blindly and finishing, it launches `MainActivity` with `Intent.EXTRA_TEXT` / `extra_shared_url` and `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`.
   - `MainActivity` implements `onNewIntent` and passes `sharedUrl` into `HyperStreamApp`, which immediately opens the **Smart Format Picker Sheet** (or switches to Grabber and triggers `probeVideo(sharedUrl)` automatically).
2. **Smart Format Picker & Grabber UI:**
   - Displays detected platform pill (`YouTube`, `TikTok • No Watermark`, `Instagram`, `Facebook`, `Direct Media`), video title, author/channel, and extractor version badge with a **"Refresh Extractors"** button.
   - Two Segmented Mode Tabs inside the picker:
     - **Video (MP4 / WebM):** Lists available resolutions (`2160p 4K`, `1440p QHD`, `1080p Full HD`, `720p HD`, `480p`, `360p Audio+Video`), showing codec, file size, and a `"No Watermark"` badge on TikTok/Reels streams.
     - **Audio (MP3 / M4A):** Always offers **`MP3 320 kbps (Studio Quality)`**, **`MP3 192 kbps (High Quality)`**, **`MP3 128 kbps (Standard)`**, plus native `M4A / Opus Original` stream, mapping to the best audio stream from the source and saving as `.mp3` in `Music/`.
3. **Hot-Updatable Extractor Engine (`ExtractorUpdater`):**
   - Rust `AndroidCoreController` loads `<storage_dir>/extractor_rules.json` on startup and exposes `{"op":"update_extractors"}` which fetches the latest `extractor_rules.json` from `https://raw.githubusercontent.com/djsusmith/hyperstream-android/main/extractor_rules.json` (with built-in defaults if offline), updating YouTube InnerTube client versions, TikTok no-watermark endpoints, and social media user-agents/fallbacks on the fly.
4. **UI Overhaul (`HyperStreamApp`, `DownloadsScreen`, `GrabberScreen`, `ProgressInspectorSheet`):**
   - **Theme & Cards:** Deep Obsidian (`#090D16`) background, elevated glass-styled cards (`#131B2E`) with subtle borders, platform-colored accent pills (YouTube Red, TikTok Cyan/Pink, Instagram Magenta, Facebook Blue, HyperStream Electric Cyan), smooth animated progress bars, and clear status badges (`Downloading`, `Probing`, `Completed`, `Paused`, `Failed` with visible error reason and `Retry` + `Delete` buttons).
   - **Dismissible `ModalBottomSheet` Inspector:** Tapping a download card opens a proper Material 3 `ModalBottomSheet` with a close (`X`) button, live segment progress bars, per-connection status, and `Pause`/`Resume`/`Retry`/`Delete` actions. Switching tabs never leaves a stuck overlay.
   - **5-Tab Bottom Bar:** `Downloads`, `Grabber`, `Torrents`, `Queues`, `Settings` (with `Credits` in the top app bar Info button and inside Settings) so no tab label ever wraps.
