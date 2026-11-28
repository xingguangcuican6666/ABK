#!/usr/bin/env bash
# 干净 GKI 内核：环境、校验、工具链、源码同步、编译与打包。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/clean/env.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 构建信息摘要 ----
summary() {
    set -euo pipefail

    # 把薄壳步骤传入的 ABK_INPUT_* 固化到 $GITHUB_ENV，后续脚本步骤统一从这里读取。
    abk_export_inputs

    effective_use_zram="${ABK_INPUT_USE_ZRAM}"
    effective_zram_full_algo="${ABK_INPUT_ZRAM_FULL_ALGO}"
    effective_zram_extra_algos="${ABK_INPUT_ZRAM_EXTRA_ALGOS:-}"
    effective_zram_legacy_lz4k="true"
    effective_modern_gki="false"

    case "${ABK_INPUT_KERNEL_VERSION}" in
      6.12|6.18)
        effective_modern_gki="true"
        effective_zram_legacy_lz4k="false"
        ;;
    esac

    if [ "${ABK_INPUT_ANDROID_VERSION}" = "android17" ] && [ "${ABK_INPUT_KERNEL_VERSION}" = "6.18" ] \
      && [ "${ABK_INPUT_USE_BBG}" = "true" ]; then
      echo "::error::android17/6.18 暂不支持 BBG：上游 Baseband-guard 仍使用 6.18 已删除的 blkdev_get_no_open/blkdev_put_no_open，请关闭 BBG 后重试。"
      exit 1
    fi

    if [ "$effective_zram_legacy_lz4k" = "false" ]; then
      if [ "$effective_zram_full_algo" = "true" ]; then
        echo "::warning::${ABK_INPUT_ANDROID_VERSION}/${ABK_INPUT_KERNEL_VERSION} 会跳过 legacy LZ4K/LZ4KD/LZ4K_OPLUS 补丁栈，仅保留内核原生支持的 ZRAM 算法。"
      fi
      if [ -n "$effective_zram_extra_algos" ]; then
        filtered_algos="$(python3 -c 'import sys; blocked={"lz4k","lz4kd","lz4k_oplus"}; print(",".join(item.strip() for item in sys.argv[1].split(",") if item.strip() and item.strip().lower() not in blocked))' "$effective_zram_extra_algos")"
        if [ "$filtered_algos" != "$effective_zram_extra_algos" ]; then
          echo "::warning::${ABK_INPUT_ANDROID_VERSION}/${ABK_INPUT_KERNEL_VERSION} 不支持 legacy LZ4K/LZ4KD/LZ4K_OPLUS，已自动从自定义算法列表移除。"
        fi
        effective_zram_extra_algos="$filtered_algos"
      fi
    fi

    if [ -n "$effective_zram_extra_algos" ]; then
      if python3 -c 'import sys; algos=[item.strip().lower() for item in sys.argv[1].split(",") if item.strip()]; raise SystemExit(0 if "zstdp" in algos else 1)' "$effective_zram_extra_algos"; then
        echo "::warning::zstdp 后端已废弃（scomp 回调与 6.12+ crypto API 不兼容），已从 ZRAM 算法列表移除。"
        effective_zram_extra_algos="$(python3 -c 'import sys; print(",".join(item.strip() for item in sys.argv[1].split(",") if item.strip() and item.strip().lower() != "zstdp"))' "$effective_zram_extra_algos")"
      fi
    fi

    {
      echo "EFFECTIVE_USE_ZRAM=$effective_use_zram"
      echo "EFFECTIVE_ZRAM_FULL_ALGO=$effective_zram_full_algo"
      echo "EFFECTIVE_ZRAM_EXTRA_ALGOS=$effective_zram_extra_algos"
      echo "EFFECTIVE_ZRAM_LEGACY_LZ4K=$effective_zram_legacy_lz4k"
      echo "EFFECTIVE_MODERN_GKI=$effective_modern_gki"
    } >> "$GITHUB_ENV"

    echo "EFFECTIVE_KSU_BRANCH=${ABK_INPUT_KSU_BRANCH}" >> "$GITHUB_ENV"

    zram_extra_label="${effective_zram_extra_algos:-无}"

    custom_modules_label="false"
    [ -n "${ABK_INPUT_CUSTOM_EXTERNAL_MODULES}" ] && custom_modules_label="true"
    custom_options_label="false"
    [ -n "${ABK_INPUT_CUSTOM_KERNEL_OPTIONS}" ] && custom_options_label="true"

    echo "========================================"
    echo "       内核构建配置摘要"
    echo "========================================"
    echo "Android 版本  : ${ABK_INPUT_ANDROID_VERSION}"
    echo "内核版本      : ${ABK_INPUT_KERNEL_VERSION}"
    echo "子版本号      : ${ABK_INPUT_SUB_LEVEL}"
    echo "补丁级别      : ${ABK_INPUT_OS_PATCH_LEVEL}"
    echo "KSU 变体      : ${ABK_INPUT_KSU_VARIANT}"
    echo "KSU 分支      : $effective_ksu_branch"
    echo "管理器包名    : ${ABK_INPUT_MANAGER_PACKAGE:-app/signing/abk-manager-cert.env}"
    echo "构建时间      : ${ABK_INPUT_BUILD_TIME}"
    echo "ZRAM 增强     : $effective_use_zram"
    echo "ZRAM 完整算法 : $effective_zram_full_algo"
    echo "ZRAM 额外算法 : $zram_extra_label"
    echo "BBG 补丁      : ${ABK_INPUT_USE_BBG}"
    echo "DDK LSM       : ${ABK_INPUT_USE_DDK}"
    echo "NTsync 补丁   : ${ABK_INPUT_USE_NTSYNC}"
    echo "Networing 增强: ${ABK_INPUT_USE_NETWORKING}"
    echo "Re-Kernel     : ${ABK_INPUT_USE_REKERNEL}"
    echo "虚拟化支持   : ${ABK_INPUT_VIRTUALIZATION_SUPPORT}"
    echo "自定义注入    : $custom_modules_label"
    echo "内核选项覆盖  : $custom_options_label"
    echo "Stock Config  : 自动检测 config/stock_defconfig"
    echo "========================================"

}

