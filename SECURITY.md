# Security Policy

HyperStream is an open-source, high-performance download accelerator and BitTorrent client engineered for Android. Because HyperStream handles network traffic, file I/O, background services, and optional elevated system capabilities (via Shizuku), we treat security and user privacy as foundational priorities.

---

## Supported Versions

Security updates and vulnerability patches are actively provided for the following releases:

| Version | Channel | Supported | Notes |
| :--- | :--- | :---: | :--- |
| **v0.6.x** | **Stable** | ✅ Yes | Current flagship production release |
| **Nightly** | **Bug-Fix** | ✅ Yes | Continuous rolling builds from `main` |
| < v0.6.0 | Legacy | ❌ No | Please upgrade to the latest stable release |

---

## Reporting a Vulnerability

We deeply appreciate the efforts of security researchers and community contributors who practice responsible disclosure.

> [!IMPORTANT]
> **Please DO NOT report security vulnerabilities via public GitHub issues, discussions, or pull requests.**

### How to Submit a Private Security Report

You can report vulnerabilities securely through either of the following channels:

1. **GitHub Private Vulnerability Advisory (Preferred):**
   Navigate to the repository's [**Security** tab](https://github.com/djsusmith/hyperstream-android/security/advisories) and click **"Report a vulnerability"**.

2. **Direct Security Contact:**
   Send an encrypted or plain email to the project maintainer:
   - **Contact:** Ahnaf Hasan Susmith
   - **Email:** [`susdj.96@gmail.com`](mailto:susdj.96@gmail.com)
   - **Subject:** `[HyperStream Security] <Brief description of vulnerability>`

### What to Include in Your Report

To help us investigate and remediate the issue promptly, please include:
- A clear description of the vulnerability and its potential impact.
- Exact steps to reproduce the issue (including sample URLs, torrent files, or mock intents where applicable).
- A minimal Proof of Concept (PoC) or reproducible test case.
- Affected device environment (Android OS version, device model, HyperStream build number).
- Any proposed mitigations or patch suggestions (optional but welcomed).

### Response Timelines & SLA

- **Initial Triage:** Within **24 to 48 hours**, we will confirm receipt of your report and begin technical assessment.
- **Progress Updates:** You will receive status updates at least every **48 to 72 hours** while the issue is being investigated and patched.
- **Coordinated Disclosure:** We adhere to standard coordinated disclosure principles (typically 30–90 days) to ensure affected users can update before technical details are made public.
- **Credit:** With your permission, we will publicly acknowledge your contribution in the release notes and advisory credits.

---

## Security Architecture & Design Guarantees

HyperStream is built with a defense-in-depth posture:

### 1. Memory Safety via Native Rust Core
- All high-throughput networking, chunk assembly, segment halving, BitTorrent protocol processing, and archive extraction reside in the native Rust engine (`libmdm_android.so`).
- Rust eliminates entire classes of critical vulnerabilities at compile-time, including:
  - Buffer overflows and over-reads
  - Use-after-free and double-free conditions
  - Race conditions in multi-threaded concurrency
  - Null pointer dereferences

### 2. Zero-Trust Local Processing (100% Privacy)
- **Zero Third-Party Advertising SDKs:** No ad banners, interstitials, or tracking ad libraries.
- **Zero User Profiling or Behavioral Telemetry:** HyperStream never tracks user identities, downloaded file contents, torrent swarm activities, or browsing habits.
- **Local IPC Only:** Communication between the Jetpack Compose Android UI and the Rust daemon uses local, in-process JNI or authenticated localhost loopback (`127.0.0.1:9666`). No ports are exposed to external network interfaces.

### 3. Android Platform & Scoped Storage Hardening
- **Strict Content Provider Grants:** File access and sharing outside HyperStream's private sandbox are strictly mediated via AndroidX `FileProvider` with temporary per-URI read grants (`FLAG_GRANT_READ_URI_PERMISSION`). No raw `file://` URIs are exposed, eliminating `FileUriExposedException` vulnerabilities.
- **Permanent Deletion Confirmation:** Deleting downloads requires explicit user confirmation via an `AlertDialog`. When storage deletion is checked, physical files and incomplete `.mdm.part` fragments are permanently erased to prevent orphaned data residue.
- **Network Security Configuration:** Cleartext HTTP traffic is only permitted when explicitly downloading from non-HTTPS sources specified by the user. In-app update checks and API calls strictly enforce HTTPS/TLS 1.3 with system certificate validation.

### 4. Shizuku (`rish`) Elevated Privilege Isolation
- Shizuku integration (`uid=2000` ADB shell) is strictly **optional** and disabled by default.
- It requires affirmative user authorization through the Shizuku system service dialog.
- Elevated commands are locked to internal, audited operations:
  - Android Doze battery optimization whitelisting (`dumpsys deviceidle whitelist +...`)
  - User-initiated APK self-updates (`pm install -r <path>`)
- Shell commands avoid unquoted string interpolation to prevent shell injection or privilege escalation vulnerabilities.

### 5. Integrity Verification & Cryptographic Standards
- **Hardware-Accelerated Checksum Engines:** Supports automatic pre- and post-download hash verification using `CRC32`, `MD5`, `SHA-1`, `SHA-256`, and `SHA-512`.
- **Encrypted DNS & Proxy Support:** Native support for DNS-over-HTTPS (DoH) and DNS-over-TLS (DoT) via `hickory-resolver` prevents ISP tampering, DNS spoofing, and man-in-the-middle (MitM) cache poisoning.
- **VPN Kill Switch:** When VPN interface binding is enabled, traffic drops immediately if the secure VPN tunnel disconnects.

---

## Binary Integrity & Release Signing

Official HyperStream APKs released on GitHub are cryptographically signed using a dedicated **4096-bit RSA** private keystore:
- **Signature Schemes:** V1 (JAR), V2 (Full APK), V3 (Key Rotation), and V4 (Streaming).
- **Certificate Subject:** `CN=HyperStream, OU=Engineering, O=HyperStream, L=Dhaka, ST=Dhaka, C=BD`

### Verifying APK Authenticity
You can verify the cryptographic signature of any downloaded HyperStream APK using Android's official `apksigner` tool:

```bash
apksigner verify --verbose --print-certs HyperStream-v0.6.0-arm64.apk
```

Expected output includes:
```text
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Signer #1 certificate DN: CN=HyperStream, OU=Engineering, O=HyperStream, ...
```

---

## Upstream Synchronization & Patching

HyperStream maintains continuous 6-hour automated synchronization with the upstream [Mister Download Manager (`anefzaoui/misterdownloadmanager`)](https://github.com/anefzaoui/misterdownloadmanager) repository. Any upstream dependency updates, security advisories, or protocol patches are pulled, tested, and published to the **Nightly / Bug-Fix** channel automatically via GitHub Actions.
