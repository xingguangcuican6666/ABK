#!/usr/bin/env bash
# 内核启动与编译必需的底层修复（设备修复、CVE、编译兼容）。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/core/unicode.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 应用 Unicode 绕过修复 ----
unicode() {
    cd "$KERNEL_ROOT/common"
    if [ "${ABK_INPUT_KERNEL_VERSION}" = "5.10" ] || [ "${ABK_INPUT_KERNEL_VERSION}" = "5.15" ]; then
      patch -p1 --forward < "$ACTION_BUILD/patches/unicode_bypass_fix_6.1-.patch" || true
    else
      patch -p1 --forward < "$ACTION_BUILD/patches/unicode_bypass_fix_6.1+.patch" || true
    fi

}

case "${1:-}" in
    unicode) unicode ;;
    *)
        echo "用法: $0 {unicode}" >&2
        exit 2
        ;;
esac
