#!/usr/bin/env bash
# 内核启动与编译必需的底层修复（设备修复、CVE、编译兼容）。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/core/oneplus-8e.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 添加一加 8E 处理器支持 ----
oneplus8e() {
    cd "$KERNEL_ROOT/common/drivers"
    echo "下载一加 8E 支持补丁..."
    curl -LSs "https://github.com/xingguangcuican6666/ABK/raw/refs/heads/dev/hmbird_patch.c" -o hmbird_patch.c
    echo "obj-y += hmbird_patch.o" >> Makefile

}

case "${1:-}" in
    oneplus8e) oneplus8e ;;
    *)
        echo "用法: $0 {oneplus8e}" >&2
        exit 2
        ;;
esac
