# HyperStream Download Manager for Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port `anefzaoui/misterdownloadmanager` to Android (`com.hyperstream.downloadmanager`) with 1-to-1 Rust engine parity (`libmdm_android.so`), 128-thread segmented downloading, BitTorrent/magnet support, video/batch grabber, ForegroundService WakeLock, automatic link catching, optional Shizuku integration, a Jetpack Compose UI verified with `/compose-preview`, and automated upstream sync (`Stable` + `Bug-Fix / Nightly` channels) published to GitHub.

**Architecture:** We vendor and sync upstream's Rust workspace crates (`mdm-engine`, `mdm-torrent`, `mdm-debrid`, `mdm-extract`, `mdm-geo`, `mdm-cnl`, `mdm-ipc`, `mdm-remote`) under `upstream/` and expose them through a new `mdm-android-ffi` Rust `cdylib` (`libmdm_android.so`) communicating via JSON envelopes and 10 Hz state snapshots over JNI. An Android Gradle (`:app`) module in `android/` wraps the JNI core inside `HyperStreamDownloadService` (holding `PARTIAL_WAKE_LOCK` + `WIFI_MODE_FULL_HIGH_PERF`), wires system-wide `IntentFilter` link interception, Shizuku (`rish` / `ShizukuProvider`), and an upstream release-channel updater, and renders a 5-tab Material 3 / MDM-themed Jetpack Compose UI with `@Preview` functions rendered via `/compose-preview`.

**Tech Stack:** Rust 2024 (`1.98+`), Tokio, `mdm-engine`, `librqbit` (`mdm-torrent`), `jni = "0.21"`, Kotlin 2.1+, Jetpack Compose (Material 3), Android Gradle Plugin 8.7+, Coroutines/StateFlow, `kotlinx.serialization`, Shizuku (`dev.rikka.shizuku:api` + `rish`), GitHub Actions (`upstream-sync.yml` + `android-ci.yml`).

**Spec:** `docs/superpowers/specs/2026-11-15-hyperstream-mdm-android-design.md`

## Global Constraints

- **Application ID & Branding:** `com.hyperstream.downloadmanager` (**HyperStream Download Manager**), licensed under `GPL-3.0-or-later`, target GitHub repo `https://github.com/djsusmith/hyperstream-android`.
- **Maximum Connections Unlocked:** `MAX_CONNECTIONS_UNLOCKED = 128` (`1..=128` simultaneous connections per download exposed in both Rust `mdm-engine` and Compose UI).
- **EC2 Coexistence Rule (`ubuntu@ec2-13-61-33-233.eu-north-1.compute.amazonaws.com`):** Never stop, kill, or starve `ec2-kiwi-builder` (`Runner.Listener` / `ninja`). All commands on EC2 must run inside `/home/ubuntu/hyperstream-build` prefixed with `nice -n 19 ionice -c 3` and bounded to `-j4` when `ec2-kiwi-builder` is active.
- **Target Android SDK:** `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`, `ndk.abiFilters = ["arm64-v8a"]` (compatible with Android 17 / API 37 on Galaxy S25 Ultra `SM-S938B`).
- **Compose Previews:** Every primary screen must have a corresponding `@Preview` in `com.hyperstream.downloadmanager.ui.preview.HyperStreamPreviewsKt` rendered with the `/compose-preview` sidecar.

## Review Focus

