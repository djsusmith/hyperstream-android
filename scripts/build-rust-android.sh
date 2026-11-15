#!/data/user/0/com.agy/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
OUT_DIR="$ROOT_DIR/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$OUT_DIR"

if [[ -d "/data/user/0/com.agy/files/usr/lib" ]]; then
  export LIBCLANG_PATH="/data/user/0/com.agy/files/usr/lib"
fi
echo "Building libmdm_android.so (mdm-android-ffi)..."
cargo build --manifest-path "$ROOT_DIR/upstream/Cargo.toml" -p mdm-android-ffi --release

SO_PATH="$ROOT_DIR/upstream/target/release/libmdm_android.so"
if [[ -f "$SO_PATH" ]]; then
  cp -f "$SO_PATH" "$OUT_DIR/libmdm_android.so"
  if command -v llvm-strip >/dev/null 2>&1; then
    llvm-strip --strip-debug "$OUT_DIR/libmdm_android.so" || true
  fi
  echo "Copied $SO_PATH -> $OUT_DIR/libmdm_android.so"
fi
