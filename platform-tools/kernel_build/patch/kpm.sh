#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/kpm.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 注入 SukiSU/BakaSU KPM Kconfig ----
kconfig() {
    cd "$KERNEL_ROOT"
    set -euo pipefail
    KSU_KERNEL_DIR=""
    for dir in ./common/drivers/kernelsu ./drivers/kernelsu ./KernelSU/kernel ./kernel; do
      if [ -f "$dir/Kconfig" ]; then
        KSU_KERNEL_DIR="$dir"
        break
      fi
    done
    if [ -z "$KSU_KERNEL_DIR" ]; then
      echo "::error::未找到 ${ABK_INPUT_KSU_VARIANT} Kconfig，无法注入 KPM Kconfig 符号"
      exit 1
    fi

    {
      echo "ABK_KPM_KCONFIG_DIR=$KSU_KERNEL_DIR"
      echo "ABK_KPM_KCONFIG_PATH=$KSU_KERNEL_DIR/Kconfig"
    } >> "$GITHUB_ENV"

    python3 - "$KSU_KERNEL_DIR/Kconfig" "$GITHUB_ENV" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
github_env = Path(sys.argv[2])
text = path.read_text()
pattern = re.compile(r"(?m)^[ \t]*config[ \t]+KPM\b")
if pattern.search(text):
    with github_env.open("a", encoding="utf-8") as handle:
        handle.write("ABK_KPM_KCONFIG_STATUS=native\n")
        handle.write("ABK_KPM_KCONFIG_READY=true\n")
    print(f"{path} 已包含 KPM Kconfig 符号")
    raise SystemExit(0)

lines = [
    "",
    'menu "KernelSU - KPM"',
    "config KPM",
    '  bool "KernelPatch Module support"',
    "  depends on KSU",
    "  default y",
    "",
    "endmenu",
]
block = "\n".join(lines)

marker = "\nendmenu\n"
if marker in text:
    text = text.rsplit(marker, 1)[0].rstrip() + "\n" + block + marker
else:
    text = text.rstrip() + "\n" + block + "\n"
path.write_text(text)

final_text = path.read_text()
if not pattern.search(final_text):
    raise SystemExit(f"{path} 注入后仍缺少 config KPM")

with github_env.open("a", encoding="utf-8") as handle:
    handle.write("ABK_KPM_KCONFIG_STATUS=injected\n")
    handle.write("ABK_KPM_KCONFIG_READY=true\n")
print(f"已注入 {path} 的 KPM Kconfig 符号")
PY

}

# ---- 配置 KPM 超级密码 ----
password() {
    echo "KPM 超级密码将在 Image KPM patch 阶段写入。"
    echo "CONFIG_KPM_SUPER_KEY 不是当前 KSU Kconfig 符号，不能写入 defconfig fragment。"

}

# ---- 应用 KPM 补丁 (SukiSU/BakaSU) ----
image() {
    set -euo pipefail
    if [ "${ABK_INPUT_SOURCE_MODE}" = "custom_git" ] && [ -f "$ABK_CUSTOM_SOURCE_TRANSACTION_DIR/kpm.disabled" ]; then
      echo "KPM was skipped during source integration; no image patch will be applied."
      exit 0
    fi
    case "${ABK_INPUT_ANDROID_VERSION}" in
      "android12"|"android13")
        if [ "${ABK_INPUT_KERNEL_VERSION}" != "6.6" ]; then
          TARGET_PATH="$KERNEL_ROOT/out/${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}/dist"
        fi
        ;;
      "android14"|"android15"|"android16"|"android17")
        TARGET_PATH="$KERNEL_ROOT/bazel-bin/common/kernel_aarch64/"
        ;;
    esac

    image_backup="$TARGET_PATH/Image.abk-kpm-backup"
    cp "$TARGET_PATH/Image" "$image_backup"
    cp -r $SUKISU_PATCHES/kpm/patch_linux "$TARGET_PATH/patch"
    cd "$TARGET_PATH"
    chmod 777 patch
    patch_ok=true
    if ! { if [ -n "$KPM_PASSWORD" ]; then ./patch -s "$KPM_PASSWORD"; else ./patch; fi; }; then
      patch_ok=false
    fi
    [ -f oImage ] || patch_ok=false
    if [ "$patch_ok" != true ]; then
      if [ "${ABK_INPUT_SOURCE_MODE}" = "custom_git" ]; then
        mv "$image_backup" Image
        echo "::warning::KPM image patch failed; original Image restored."
        exit 1
      fi
      exit 1
    fi
    rm -rf Image
    mv oImage Image
    rm -f "$image_backup"

}

case "${1:-}" in
    kconfig) kconfig ;;
    password) password ;;
    image) image ;;
    *)
        echo "用法: $0 {kconfig|password|image}" >&2
        exit 2
        ;;
esac