1. **Out-of-range connection counts (`0` or `> 128`) in `AddDownload` command:** `AndroidCoreController::clamp_connections(n)` must clamp any requested connection count into `1..=128` rather than panicking or opening 0 workers (tested in Task 2).
2. **Malformed or unsupported URLs / Magnet links passed via Android Share sheet (`ACTION_SEND`):** `LinkParser.extractDownloadableTarget(rawText)` must extract the first valid `http://`, `https://`, `ftp://`, `ftps://`, or `magnet:?xt=` URI from mixed share text (e.g. `"Check this file https://example.com/a.zip thanks!"`) and return `null` on plain non-URL text without crashing (tested in Task 4).
3. **WakeLock leak when all downloads fail or pause:** `WakeLockCoordinator.shouldHoldLocks(activeDownloads, activeTorrents, activeQueues)` must return `false` immediately when all items transition to `Paused`, `Done`, or `Failed` so the phone's battery is never drained while idle (tested in Task 3).
4. **Shizuku unavailable or permission denied when user toggles Shizuku features:** `ShizukuManager.executePrivileged(cmd)` must return `ShizukuResult.Unavailable` gracefully instead of throwing an unhandled `SecurityException` or `DeadObjectException` (tested in Task 5).
5. **Upstream sync comparing identical vs. newer commit SHAs:** `UpdateChannelManager.isUpdateAvailable(currentVersion, currentUpstreamSha, releaseInfo, channel)` must detect both semver upgrades on `Stable` and upstream commit SHA changes on `Nightly / Bug-Fix` while ignoring self-matching SHAs (tested in Task 5).

---

### Task 1: Upstream Vendor Sync & Workspace Scaffolding

**Files:**
- Create: `.gitignore`
- Create: `scripts/sync-upstream.sh`
- Create: `upstream/UPSTREAM_VERSION.json`
- Copy/Sync: `upstream/` (from `/data/user/0/com.agy/files/.gemini/antigravity-app/brain/ccf7e660-2edd-4364-91c0-481578876221/scratch/misterdownloadmanager`)
- Test: `scripts/test-sync-upstream.sh`

**Interfaces:**
- Produces:
  - `upstream/Cargo.toml` workspace containing `crates/engine`, `crates/torrent`, `crates/debrid`, `crates/extract`, `crates/geo`, `crates/cnl`, `crates/ipc`, `crates/remote`, `crates/tools`, and `i18n/*.tsv`
  - `upstream/UPSTREAM_VERSION.json` with schema `{"upstream_repo": "anefzaoui/misterdownloadmanager", "version": "0.5.2", "commit": "<40-hex-sha>", "synced_at": "<iso8601>"}`
  - `scripts/sync-upstream.sh [--from-dir <path> | --remote]`

- [ ] **Step 1: Write the failing test `scripts/test-sync-upstream.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail
bash scripts/sync-upstream.sh --check-only
test -f upstream/UPSTREAM_VERSION.json
python3 -c '
import json
d = json.load(open("upstream/UPSTREAM_VERSION.json"))
assert d["upstream_repo"] == "anefzaoui/misterdownloadmanager"
assert len(d["commit"]) == 40
assert d["version"] == "0.5.2"
'
test -f upstream/crates/engine/Cargo.toml
test -f upstream/crates/torrent/Cargo.toml
test -f upstream/i18n/en.tsv
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bash scripts/test-sync-upstream.sh`
Expected: FAIL with `scripts/sync-upstream.sh: No such file or directory`

- [ ] **Step 3: Implement `.gitignore`, `scripts/sync-upstream.sh`, and sync `upstream/` + `upstream/UPSTREAM_VERSION.json`**

`scripts/sync-upstream.sh` syncs crates (`engine`, `torrent`, `debrid`, `extract`, `geo`, `cnl`, `ipc`, `remote`, `tools`, `labserver`) and `i18n/` from `/data/user/0/com.agy/files/.gemini/antigravity-app/brain/ccf7e660-2edd-4364-91c0-481578876221/scratch/misterdownloadmanager` (or fetches `https://github.com/anefzaoui/misterdownloadmanager.git` when `--remote` is passed), writes `upstream/UPSTREAM_VERSION.json`, and supports `--check-only`. Ensure `.worktrees/`, `target/`, `android/.gradle/`, `android/build/`, and `android/app/build/` are in `.gitignore`.

- [ ] **Step 4: Run test to verify it passes**