# ---- 准备 ABK 管理器证书元数据 ----
manager-cert() {
    set -euo pipefail

    read_cert_value() {
      local key="$1"
      local file="$GITHUB_WORKSPACE/app/signing/abk-manager-cert.env"
      [ -f "$file" ] || return 0
      awk -F= -v target="$key" '
        /^[[:space:]]*#/ { next }
        $1 == target {
          value = substr($0, index($0, "=") + 1)
          gsub(/^[[:space:]\042\047]+|[[:space:]\042\047]+$/, "", value)
          print value
          exit
        }
      ' "$file"
    }

    package="${ABK_INPUT_MANAGER_PACKAGE:-}"
    cert_size="${ABK_INPUT_MANAGER_CERT_SIZE:-}"
    cert_sha="${ABK_INPUT_MANAGER_CERT_SHA256:-}"

    [ -n "$package" ] || package="$(read_cert_value ABK_MANAGER_PACKAGE)"
    [ -n "$cert_size" ] || cert_size="$(read_cert_value ABK_MANAGER_CERT_SIZE)"
    [ -n "$cert_sha" ] || cert_sha="$(read_cert_value ABK_MANAGER_CERT_SHA256)"

    package="${package:-com.abk.kernel}"
    cert_size="${cert_size:-1407}"
    cert_sha="${cert_sha:-34e5e843952277759603cd0f949770b24c868530d80d7baeff08776a7e132b16}"
    cert_sha="$(printf '%s' "$cert_sha" | tr '[:upper:]' '[:lower:]')"

    if ! [[ "$package" =~ ^[A-Za-z0-9_.]+$ ]]; then
      echo "::error::ABK manager package is invalid: $package"
      exit 1
    fi
    if ! [[ "$cert_size" =~ ^(0x[0-9a-fA-F]+|[0-9]+)$ ]]; then
      echo "::error::ABK manager certificate size is invalid: $cert_size"
      exit 1
    fi
    if ! [[ "$cert_sha" =~ ^[0-9a-f]{64}$ ]]; then
      echo "::error::ABK manager certificate SHA-256 is invalid: $cert_sha"
      exit 1
    fi

    {
      echo "ABK_MANAGER_PACKAGE=$package"
      echo "ABK_MANAGER_CERT_SIZE=$cert_size"
      echo "ABK_MANAGER_CERT_SHA256=$cert_sha"
    } >> "$GITHUB_ENV"

    {
      echo "### ABK manager identity"
      echo
      echo "- Package: \`$package\`"
      echo "- Certificate size: \`$cert_size\`"
      echo "- Certificate SHA-256: \`$cert_sha\`"
    } >> "$GITHUB_STEP_SUMMARY"

    echo "ABK manager package: $package"
    echo "ABK manager cert size: $cert_size"
    echo "ABK manager cert sha256: $cert_sha"

}

