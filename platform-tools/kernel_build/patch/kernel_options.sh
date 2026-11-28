#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/kernel_options.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 配置内核选项 ----
kernel-options() {
    cd "$KERNEL_ROOT"
    set -euo pipefail
    feature_disabled() {
      [ "${ABK_INPUT_SOURCE_MODE}" = "custom_git" ] &&
        [ -f "$ABK_CUSTOM_SOURCE_TRANSACTION_DIR/$1.disabled" ]
    }

    # NixOS 容器需要 tmpfs 的 xattr 与 POSIX ACL。虚拟化支持也会写入这两项，
    # 这里保持幂等，避免 defconfig 出现重复赋值 (kconfig 会报 override 警告)。
    ensure_global_config() {
      local cfg="$1"
      grep -qxF "${cfg}=y" "$DEFCONFIG" && return 0
      if grep -qxF "# ${cfg} is not set" "$DEFCONFIG"; then
        sed -i "s|^# ${cfg} is not set$|${cfg}=y|" "$DEFCONFIG"
      else
        echo "${cfg}=y" >> "$DEFCONFIG"
      fi
    }

    ensure_global_config CONFIG_TMPFS_XATTR
    ensure_global_config CONFIG_TMPFS_POSIX_ACL
    if [ "${ABK_INPUT_KSU_VARIANT}" != "None" ] && ! feature_disabled kernelsu; then
      echo "CONFIG_KSU=y" >> "$DEFCONFIG"
    fi

    CURRENT_SUB="${ABK_INPUT_SUB_LEVEL}"
    if [[ ! "$CURRENT_SUB" =~ ^[0-9]+$ ]]; then
      CURRENT_SUB=99999
    fi

    sed -i 's/check_defconfig//' ./common/build.config.gki

}

case "${1:-}" in
    kernel-options) kernel-options ;;
    *)
        echo "用法: $0 {kernel-options}" >&2
        exit 2
        ;;
esac