Run: `bash scripts/test-sync-upstream.sh`
Expected: PASS (exit code 0)

- [ ] **Step 5: Commit**

```bash
git add .gitignore scripts/sync-upstream.sh scripts/test-sync-upstream.sh upstream/
git commit -m "chore: vendor upstream misterdownloadmanager crates and sync script"
```

---

### Task 2: Rust JNI Bridge Crate (`upstream/crates/android-ffi` -> `libmdm_android.so`)

**Files:**
- Modify: `upstream/Cargo.toml`
- Create: `upstream/crates/android-ffi/Cargo.toml`
- Create: `upstream/crates/android-ffi/src/lib.rs`
- Create: `upstream/crates/android-ffi/src/controller.rs`
- Create: `upstream/crates/android-ffi/src/models.rs`
- Create: `upstream/crates/android-ffi/src/i18n_catalog.rs`
- Create: `scripts/build-rust-android.sh`

**Interfaces:**
- Consumes: `mdm_engine::{Download, Options, Request, Destination, MAX_CONNECTIONS_UNLOCKED}`, `mdm_torrent`, `mdm_debrid`, `mdm_extract`, `mdm_geo`, `mdm_cnl`, `mdm_ipc`, `mdm_remote`
- Produces:
  - Rust library `mdm_android_ffi` (`cdylib` + `rlib`, output `libmdm_android.so`)
  - `pub fn clamp_connections(requested: u32) -> u32` (clamps into `1..=mdm_engine::download::MAX_CONNECTIONS_UNLOCKED` which is `128`)
  - `pub struct AndroidCoreController` with `pub fn handle_command_json(&self, cmd_json: &str) -> String` and `pub fn snapshot_json(&self) -> String`
  - JNI exports:
    - `Java_com_hyperstream_downloadmanager_core_MdmNative_init(JNIEnv, JClass, JString dataDir, JString downloadsDir) -> jboolean`
    - `Java_com_hyperstream_downloadmanager_core_MdmNative_executeCommand(JNIEnv, JClass, JString cmdJson) -> jstring`
    - `Java_com_hyperstream_downloadmanager_core_MdmNative_getSnapshotJson(JNIEnv, JClass) -> jstring`
    - `Java_com_hyperstream_downloadmanager_core_MdmNative_getLocalesJson(JNIEnv, JClass) -> jstring`