# ---- 初始化构建环境 ----
init() {
    CONFIG="${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}-${ABK_INPUT_SUB_LEVEL}"
    KERNEL_ROOT="$GITHUB_WORKSPACE/$CONFIG"
    mkdir -p "$KERNEL_ROOT"

    cat >> $GITHUB_ENV << EOF
CONFIG=$CONFIG
KERNEL_ROOT=$KERNEL_ROOT
DEFCONFIG=$KERNEL_ROOT/common/arch/arm64/configs/gki_defconfig
KERNEL_PATCHES=$GITHUB_WORKSPACE/kernel_patches
SUKISU_PATCHES=$GITHUB_WORKSPACE/SukiSU_patch
ZZH_PATCHES=$GITHUB_WORKSPACE
CUSTOM_EXTERNAL_MODULES_MANIFEST=$GITHUB_WORKSPACE/custom_external_modules.tsv
ANYKERNEL3=$GITHUB_WORKSPACE/AnyKernel3
ACTION_BUILD=$GITHUB_WORKSPACE/Action-Build
AVBTOOL=$GITHUB_WORKSPACE/kernel-build-tools/linux-x86/bin/avbtool
MKBOOTIMG=$GITHUB_WORKSPACE/mkbootimg/mkbootimg.py
UNPACK_BOOTIMG=$GITHUB_WORKSPACE/mkbootimg/unpack_bootimg.py
BOOT_SIGN_KEY_PATH=$GITHUB_WORKSPACE/kernel-build-tools/linux-x86/share/avb/testkey_rsa2048.pem
EOF

    mkdir -p "$GITHUB_WORKSPACE/git-repo"
    curl -L https://storage.googleapis.com/git-repo-downloads/repo -o "$GITHUB_WORKSPACE/git-repo/repo"
    chmod 0755 "$GITHUB_WORKSPACE/git-repo/repo"
    echo "$GITHUB_WORKSPACE/git-repo" >> "$GITHUB_PATH"
    echo "REPO=$GITHUB_WORKSPACE/git-repo/repo" >> $GITHUB_ENV

}

# ---- 显示配置信息 ----
show() {
    CONFIG_FILE="$GITHUB_WORKSPACE/config/config"
    if [ -f "$CONFIG_FILE" ]; then
      echo "加载配置文件: $CONFIG_FILE"
      cat "$CONFIG_FILE"
    fi

}

case "${1:-}" in
    summary) summary ;;
    manager-cert) manager-cert ;;
    init) init ;;
    show) show ;;
    *)
        echo "用法: $0 {summary|manager-cert|init|show}" >&2
        exit 2
        ;;
esac
