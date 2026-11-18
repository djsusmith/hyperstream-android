//! HyperStream Android JNI FFI entrypoints (`libmdm_android.so`).
#![allow(unsafe_code)]

pub mod controller;
pub mod extractors;
pub mod i18n_catalog;
pub mod models;

use std::path::PathBuf;
use std::sync::{OnceLock, RwLock};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jstring};

use crate::controller::AndroidCoreController;

static GLOBAL_CONTROLLER: OnceLock<RwLock<Option<AndroidCoreController>>> = OnceLock::new();

fn controller_slot() -> &'static RwLock<Option<AndroidCoreController>> {
    GLOBAL_CONTROLLER.get_or_init(|| RwLock::new(None))
}

/// Clamps any requested connection count into `1..=MAX_CONNECTIONS_UNLOCKED` (`128`).
pub fn clamp_connections(requested: u32) -> u32 {
    requested.clamp(1, mdm_engine::download::MAX_CONNECTIONS_UNLOCKED)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_hyperstream_downloadmanager_core_MdmNative_init(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    data_dir: JString<'_>,
    downloads_dir: JString<'_>,
) -> jboolean {
    let Ok(data_str) = env.get_string(&data_dir) else {
        return JNI_FALSE;
    };
    let Ok(dl_str) = env.get_string(&downloads_dir) else {
        return JNI_FALSE;
    };
    let data_path = PathBuf::from(String::from(data_str));
    let dl_path = PathBuf::from(String::from(dl_str));

    match AndroidCoreController::new(data_path, dl_path) {
        Ok(ctrl) => {
            if let Ok(mut guard) = controller_slot().write() {
                *guard = Some(ctrl);
                return JNI_TRUE;
            }
            JNI_FALSE
        }
        Err(_) => JNI_FALSE,
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_hyperstream_downloadmanager_core_MdmNative_executeCommand<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    cmd_json: JString<'local>,
) -> jstring {
    let cmd_str: String = env
        .get_string(&cmd_json)
        .map(Into::into)
        .unwrap_or_default();
    let reply = if let Ok(guard) = controller_slot().read()
        && let Some(ctrl) = guard.as_ref()
    {
        ctrl.handle_command_json(&cmd_str)
    } else {
        r#"{"ok":false,"error":"Controller not initialized"}"#.to_string()
    };

    env.new_string(reply)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_hyperstream_downloadmanager_core_MdmNative_getSnapshotJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let snap = if let Ok(guard) = controller_slot().read()
        && let Some(ctrl) = guard.as_ref()
    {
        ctrl.snapshot_json()
    } else {
        "{}".to_string()
    };

    env.new_string(snap)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_hyperstream_downloadmanager_core_MdmNative_getLocalesJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let locales = i18n_catalog::supported_locales();
    let json = serde_json::to_string(&locales).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

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
        )
        .unwrap();

        let resp = ctrl.handle_command_json(
            r#"{"op":"update_settings","max_connections":128,"speed_limit_kbps":0,"shizuku_enabled":true,"update_channel":"nightly"}"#,
        );
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

    #[test]
    fn extracts_youtube_video_id_and_infers_video_filename() {
        assert_eq!(
            extractors::detect_platform("https://youtube.com/watch?v=paZ0RGJ0cj0"),
            "YouTube"
        );
        assert_eq!(
            extractors::detect_platform("https://vm.tiktok.com/ZM12345/"),
            "TikTok"
        );
        assert_eq!(
            extractors::detect_platform("https://www.instagram.com/reel/C9abc/"),
            "Instagram"
        );
        assert_eq!(
            extractors::detect_platform("https://fb.watch/xyz123/"),
            "Facebook"
        );
        assert_eq!(
            extractors::extract_youtube_video_id(
                "https://youtube.com/watch?v=paZ0RGJ0cj0&is=dFhWHpvEd4amMzs6"
            ),
            Some("paZ0RGJ0cj0".to_string())
        );
        assert_eq!(
            extractors::extract_youtube_video_id("https://youtu.be/aqz-KE-bpKQ?si=123"),
            Some("aqz-KE-bpKQ".to_string())
        );
        assert_eq!(
            extractors::infer_smart_filename(
                "https://youtube.com/watch?v=paZ0RGJ0cj0&is=dFhWHpvEd4amMzs6"
            ),
            "YouTube_paZ0RGJ0cj0.mp4"
        );
    }

    #[test]
    fn builds_mp3_quality_variants_for_probed_media() {
        let mp3s = extractors::build_mp3_variants(
            "Trillion Dollar Scandal",
            "https://cdn.example.com/audio.m4a",
            10_000_000,
            "TestUA",
        );
        assert_eq!(mp3s.len(), 3);
        assert_eq!(mp3s[0]["bitrate_kbps"], 320);
        assert_eq!(mp3s[0]["ext"], "mp3");
        assert_eq!(mp3s[0]["category"], "Music");
        assert!(
            mp3s[0]["file_name"]
                .as_str()
                .unwrap()
                .ends_with("(320kbps).mp3")
        );
        assert_eq!(mp3s[1]["bitrate_kbps"], 192);
        assert_eq!(mp3s[2]["bitrate_kbps"], 128);
    }

    #[test]
    fn main_queue_downloads_start_immediately_while_overnight_queues() {
        let tmp = tempfile::tempdir().unwrap();
        let ctrl = controller::AndroidCoreController::new(
            tmp.path().join("data"),
            tmp.path().join("downloads"),
        )
        .unwrap();

        let _ = ctrl.handle_command_json(
            r#"{"op":"add_download","url":"https://example.com/test.mp4","connections":32,"category":"Video","queue":"Main"}"#,
        );
        let _ = ctrl.handle_command_json(
            r#"{"op":"add_download","url":"https://example.com/night.iso","connections":32,"category":"Programs","queue":"Overnight"}"#,
        );

        let snap = ctrl.snapshot();
        let main_dl = snap.downloads.iter().find(|d| d.name == "test.mp4").unwrap();
        let night_dl = snap.downloads.iter().find(|d| d.name == "night.iso").unwrap();
        assert_eq!(main_dl.status, "Downloading");
        assert_eq!(night_dl.status, "Queued");
    }

    #[test]
    fn refresh_link_updates_download_url_and_resumes_paused_or_failed() {
        let tmp = tempfile::tempdir().unwrap();
        let ctrl = controller::AndroidCoreController::new(
            tmp.path().join("data"),
            tmp.path().join("downloads"),
        )
        .unwrap();

        let add_res = ctrl.handle_command_json(
            r#"{"op":"add_download","url":"https://example.com/expired-stream-url.mp4","connections":16,"category":"Video"}"#,
        );
        let add_json: serde_json::Value = serde_json::from_str(&add_res).unwrap();
        let id = add_json["id"].as_u64().unwrap();

        // Pause it
        let _ = ctrl.handle_command_json(&format!(r#"{{"op":"pause_download","id":{id}}}"#));

        // Refresh with fresh signed URL
        let refresh_res = ctrl.handle_command_json(&format!(
            r#"{{"op":"refresh_link","id":{id},"new_url":"https://example.com/fresh-signed-url.mp4"}}"#
        ));
        let refresh_json: serde_json::Value = serde_json::from_str(&refresh_res).unwrap();
        assert_eq!(refresh_json["ok"], true);
        assert_eq!(refresh_json["resumed"], true);

        let snap = ctrl.snapshot();
        let dl = snap.downloads.iter().find(|d| d.id == id).unwrap();
        assert_eq!(dl.url, "https://example.com/fresh-signed-url.mp4");
    }
}