- [ ] **Step 1: Write failing unit tests in `upstream/crates/android-ffi/src/lib.rs`**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clamps_connections_up_to_128_unlocked() {
        assert_eq!(clamp_connections(0), 1);
        assert_eq!(clamp_connections(8), 8);
        assert_eq!(clamp_connections(32), 32);
        assert_eq!(clamp_connections(128), 128);
        assert_eq!(clamp_connections(500), 128);
    }

    #[test]
    fn controller_handles_add_pause_resume_queue_and_settings_commands() {
        let tmp = tempfile::tempdir().unwrap();
        let ctrl = controller::AndroidCoreController::new(
            tmp.path().join("data"),
            tmp.path().join("downloads"),
        ).unwrap();

        // Set max connections to 128 and enable debrid/scheduler settings
        let resp = ctrl.handle_command_json(r#"{"op":"update_settings","max_connections":128,"speed_limit_kbps":0,"shizuku_enabled":true,"update_channel":"nightly"}"#);
        assert!(resp.contains(r#""ok":true"#));

        let snap = ctrl.snapshot_json();
        assert!(snap.contains(r#""max_connections":128"#));
        assert!(snap.contains(r#""update_channel":"nightly""#));
    }

    #[test]
    fn loads_all_86_locales_and_identifies_rtl() {
        let locales = i18n_catalog::supported_locales();
        assert!(locales.len() >= 86);
        assert!(locales.iter().any(|l| l.code == "ar" && l.rtl));
        assert!(locales.iter().any(|l| l.code == "en" && !l.rtl));
    }
}
```

- [ ] **Step 2: Run `cargo test -p mdm-android-ffi` to verify it fails**

Run: `cargo test --manifest-path upstream/Cargo.toml -p mdm-android-ffi`
Expected: FAIL (package `mdm-android-ffi` not yet created)

- [ ] **Step 3: Implement `upstream/crates/android-ffi` (`lib.rs`, `controller.rs`, `models.rs`, `i18n_catalog.rs`) and `scripts/build-rust-android.sh`**

Wire `AndroidCoreController` with a multi-threaded Tokio runtime driving:
- `mdm-engine` downloads (`1..=128` connections, dynamic segment table snapshots `Vec<SegmentDto>`, checksum verification `MD5/SHA-1/SHA-256/CRC32`, Google Drive/MediaFire site resolvers, FTP/FTPS, custom DNS DoH/DoT, proxy/SOCKS5/PAC, network interface binding)
- `mdm-torrent` (`librqbit` session for `.torrent` files and `magnet:` URIs, sequential mode, files/peers/trackers/pieces inspection, RSS feed rules)
- `mdm-debrid` (Real-Debrid, AllDebrid, Premiumize, TorBox, Mega-Debrid link unlocking)
- `mdm-extract` (`zip`, `tar`, `gz`, `xz` extraction + `7z`/`rar` command bridge)
- `mdm-geo` (IP-to-country lookup + flag code)
- `mdm-cnl` (`127.0.0.1:9666` Click'n'Load 2 listener) and `mdm-remote` (`8463` paired phone/PC HTTPS server)
- Categories, Queues (`Main Queue`, `Secondary Queue`, custom queues), and Overnight Scheduler state persisted atomically to `downloads.json`.

- [ ] **Step 4: Run `cargo test -p mdm-android-ffi` to verify all tests pass**

Run: `cargo test --manifest-path upstream/Cargo.toml -p mdm-android-ffi`
Expected: PASS (`3 passed; 0 failed`)

- [ ] **Step 5: Commit**

```bash
git add upstream/Cargo.toml upstream/crates/android-ffi scripts/build-rust-android.sh
git commit -m "feat(rust-ffi): add mdm-android-ffi JNI bridge for 128-thread engine, torrent, debrid, and 86 locales"
```

---

### Task 3: Android Gradle Project, Core Repository, & Foreground Service with WakeLock

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/core/MdmModels.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/core/MdmNative.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/core/MdmRepository.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/service/WakeLockCoordinator.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/service/HyperStreamDownloadService.kt`
- Test: `android/app/src/test/java/com/hyperstream/downloadmanager/service/WakeLockCoordinatorTest.kt`

**Interfaces:**
- Consumes: `MdmNative` JNI exports from Task 2
- Produces:
  - `MdmUiState`, `DownloadItemUi`, `SegmentUi`, `TorrentItemUi`, `QueueUi`, `SchedulerConfigUi`, `SettingsUi`, `LocaleUi` in `com.hyperstream.downloadmanager.core`
  - `WakeLockCoordinator.shouldHoldLocks(downloads: List<DownloadItemUi>, torrents: List<TorrentItemUi>, queues: List<QueueUi>): Boolean`
  - `HyperStreamDownloadService` holding `PowerManager.PARTIAL_WAKE_LOCK` (`"HyperStream::DownloadWakeLock"`) and `WifiManager.WifiLock(WIFI_MODE_FULL_HIGH_PERF, "HyperStream::DownloadWifiLock")`

- [ ] **Step 1: Write the failing test `WakeLockCoordinatorTest.kt`**

```kotlin
package com.hyperstream.downloadmanager.service

import com.hyperstream.downloadmanager.core.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeLockCoordinatorTest {
    @Test
    fun holdsWakeLockOnlyWhenDownloadTorrentOrQueueIsActive() {
        val idleDownload = DownloadItemUi(id = 1, name = "a.zip", url = "https://example.com/a.zip", status = "Paused")
        val activeDownload = idleDownload.copy(status = "Downloading", activeConnections = 128)
        assertFalse(WakeLockCoordinator.shouldHoldLocks(listOf(idleDownload), emptyList(), emptyList()))
        assertTrue(WakeLockCoordinator.shouldHoldLocks(listOf(activeDownload), emptyList(), emptyList()))
    }

    @Test
    fun releasesWakeLockWhenAllTransfersFinishOrFail() {
        val done = DownloadItemUi(id = 1, name = "a.zip", url = "https://example.com/a.zip", status = "Done")
        val failed = DownloadItemUi(id = 2, name = "b.zip", url = "https://example.com/b.zip", status = "Failed")
        assertFalse(WakeLockCoordinator.shouldHoldLocks(listOf(done, failed), emptyList(), emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle -p android :app:testDebugUnitTest --tests com.hyperstream.downloadmanager.service.WakeLockCoordinatorTest`
Expected: FAIL (project/classes not yet created)

- [ ] **Step 3: Implement Gradle build files, `AndroidManifest.xml`, `MdmModels.kt`, `MdmNative.kt`, `MdmRepository.kt`, `WakeLockCoordinator.kt`, and `HyperStreamDownloadService.kt`**

Configure `android/app/build.gradle.kts` with Jetpack Compose, Material 3, `kotlinx.serialization`, and JUnit 4. `MdmNative` loads `libmdm_android.so` when running on device and falls back gracefully in JVM unit/preview environments. `HyperStreamDownloadService` manages `PARTIAL_WAKE_LOCK` + `WIFI_MODE_FULL_HIGH_PERF` via `WakeLockCoordinator` and posts live multi-download progress notifications.

- [ ] **Step 4: Run unit test to verify it passes**

Run: `gradle -p android :app:testDebugUnitTest --tests com.hyperstream.downloadmanager.service.WakeLockCoordinatorTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/
git commit -m "feat(android-core): scaffold Gradle project, JNI repository, and ForegroundService with WakeLock"
```

---

### Task 4: Automatic Link Interception, In-App Video Browser & Browser Extension Bridge

**Files:**
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/browser/LinkParser.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/browser/LinkCatcherActivity.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/browser/VideoGrabberWebView.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/browser/ClipboardLinkMonitor.kt`
- Test: `android/app/src/test/java/com/hyperstream/downloadmanager/browser/LinkParserTest.kt`

**Interfaces:**
- Consumes: `MdmRepository` from Task 3
- Produces:
  - `LinkParser.extractDownloadableTarget(rawText: String?): ParsedLinkTarget?`
  - `LinkParser.isLikelyDirectDownload(url: String, mimeType: String?): Boolean`
  - `LinkCatcherActivity` (registered in `AndroidManifest.xml` for `ACTION_VIEW` and `ACTION_SEND` on `http`, `https`, `ftp`, `ftps`, `magnet`, `application/x-bittorrent`, and downloadable extensions)
  - `VideoGrabberWebView` (Composable WebView with JS media sniffer detecting `<video>`, `.m3u8`, `.mpd`, `.mp4`, `.webm` and displaying MDM's floating **"Download Video"** button)

- [ ] **Step 1: Write the failing test `LinkParserTest.kt`**

```kotlin
package com.hyperstream.downloadmanager.browser

import org.junit.Assert.*
import org.junit.Test

class LinkParserTest {
    @Test
    fun extractsUrlAndMagnetFromSharedText() {
        val shared = "Download ISO here: https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso fast!"
        val target = LinkParser.extractDownloadableTarget(shared)
        assertNotNull(target)
        assertEquals("https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso", target?.uri)
        assertEquals(LinkKind.DIRECT_FILE, target?.kind)

        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=debian"
        val magnetTarget = LinkParser.extractDownloadableTarget("Here is the magnet $magnet enjoy")
        assertEquals(LinkKind.MAGNET, magnetTarget?.kind)

        assertNull(LinkParser.extractDownloadableTarget("hello world with no link"))
    }

    @Test
    fun identifiesVideoPagesVsDirectArchives() {
        val yt = LinkParser.extractDownloadableTarget("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(LinkKind.VIDEO_STREAM_PAGE, yt?.kind)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle -p android :app:testDebugUnitTest --tests com.hyperstream.downloadmanager.browser.LinkParserTest`
Expected: FAIL (`LinkParser` not defined)

- [ ] **Step 3: Implement `LinkParser.kt`, `LinkCatcherActivity.kt`, `VideoGrabberWebView.kt`, and `ClipboardLinkMonitor.kt`**

Implement regex/URI extraction in `LinkParser`, wire `LinkCatcherActivity` to open a fast bottom-sheet dialog that probes size/country/resumability and starts the download with up to 128 threads, and implement `VideoGrabberWebView` with an injected `MutationObserver` + resource sniffer that surfaces the draggable **"Download Video"** pill over any detected HTML5 video or HLS/DASH stream.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle -p android :app:testDebugUnitTest --tests com.hyperstream.downloadmanager.browser.LinkParserTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/
git commit -m "feat(browser-catch): add automatic link interception, Share handler, and floating video grabber"
```

---

### Task 5: Optional Shizuku Integration & Dual-Channel Upstream Updater

**Files:**
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/shizuku/ShizukuManager.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/updater/UpdateChannelManager.kt`
- Test: `android/app/src/test/java/com/hyperstream/downloadmanager/updater/UpdateChannelAndShizukuTest.kt`

**Interfaces:**
- Produces:
  - `ShizukuManager` with `whitelistFromDoze(): ShizukuResult`, `silentInstallApk(apkPath: String): ShizukuResult`, `moveToRestrictedDir(src: String, dest: String): ShizukuResult`, `runHelperBinary(bin: String, args: List<String>): ShizukuResult`
  - `UpdateChannelManager.isUpdateAvailable(currentVersion: String, currentUpstreamSha: String, release: GithubReleaseDto, channel: UpdateChannel): Boolean`

- [ ] **Step 1: Write the failing test `UpdateChannelAndShizukuTest.kt`**

```kotlin
package com.hyperstream.downloadmanager.updater

import com.hyperstream.downloadmanager.shizuku.ShizukuManager
import com.hyperstream.downloadmanager.shizuku.ShizukuResult
import org.junit.Assert.*
import org.junit.Test

class UpdateChannelAndShizukuTest {
    @Test
    fun detectsStableSemverAndNightlyUpstreamCommitUpdates() {
        val stableRel = GithubReleaseDto(
            tagName = "v0.5.3",
            prerelease = false,
            upstreamCommit = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            apkDownloadUrl = "https://github.com/djsusmith/hyperstream-android/releases/download/v0.5.3/HyperStream.apk"
        )
        assertTrue(UpdateChannelManager.isUpdateAvailable("0.5.2", "1111111111111111111111111111111111111111", stableRel, UpdateChannel.STABLE))
        assertFalse(UpdateChannelManager.isUpdateAvailable("0.5.3", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", stableRel, UpdateChannel.STABLE))

        val nightlyRel = GithubReleaseDto(
            tagName = "nightly",
            prerelease = true,
            upstreamCommit = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            apkDownloadUrl = "https://github.com/djsusmith/hyperstream-android/releases/download/nightly/HyperStream-nightly.apk"
        )
        assertTrue(UpdateChannelManager.isUpdateAvailable("0.5.2", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", nightlyRel, UpdateChannel.NIGHTLY_BUGFIX))
        assertFalse(UpdateChannelManager.isUpdateAvailable("0.5.2", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", nightlyRel, UpdateChannel.NIGHTLY_BUGFIX))
    }

    @Test
    fun shizukuGracefullyReportsUnavailableWhenDisabledOrMissing() {
        val mgr = ShizukuManager(enabled = false, commandRunner = { -1 to "not running" })
        val res = mgr.whitelistFromDoze()
        assertTrue(res is ShizukuResult.Unavailable)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle -p android :app:testDebugUnitTest --tests com.hyperstream.downloadmanager.updater.UpdateChannelAndShizukuTest`
Expected: FAIL

- [ ] **Step 3: Implement `ShizukuManager.kt` and `UpdateChannelManager.kt`**

Implement `ShizukuManager` using `rish -c "<cmd>"` / `sh` via Shizuku binder when enabled, with commands for Doze whitelisting (`dumpsys deviceidle whitelist +com.hyperstream.downloadmanager`), silent APK installation (`pm install -r`), `/Android/data` file copy, and native `yt-dlp`/`ffmpeg`/`7z` execution. Implement `UpdateChannelManager` querying `https://api.github.com/repos/djsusmith/hyperstream-android/releases` for `Stable` (`vX.Y.Z`) and `Bug-Fix / Nightly` (`nightly` tag with `upstream_commit` metadata in release notes).

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle -p android :app:testDebugUnitTest --tests com.hyperstream.downloadmanager.updater.UpdateChannelAndShizukuTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/
git commit -m "feat(shizuku-updater): add optional Shizuku integration and Stable/Nightly upstream update channels"
```

---

### Task 6: Jetpack Compose Material 3 UI, MDM Themes, & `/compose-preview` Verification

**Files:**
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/theme/HyperStreamTheme.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/components/SegmentMapBar.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/DownloadsScreen.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/ProgressInspectorSheet.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/TorrentsAndRssScreen.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/GrabberScreen.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/QueuesAndSchedulerScreen.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/screens/SettingsAndUpdaterScreen.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/HyperStreamApp.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/MainActivity.kt`
- Create: `android/app/src/main/java/com/hyperstream/downloadmanager/ui/preview/HyperStreamPreviews.kt`

**Interfaces:**
- Consumes: `MdmRepository`, `VideoGrabberWebView`, `ShizukuManager`, `UpdateChannelManager`
- Produces:
  - Full 5-tab Compose UI (`Downloads`, `Torrents & RSS`, `Video & Link Grabber`, `Queues & Scheduler`, `Settings & Channels`)
  - `SegmentMapBar(segments: List<SegmentUi>, maxConnections: Int)` rendering up to 128 live parallel worker segments
  - `@Preview` functions in `com.hyperstream.downloadmanager.ui.preview.HyperStreamPreviewsKt`:
    - `MainDownloadsDashboardPreview`
    - `ProgressInspector128ThreadsPreview`
    - `TorrentSwarmInspectorPreview`
    - `VideoAndBatchGrabberPreview`
    - `QueuesAndSchedulerPreview`
    - `SettingsShizukuUpdaterPreview`

- [ ] **Step 1: Implement the Compose theme, components, screens, `MainActivity.kt`, and `@Preview` functions in `HyperStreamPreviews.kt`**

Build the complete Material 3 + MDM Theme UI with:
- Category filter rail/chips (`All`, `Unfinished`, `Finished`, `Video`, `Music`, `Programs`, `Compressed`, `Documents`, `Torrents`)
- Live 128-thread segment visualizer bar (`SegmentMapBar`) + Country Flag badge + Speed/ETA indicators
- Add URL / Add Magnet / Batch Link Grabber dialogs with connection slider (`1..128`), Category, Queue, Referer, Cookies, and Checksum input
- Detailed Progress Inspector Sheet (Connections table, speed graph, checksum verifier)
- Torrent Inspector (Files tree with checkboxes, Peers list, Trackers, Pieces bitmap, Seeding limits, RSS feed auto-downloader)
- Queues & Overnight Scheduler (start/stop times, active days, concurrent limit, post-queue actions)
- Settings (128-thread slider, Speed Limiter, Proxy/SOCKS5/PAC, Custom DNS DoH/DoT, VPN interface lock + Kill Switch, Debrid accounts for Real-Debrid/AllDebrid/Premiumize/TorBox/Mega-Debrid, Shizuku Enhanced Mode toggle + one-tap actions, 86-language flag picker with RTL support, Theme picker, Remote Control pairing QR code, and `Stable` vs `Bug-Fix / Nightly` Upstream Updater).

- [ ] **Step 2: Compile and run unit tests to verify UI & state integration**

Run: `gradle -p android :app:testDebugUnitTest`
Expected: PASS (`BUILD SUCCESSFUL`)

- [ ] **Step 3: Render `@Preview` functions via `/compose-preview` sidecar**

Configure `/compose-preview` session for `android` (`gradle://.?:app@debug`) and render `com.hyperstream.downloadmanager.ui.preview.HyperStreamPreviewsKt.MainDownloadsDashboardPreview` and `ProgressInspector128ThreadsPreview`, verifying zero render errors.

- [ ] **Step 4: Commit**

```bash
git add android/
git commit -m "feat(ui): implement Jetpack Compose Material 3 UI, 128-segment visualizer, and @Preview suite"
```

---

### Task 7: End-to-End Rust NDK + APK Build, CI/CD Upstream Sync Workflow, GitHub Push & Device Install

**Files:**
- Create: `.github/workflows/android-ci.yml`
- Create: `.github/workflows/upstream-sync.yml`
- Create: `README.md`

**Interfaces:**
- Consumes: All tasks 1–6
- Produces:
  - Compiled `arm64-v8a/libmdm_android.so` bundled inside signed `HyperStream-v0.5.2-arm64.apk`
  - Published GitHub repository `https://github.com/djsusmith/hyperstream-android` with active `android-ci.yml` and 6-hour `upstream-sync.yml` (`Stable` + `nightly` Bug-Fix releases)
  - Installed & verified `com.hyperstream.downloadmanager` on the local Galaxy S25 Ultra (`SM-S938B`) and exported to `/storage/emulated/0/Download/HyperStream-v0.5.2-arm64.apk`

- [ ] **Step 1: Create `.github/workflows/android-ci.yml`, `.github/workflows/upstream-sync.yml`, and `README.md`**

`upstream-sync.yml` runs every 6 hours (`cron: "0 */6 * * *"`) and on `workflow_dispatch`, runs `bash scripts/sync-upstream.sh --remote`, builds `libmdm_android.so` (`aarch64-linux-android`) and the signed Android APK, and publishes to the `nightly` (Bug-Fix) release (and `stable` release when upstream version increments).

- [ ] **Step 2: Build `libmdm_android.so` and signed `HyperStream-v0.5.2-arm64.apk` (using EC2 `ubuntu@ec2-13-61-33-233.eu-north-1.compute.amazonaws.com` with `nice -n 19 ionice -c 3` in `/home/ubuntu/hyperstream-build` without disturbing `ec2-kiwi-builder`)**

Verify `ec2-kiwi-builder` is untouched before and after the build, pull the compiled `libmdm_android.so` and signed APK back to the device, copy to `/storage/emulated/0/Download/HyperStream-v0.5.2-arm64.apk` and `/storage/emulated/0/Documents/HyperStream-v0.5.2-arm64.apk`, and install on the phone via `rish -c "pm install -r /storage/emulated/0/Download/HyperStream-v0.5.2-arm64.apk"`.

- [ ] **Step 3: Create GitHub repository `djsusmith/hyperstream-android`, push branch/main, and publish initial `v0.5.2` (Stable) and `nightly` (Bug-Fix) releases**

Run: `git push -u origin main` and upload the signed APK to GitHub Releases.

- [ ] **Step 4: Commit all remaining files**

```bash
git add .github/workflows/ README.md
git commit -m "ci: add automated upstream-sync (Stable + Nightly Bug-Fix) and Android release workflows"
```
