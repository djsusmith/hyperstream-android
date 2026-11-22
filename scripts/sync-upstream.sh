#!/data/user/0/com.agy/files/usr/bin/bash
set -euo pipefail
export GIT_CONFIG_NOSYSTEM=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT_DIR"

MODE="local"
SRC_DIR="/data/user/0/com.agy/files/.gemini/antigravity-app/brain/ccf7e660-2edd-4364-91c0-481578876221/scratch/misterdownloadmanager"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-only)
      MODE="check"
      shift
      ;;
    --remote)
      MODE="remote"
      shift
      ;;
    --from-dir)
      SRC_DIR="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

if [[ "$MODE" == "check" ]]; then
  test -f "$ROOT_DIR/upstream/UPSTREAM_VERSION.json"
  test -f "$ROOT_DIR/upstream/Cargo.toml"
  exit 0
fi

TMP_CLONE=""
if [[ "$MODE" == "remote" || ! -d "$SRC_DIR/.git" ]]; then
  TMP_CLONE="$(mktemp -d)"
  git clone --depth 1 https://github.com/anefzaoui/misterdownloadmanager.git "$TMP_CLONE"
  SRC_DIR="$TMP_CLONE"
fi

COMMIT_SHA="$(git -C "$SRC_DIR" rev-parse HEAD)"
VERSION="$(python3 -c '
import re
txt = open("'"$SRC_DIR"'/Cargo.toml").read()
m = re.search(r"version\s*=\s*\"([^\"]+)\"", txt)
print(m.group(1) if m else "0.5.2")
')"
SYNCED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

mkdir -p "$ROOT_DIR/upstream/crates" "$ROOT_DIR/upstream/i18n"

for crate in torrent debrid extract geo cnl ipc remote tools labserver cli; do
  if [[ -d "$SRC_DIR/crates/$crate" ]]; then
    rm -rf "$ROOT_DIR/upstream/crates/$crate"
    cp -a "$SRC_DIR/crates/$crate" "$ROOT_DIR/upstream/crates/$crate"
  fi
done
if [[ -d "$SRC_DIR/crates/app/assets" ]]; then
  mkdir -p "$ROOT_DIR/upstream/crates/app"
  rm -rf "$ROOT_DIR/upstream/crates/app/assets"
  cp -a "$SRC_DIR/crates/app/assets" "$ROOT_DIR/upstream/crates/app/assets"
fi

cp -a "$SRC_DIR/i18n/." "$ROOT_DIR/upstream/i18n/"
if [[ ! -f "$ROOT_DIR/upstream/Cargo.toml" ]] || ! grep -q "crates/android-ffi" "$ROOT_DIR/upstream/Cargo.toml"; then
  cp -a "$SRC_DIR/Cargo.toml" "$ROOT_DIR/upstream/Cargo.toml"
fi
if ! grep -q '^tokio-socks[[:space:]]*=' "$ROOT_DIR/upstream/Cargo.toml"; then
  python3 -c '
p = "'"$ROOT_DIR"'/upstream/Cargo.toml"
s = open(p).read()
if "tokio-socks" not in s:
    s = s.replace("[workspace.dependencies]\n", "[workspace.dependencies]\ntokio-socks = \"0.5.2\"\n", 1)
    open(p, "w").write(s)
'
fi
if [[ -f "$ROOT_DIR/upstream/crates/engine/Cargo.toml" ]] && ! grep -q 'target_os = "android"' "$ROOT_DIR/upstream/crates/engine/Cargo.toml"; then
  cat >> "$ROOT_DIR/upstream/crates/engine/Cargo.toml" <<'EOF'

[target.'cfg(target_os = "android")'.dependencies]
rquickjs = { version = "0.14.0", default-features = false, features = ["std", "bindgen"] }
EOF
fi
if [[ -f "$ROOT_DIR/upstream/crates/engine/src/ftp.rs" ]] && ! grep -q 'target_os = "android"' "$ROOT_DIR/upstream/crates/engine/src/ftp.rs"; then
  python3 -c '
p = "'"$ROOT_DIR"'/upstream/crates/engine/src/ftp.rs"
txt = open(p).read()
old = """    let verifier = if extra.is_empty() {
        Verifier::new(provider.clone())
    } else {
        Verifier::new_with_extra_roots(
            extra.iter().map(|c| CertificateDer::from(c.clone())),
            provider.clone(),
        )
    }?;"""
new = """    #[cfg(not(target_os = "android"))]
    let verifier = if extra.is_empty() {
        Verifier::new(provider.clone())
    } else {
        Verifier::new_with_extra_roots(
            extra.iter().map(|c| CertificateDer::from(c.clone())),
            provider.clone(),
        )
    }?;
    #[cfg(target_os = "android")]
    let verifier = {
        let _ = extra;
        Verifier::new(provider.clone())?
    };"""
if old in txt:
    open(p, "w").write(txt.replace(old, new, 1))
'
fi
if [[ -f "$SRC_DIR/Cargo.lock" && ! -f "$ROOT_DIR/upstream/Cargo.lock" ]]; then
  cp -a "$SRC_DIR/Cargo.lock" "$ROOT_DIR/upstream/Cargo.lock"
fi
if [[ -f "$SRC_DIR/LICENSE" ]]; then
  cp -a "$SRC_DIR/LICENSE" "$ROOT_DIR/LICENSE"
fi
if [[ -f "$SRC_DIR/ATTRIBUTION.md" ]]; then
  cp -a "$SRC_DIR/ATTRIBUTION.md" "$ROOT_DIR/ATTRIBUTION.md"
fi

python3 -c '
import json
data = {
    "upstream_repo": "anefzaoui/misterdownloadmanager",
    "version": "'"$VERSION"'",
    "commit": "'"$COMMIT_SHA"'",
    "synced_at": "'"$SYNCED_AT"'"
}
with open("'"$ROOT_DIR"'/upstream/UPSTREAM_VERSION.json", "w") as f:
    json.dump(data, f, indent=2)
'

if [[ -n "$TMP_CLONE" && -d "$TMP_CLONE" ]]; then
  rm -rf "$TMP_CLONE"
fi

echo "Synced upstream anefzaoui/misterdownloadmanager @ $COMMIT_SHA (v$VERSION)"
