#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DESKTOP_DIR="$ROOT_DIR/desktop"
FLUTTER_DIR="$DESKTOP_DIR/flutter_app"
OUT_DIR="$DESKTOP_DIR/out/linux"
APPDIR="$OUT_DIR/AppDir"
BUNDLE_DIR="$FLUTTER_DIR/build/linux/x64/release/bundle"
OUTPUT_APPIMAGE="$OUT_DIR/ABK-x86_64.AppImage"

copy_resource_tree() {
  local src="$1"
  local dst="$2"
  mkdir -p "$(dirname "$dst")"
  cp -a "$src" "$dst"
}

prepare_appimage_tool() {
  local tool="$OUT_DIR/appimagetool-x86_64.AppImage"
  if [[ -x "$tool" ]]; then
    printf '%s\n' "$tool"
    return 0
  fi
  mkdir -p "$OUT_DIR"
  curl -L \
    -o "$tool" \
    https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage
  chmod 0755 "$tool"
  printf '%s\n' "$tool"
}

mkdir -p "$OUT_DIR"

cargo build \
  --manifest-path "$DESKTOP_DIR/Cargo.toml" \
  --release \
  --bin abk_launcher \
  --bin abk_sidecar

(
  cd "$FLUTTER_DIR"
  flutter build linux --release
)

rm -rf "$APPDIR"
mkdir -p \
  "$APPDIR/usr/bin" \
  "$APPDIR/usr/lib/abk_desktop" \
  "$APPDIR/usr/share/abk/app/signing" \
  "$APPDIR/usr/share/applications" \
  "$APPDIR/usr/share/icons/hicolor/256x256/apps"

install -Dm755 "$DESKTOP_DIR/target/release/abk_launcher" "$APPDIR/usr/bin/abk_launcher"
install -Dm755 "$DESKTOP_DIR/target/release/abk_sidecar" "$APPDIR/usr/bin/abk_sidecar"
cp -a "$BUNDLE_DIR"/. "$APPDIR/usr/lib/abk_desktop/"

copy_resource_tree "$ROOT_DIR/cli" "$APPDIR/usr/share/abk/cli"
copy_resource_tree "$ROOT_DIR/zram" "$APPDIR/usr/share/abk/zram"
copy_resource_tree "$ROOT_DIR/ddk" "$APPDIR/usr/share/abk/ddk"
copy_resource_tree "$ROOT_DIR/config" "$APPDIR/usr/share/abk/config"
install -Dm644 "$ROOT_DIR/hmbird_patch.c" "$APPDIR/usr/share/abk/hmbird_patch.c"
install -Dm644 "$ROOT_DIR/app/signing/abk-manager-cert.env" "$APPDIR/usr/share/abk/app/signing/abk-manager-cert.env"

install -Dm755 "$DESKTOP_DIR/packaging/linux/AppRun" "$APPDIR/AppRun"
install -Dm644 \
  "$DESKTOP_DIR/packaging/linux/com.abk.abk_desktop.desktop" \
  "$APPDIR/com.abk.abk_desktop.desktop"
install -Dm644 \
  "$DESKTOP_DIR/packaging/linux/com.abk.abk_desktop.desktop" \
  "$APPDIR/usr/share/applications/com.abk.abk_desktop.desktop"
install -Dm644 \
  "$FLUTTER_DIR/assets/images/android_abk_foreground.png" \
  "$APPDIR/com.abk.abk_desktop.png"
install -Dm644 \
  "$FLUTTER_DIR/assets/images/android_abk_foreground.png" \
  "$APPDIR/usr/share/icons/hicolor/256x256/apps/com.abk.abk_desktop.png"

appimagetool="$(prepare_appimage_tool)"
ARCH=x86_64 "$appimagetool" --appimage-extract-and-run "$APPDIR" "$OUTPUT_APPIMAGE"
printf 'AppImage created: %s\n' "$OUTPUT_APPIMAGE"
