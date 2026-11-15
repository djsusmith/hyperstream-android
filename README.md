# HyperStream Download Manager for Android

**HyperStream** (`com.hyperstream.downloadmanager`) is the native Android port of [Mister Download Manager (`anefzaoui/misterdownloadmanager`)](https://github.com/anefzaoui/misterdownloadmanager), combining the upstream Rust multi-connection download & torrent core (`libmdm_android.so`) with a native Jetpack Compose Material 3 UI.

---

## Key Features (1-to-1 Upstream Parity + Android Native Power)

- **128-Thread Segmented Engine (`mdm-engine`):**
  - Unlocked `1..=128` simultaneous connections per file (`MAX_CONNECTIONS_UNLOCKED = 128`).
  - Dynamic work-stealing segment halving (`min_split = 12 KB`), slow-connection shedding, crash-safe `.mdm.part` atomic resume (`craft-atomic`), and whole-file checksum verification (`CRC32`, `MD5`, `SHA-1`, `SHA-256`, `SHA-512`).
  - Built-in Google Drive & MediaFire confirmation page resolvers, proxy (`HTTP`/`HTTPS`/`SOCKS5`/PAC script via `rquickjs`), custom DNS (`DoH`/`DoT` via `hickory-resolver`), and VPN interface lock + kill switch.
  - Offline DB-IP server country lookup & flag badge (`mdm-geo`).
- **Full BitTorrent & RSS Engine (`mdm-torrent` / `librqbit`):**
  - Magnet links & `.torrent` files, DHT, PEX, LSD, UPnP/NAT-PMP, sequential streaming mode, per-file selection, peer/tracker/piece-bitmap inspector, seeding ratio/time limits, and regex RSS auto-downloader.
- **Automatic Android Link Interception & Video Grabber:**
  - Catches `http`, `https`, `ftp`, `ftps`, `magnet:`, and `.torrent` links automatically via Android `ACTION_VIEW` & Share Sheet (`ACTION_SEND`), plus optional Clipboard Monitor (`ClipboardLinkMonitor`).
  - Built-in `VideoGrabberWebView` with DOM & network stream sniffer (`<video>`, `.m3u8`, `.mpd`, `.mp4`, `.webm`) and MDM's floating **"Download Video"** pill.
  - Batch pattern generator (`[001-100]`, `[a-z]`) and JDownloader **Click'n'Load 2** (`127.0.0.1:9666`, `mdm-cnl`).
- **Debrid & Archive Extraction (`mdm-debrid`, `mdm-extract`):**
  - Native support for Real-Debrid, AllDebrid, Premiumize, TorBox, and Mega-Debrid.
  - Automatic post-download extraction for `.zip`, `.tar`, `.tar.gz`, `.tar.xz`, `.gz`, `.xz`, `.7z`, and `.rar`.
- **Foreground Service with CPU WakeLock & High-Perf Wi-Fi Lock:**
  - `HyperStreamDownloadService` + `WakeLockCoordinator` automatically hold `PowerManager.PARTIAL_WAKE_LOCK` and `WifiManager.WIFI_MODE_FULL_HIGH_PERF` while any download, torrent, or scheduled queue is running, releasing immediately when idle.
- **Optional Shizuku Enhanced Mode (`ShizukuManager`):**
  - Toggleable `uid=2000` ADB privileges via Shizuku (`rish`) for 1-tap Doze whitelisting, silent APK self-updating (`pm install -r`), `/Android/data` access, and native helper binary execution.
- **86 Languages (with RTL) & Dual Upstream Update Channels:**
  - Bundles all 86 upstream `.tsv` locale catalogs (including RTL layout mirroring for Arabic, Hebrew, Persian, Urdu, and Uyghur).
  - Automated GitHub Actions workflow (`.github/workflows/upstream-sync.yml`) polls `anefzaoui/misterdownloadmanager` every 6 hours and publishes **Stable** (`vX.Y.Z`) and **Bug-Fix / Nightly** (`nightly`) releases.

---

## Project Structure

- [`upstream/`](upstream/) — Vendored upstream Rust crates (`engine`, `torrent`, `debrid`, `extract`, `geo`, `cnl`, `ipc`, `remote`, `tools`, `labserver`, `cli`), 86-locale `i18n/*.tsv`, and [`upstream/crates/android-ffi`](upstream/crates/android-ffi) (`libmdm_android.so` JNI bridge).
- [`android/`](android/) — Android Gradle project (`com.hyperstream.downloadmanager`) with Jetpack Compose Material 3 UI, `HyperStreamDownloadService`, `LinkCatcherActivity`, `VideoGrabberWebView`, `ShizukuManager`, and `UpdateChannelManager`.
- [`scripts/`](scripts/) — [`sync-upstream.sh`](scripts/sync-upstream.sh), [`test-sync-upstream.sh`](scripts/test-sync-upstream.sh), and [`build-rust-android.sh`](scripts/build-rust-android.sh).
- [`.github/workflows/`](.github/workflows/) — [`android-ci.yml`](.github/workflows/android-ci.yml) and 6-hour [`upstream-sync.yml`](.github/workflows/upstream-sync.yml).

---

## License

GPL-3.0-or-later (matching upstream [Mister Download Manager](https://github.com/anefzaoui/misterdownloadmanager)).
