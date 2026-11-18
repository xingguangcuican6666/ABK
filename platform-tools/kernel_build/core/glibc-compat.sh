#!/usr/bin/env bash
# 内核启动与编译必需的底层修复（设备修复、CVE、编译兼容）。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/core/glibc-compat.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 修复 glibc 2.38 兼容性 ----
glibc() {
    cd "$KERNEL_ROOT/common"
    RAW_SUB="${ABK_INPUT_SUB_LEVEL}"
    [[ ! "$RAW_SUB" =~ ^[0-9]+$ ]] && CURRENT_SUB=99999 || CURRENT_SUB=$RAW_SUB

    NEEDS_FIX=false
    if [[ "${ABK_INPUT_ANDROID_VERSION}" == "android13" && "${ABK_INPUT_KERNEL_VERSION}" == "5.10" && $CURRENT_SUB -le 186 ]] ||
       [[ "${ABK_INPUT_ANDROID_VERSION}" == "android13" && "${ABK_INPUT_KERNEL_VERSION}" == "5.15" && $CURRENT_SUB -le 119 ]] ||
       [[ "${ABK_INPUT_ANDROID_VERSION}" == "android14" && "${ABK_INPUT_KERNEL_VERSION}" == "6.1" && $CURRENT_SUB -le 43 ]]; then
      NEEDS_FIX=true
    fi

    if [ "$NEEDS_FIX" = true ]; then
      GLIBC_VERSION=$(ldd --version 2>/dev/null | head -n 1 | awk '{print $NF}')
      if [ "$(printf '%s\n' "2.38" "$GLIBC_VERSION" | sort -V | head -n1)" = "2.38" ]; then
        echo "应用 glibc 2.38 兼容性修复..."
        sed -i '/\$(Q)\$(MAKE) -C \$(SUBCMD_SRC) OUTPUT=\$(abspath \$(dir \$@))\/ \$(abspath \$@)/s//$(Q)$(MAKE) -C $(SUBCMD_SRC) EXTRA_CFLAGS="$(CFLAGS)" OUTPUT=$(abspath $(dir $@))\/ $(abspath $@)/' tools/bpf/resolve_btfids/Makefile 2>/dev/null || true

        if [[ "${ABK_INPUT_KERNEL_VERSION}" == "5.10" || "${ABK_INPUT_KERNEL_VERSION}" == "5.15" ]]; then
          sed -i '/char \*buf = NULL;/a int i;' tools/lib/subcmd/parse-options.c 2>/dev/null || true
          sed -i 's/for (int i = 0; subcommands\[i\]; i++) {/for (i = 0; subcommands[i]; i++) {/' tools/lib/subcmd/parse-options.c 2>/dev/null || true
          sed -i '/if (subcommands) {/a int i;' tools/lib/subcmd/parse-options.c 2>/dev/null || true
          sed -i 's/for (int i = 0; subcommands\[i\]; i++)/for (i = 0; subcommands[i]; i++)/' tools/lib/subcmd/parse-options.c 2>/dev/null || true
        fi
      fi
    fi

}

case "${1:-}" in
    glibc) glibc ;;
    *)
        echo "用法: $0 {glibc}" >&2
        exit 2
        ;;
esac
