#!/usr/bin/env bash
# 内核启动与编译必需的底层修复（设备修复、CVE、编译兼容）。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/core/wifi-bt-66.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 修复 6.6 WiFi/蓝牙兼容性（三星 + 小米） ----
wifi-bt() {
    cd "$KERNEL_ROOT/common"
    ensure_line_once() {
      local file="$1"
      local line="$2"
      if [ ! -f "$file" ]; then
        echo "::error::文件不存在: $file"
        exit 1
      fi
      if ! grep -qF "$line" "$file"; then
        echo "$line" >> "$file"
      fi
    }

    GALAXY_SYMBOL_LIST="android/abi_gki_aarch64_galaxy"
    XIAOMI_SYMBOL_LIST="android/abi_gki_aarch64_xiaomi"
    DRIVERS_MAKEFILE="drivers/Makefile"
    MIN_KDP_SRC="$KERNEL_PATCHES/samsung/min_kdp/min_kdp.c"
    MIN_KDP_PATCH="$KERNEL_PATCHES/samsung/min_kdp/add-min_kdp-symbols.patch"
    MIN_KDP_DST="drivers/min_kdp.c"

    ensure_line_once "$GALAXY_SYMBOL_LIST" "kdp_set_cred_non_rcu"
    ensure_line_once "$GALAXY_SYMBOL_LIST" "kdp_usecount_dec_and_test"
    ensure_line_once "$GALAXY_SYMBOL_LIST" "kdp_usecount_inc"

    if [ ! -f "$MIN_KDP_PATCH" ]; then
      echo "::error::补丁不存在: $MIN_KDP_PATCH"
      exit 1
    fi
    if patch -p1 --dry-run < "$MIN_KDP_PATCH" >/dev/null 2>&1; then
      patch -p1 --no-backup-if-mismatch < "$MIN_KDP_PATCH"
    else
      echo "min_kdp symbols patch 已应用或当前上下文不匹配，跳过。"
    fi

    if [ ! -f "$MIN_KDP_SRC" ]; then
      echo "::error::文件不存在: $MIN_KDP_SRC"
      exit 1
    fi
    cp "$MIN_KDP_SRC" "$MIN_KDP_DST"
    ensure_line_once "$DRIVERS_MAKEFILE" "obj-y += min_kdp.o"

    ensure_line_once "$XIAOMI_SYMBOL_LIST" "device_find_any_child"

}

case "${1:-}" in
    wifi-bt) wifi-bt ;;
    *)
        echo "用法: $0 {wifi-bt}" >&2
        exit 2
        ;;
esac
