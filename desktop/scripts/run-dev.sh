#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DESKTOP_DIR="$ROOT_DIR/desktop"
FLUTTER_DIR="$DESKTOP_DIR/flutter_app"
FLUTTER_SDK="${FLUTTER_SDK:-/run/media/xingguangcuican/Project/flutter-sdk/flutter}"

if [[ ! -x "$FLUTTER_SDK/bin/flutter" ]]; then
  echo "flutter sdk not found at: $FLUTTER_SDK" >&2
  echo "set FLUTTER_SDK to your flutter installation path" >&2
  exit 1
fi

(
  cd "$FLUTTER_DIR"
  "$FLUTTER_SDK/bin/flutter" build linux --debug --no-pub
)

(
  cd "$DESKTOP_DIR"
  cargo build --bin abk-desktop --bin abk_sidecar
  exec ./target/debug/abk-desktop
)
