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
    kpm_kconfig_status="${ABK_KPM_KCONFIG_STATUS:-unknown}"
    kpm_kconfig_ready="${ABK_KPM_KCONFIG_READY:-false}"
    kpm_kconfig_path="${ABK_KPM_KCONFIG_PATH:-}"

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

    kpm_config_supported="false"
    if [ "${ABK_INPUT_KSU_VARIANT}" == "SukiSU" ] || [ "${ABK_INPUT_KSU_VARIANT}" == "BakaSU" ]; then
      if [ "$kpm_kconfig_ready" = "true" ] && [ -f "$kpm_kconfig_path" ]; then
        kpm_config_supported="true"
      else
        for dir in KernelSU/kernel common/drivers/kernelsu drivers/kernelsu kernel; do
        if [ -f "$dir/Kconfig" ] && rg -n '^[[:space:]]*config[[:space:]]+KPM\b' "$dir/Kconfig" >/dev/null 2>&1; then
          kpm_config_supported="true"
          break
        fi
        done
      fi
    fi

    if [ "${ABK_INPUT_USE_KPM}" == "true" ] && ! feature_disabled kpm && { [ "${ABK_INPUT_KSU_VARIANT}" == "SukiSU" ] || [ "${ABK_INPUT_KSU_VARIANT}" == "BakaSU" ]; }; then
      if [ "$kpm_config_supported" = "true" ]; then
        echo "CONFIG_KPM=y" >> "$DEFCONFIG"
        case "$kpm_kconfig_status" in
          native)
            echo "检测到原生 KPM Kconfig，已启用 CONFIG_KPM。"
            ;;
          injected)
            echo "源码未暴露 KPM Kconfig，已自动注入并启用 CONFIG_KPM。"
            ;;
          *)
            echo "检测到 KPM Kconfig，已启用 CONFIG_KPM。"
            ;;
        esac
      else
        echo "::error::请求启用 KPM，但当前 ${ABK_INPUT_KSU_VARIANT} 源码树仍未提供 config KPM。"
        exit 1
      fi
    elif [ "${ABK_INPUT_KSU_VARIANT}" == "SukiSU" ] || [ "${ABK_INPUT_KSU_VARIANT}" == "BakaSU" ]; then
      if [ "$kpm_config_supported" = "true" ]; then
        echo "# CONFIG_KPM is not set" >> "$DEFCONFIG"
        echo "检测到 KPM Kconfig，已显式关闭 CONFIG_KPM。"
      fi
    fi

    if [ "${ABK_INPUT_ENABLE_SUSFS}" != "true" ] && { [ "${ABK_INPUT_KSU_VARIANT}" == "SukiSU" ] || [ "${ABK_INPUT_KSU_VARIANT}" == "BakaSU" ]; }; then
      cat >> "$DEFCONFIG" << 'EOF'
# CONFIG_KSU_SUSFS is not set
# CONFIG_KSU_SUSFS_SUS_PATH is not set
# CONFIG_KSU_SUSFS_SUS_MOUNT is not set
# CONFIG_KSU_SUSFS_SUS_KSTAT is not set
# CONFIG_KSU_SUSFS_SPOOF_UNAME is not set
# CONFIG_KSU_SUSFS_ENABLE_LOG is not set
# CONFIG_KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS is not set
# CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG is not set
# CONFIG_KSU_SUSFS_OPEN_REDIRECT is not set
# CONFIG_KSU_SUSFS_SUS_MAP is not set
EOF
    fi

    CURRENT_SUB="${ABK_INPUT_SUB_LEVEL}"
    if [[ ! "$CURRENT_SUB" =~ ^[0-9]+$ ]]; then
      CURRENT_SUB=99999
    fi
    if [[ ( "${ABK_INPUT_KSU_VARIANT}" == "Official" || "${ABK_INPUT_KSU_VARIANT}" == "BakaSU" ) && "${ABK_INPUT_ANDROID_VERSION}" == "android13" && "${ABK_INPUT_KERNEL_VERSION}" == "5.15" && "$CURRENT_SUB" -ge 41 && "$CURRENT_SUB" -le 137 ]]; then
      {
        echo "CONFIG_KALLSYMS=y"
        echo "CONFIG_KALLSYMS_ALL=y"
      } >> "$DEFCONFIG"
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
