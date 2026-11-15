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
grep -q "cargo ndk -t arm64-v8a" .github/workflows/upstream-sync.yml
grep -q "cargo ndk -t arm64-v8a" .github/workflows/android-ci.yml
