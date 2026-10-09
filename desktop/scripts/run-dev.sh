#!/usr/bin/env bash
# One-command dev launcher: builds + starts the Rust sidecar, waits for it to be
# healthy, then runs the Flutter shell with HOT RELOAD pointed at it. When the
# Flutter session exits, the sidecar is torn down automatically.
#
# Usage:
#   bash desktop/scripts/run-dev.sh            # runs on -d linux
#   bash desktop/scripts/run-dev.sh -d windows # pick another device
#   ABK_DEV_PORT=38999 bash desktop/scripts/run-dev.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DESKTOP_DIR="$ROOT_DIR/desktop"
FLUTTER_DIR="$DESKTOP_DIR/flutter_app"
PORT="${ABK_DEV_PORT:-38765}"
BASE_URL="http://127.0.0.1:${PORT}"

# Locate flutter: explicit FLUTTER_SDK, else common local SDK, else PATH.
if [[ -n "${FLUTTER_SDK:-}" && -x "$FLUTTER_SDK/bin/flutter" ]]; then
  FLUTTER="$FLUTTER_SDK/bin/flutter"
elif [[ -x "/run/media/xingguangcuican/Project/flutter-sdk/flutter/bin/flutter" ]]; then
  FLUTTER="/run/media/xingguangcuican/Project/flutter-sdk/flutter/bin/flutter"
elif command -v flutter >/dev/null 2>&1; then
  FLUTTER="$(command -v flutter)"
else
  echo "flutter not found. Set FLUTTER_SDK to your flutter install path." >&2
  exit 1
fi

DEVICE_ARGS=("$@")
if [[ ${#DEVICE_ARGS[@]} -eq 0 ]]; then
  DEVICE_ARGS=(-d linux)
fi

echo "==> Building sidecar (abk_sidecar)"
( cd "$DESKTOP_DIR" && cargo build --bin abk_sidecar )
SIDECAR_BIN="$DESKTOP_DIR/target/debug/abk_sidecar"

echo "==> Starting sidecar on ${BASE_URL}"
ABK_DESKTOP_HOST=127.0.0.1 \
ABK_DESKTOP_APP_ROOT="$ROOT_DIR" \
  "$SIDECAR_BIN" --port "$PORT" &
SIDECAR_PID=$!

cleanup() {
  if kill -0 "$SIDECAR_PID" >/dev/null 2>&1; then
    echo "==> Stopping sidecar (pid $SIDECAR_PID)"
    kill "$SIDECAR_PID" >/dev/null 2>&1 || true
    wait "$SIDECAR_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "==> Waiting for sidecar health"
for _ in $(seq 1 60); do
  if ! kill -0 "$SIDECAR_PID" >/dev/null 2>&1; then
    echo "sidecar exited before becoming healthy" >&2
    exit 1
  fi
  if curl -fsS "${BASE_URL}/api/v1/health" >/dev/null 2>&1; then
    echo "==> Sidecar healthy"
    break
  fi
  sleep 0.25
done

echo "==> flutter run ${DEVICE_ARGS[*]} (hot reload; press q to quit)"
( cd "$FLUTTER_DIR" && ABK_DESKTOP_BASE_URL="$BASE_URL" "$FLUTTER" run "${DEVICE_ARGS[@]}" )
