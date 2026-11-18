#!/usr/bin/env bash
# 本地串联「干净内核链路」：clean/ 的环境准备、源码同步、编译、打包，以及 core/ 里
# 内核启动与编译必需的底层修复。用于脱离 GitHub Actions 复现问题、维护脚本本身。
#
# 用法示例：
#   bash platform-tools/kernel_build/run-clean-build.sh \
#     --android android15 --kernel 6.6 --sub-level 77 --os-patch-level 2025-09
#
# 注意：
#   - 需要网络、约 60 GB 磁盘，以及 AOSP repo、clang、ccache 等依赖（--system-deps 可在
#     Debian/Ubuntu 上用 apt 安装缺失包，需要 sudo）。
#   - 工作目录默认落在 ~/.cache/abk-kernel-build/workspace，仓库本身不会被写入。
#   - 这里只跑 clean/ + core/；SUSFS、KPM、ZRAM 等外围拓展补丁属于 patch/ 层，不在其中。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="${ABK_LOCAL_KERNEL_STATE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/abk-kernel-build}"

ANDROID_VERSION=""
KERNEL_VERSION=""
SUB_LEVEL=""
OS_PATCH_LEVEL=""
REVISION="r11"
SUPP_OP="false"
ENABLE_SUSFS="false"
WITH_SYSTEM_DEPS=0
RECREATE=0

usage() {
    cat <<'EOF'
用法: run-clean-build.sh --android <androidX> --kernel <5.10|5.15|6.1|6.6|6.12|6.18>
                         --sub-level <N> --os-patch-level <YYYY-MM|lts> [选项]

必选:
  --android <androidX>         Android 版本，例如 android15
  --kernel  <X.Y>              内核版本，例如 6.6
  --sub-level <N>              子版本号，例如 77（未知可写 X）
  --os-patch-level <值>        安全补丁级别：YYYY-MM 或 lts

可选:
  --revision <rN>              5.10 boot 镜像修订号，默认 r11
  --supp-op                    应用一加 8E 处理器支持
  --enable-susfs               应用 Unicode 绕过修复（CVE，SUSFS 链路需要）
  --system-deps                先在当前系统上安装编译依赖（sudo apt-get）
  --recreate                   删除已有工作目录后重新同步源码
  -h, --help                   显示本帮助

环境变量:
  ABK_LOCAL_KERNEL_STATE_DIR   工作目录，默认 ${XDG_CACHE_HOME:-$HOME/.cache}/abk-kernel-build
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --android) ANDROID_VERSION="${2:?--android 需要取值}"; shift 2 ;;
        --kernel) KERNEL_VERSION="${2:?--kernel 需要取值}"; shift 2 ;;
        --sub-level) SUB_LEVEL="${2:?--sub-level 需要取值}"; shift 2 ;;
        --os-patch-level) OS_PATCH_LEVEL="${2:?--os-patch-level 需要取值}"; shift 2 ;;
        --revision) REVISION="${2:?--revision 需要取值}"; shift 2 ;;
        --supp-op) SUPP_OP="true"; shift ;;
        --enable-susfs) ENABLE_SUSFS="true"; shift ;;
        --system-deps) WITH_SYSTEM_DEPS=1; shift ;;
        --recreate) RECREATE=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) abk_die "未知参数: $1（--help 查看用法）" ;;
    esac
done

for name in ANDROID_VERSION KERNEL_VERSION SUB_LEVEL OS_PATCH_LEVEL; do
    [ -n "${!name}" ] || { usage >&2; abk_die "缺少必选参数: $name"; }
done

WORKSPACE="$STATE_DIR/workspace"
RUNNER_TEMP_DIR="$STATE_DIR/tmp"
ENV_FILE="$STATE_DIR/github-env"
mkdir -p "$WORKSPACE" "$RUNNER_TEMP_DIR"

if [ "$RECREATE" = "1" ]; then
    abk_log "重建工作目录: $WORKSPACE"
    rm -rf "$WORKSPACE"
    mkdir -p "$WORKSPACE"
fi

# 脚本通过 $GITHUB_WORKSPACE 定位仓库内的 .github/scripts、config、app；
# 用符号链接把它们挂进独立工作目录，仓库本身保持干净。
for entry in .github config app; do
    if [ -e "$REPO_ROOT/$entry" ] && [ ! -e "$WORKSPACE/$entry" ]; then
        ln -s "$REPO_ROOT/$entry" "$WORKSPACE/$entry"
    fi
done

export GITHUB_WORKSPACE="$WORKSPACE"
export RUNNER_TEMP="$RUNNER_TEMP_DIR"
export GITHUB_ENV="$ENV_FILE"
export GITHUB_STEP_SUMMARY="$STATE_DIR/step-summary.md"
export GITHUB_PATH="$STATE_DIR/github-path"
export GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-local/abk}"
export ABK_RUNNER_ENVIRONMENT="${ABK_RUNNER_ENVIRONMENT:-self-hosted}"
export ABK_DROIDSPACES_DEFAULT_REF="${ABK_DROIDSPACES_DEFAULT_REF:-v7.1.0}"
: >> "$GITHUB_ENV"
: >> "$GITHUB_STEP_SUMMARY"
: >> "$GITHUB_PATH"

# 脚本之间只通过 $GITHUB_ENV 传值；本地没有 Actions 的中转，
# 这里在每个步骤前把文件内容导回当前 shell（后写入的值覆盖先写入的）。
applied_lines=0
apply_env_file() {
    local line key delim=""
    local value=""
    [ -f "$GITHUB_ENV" ] || return 0
    while IFS= read -r line || [ -n "$line" ]; do
        if [ -n "$delim" ]; then
            if [ "$line" = "$delim" ]; then
                export "$key=$value"
                delim=""
                value=""
            else
                value+="${value:+$'\n'}$line"
            fi
            continue
        fi
        if [[ $line =~ ^([A-Za-z_][A-Za-z0-9_]*)'<<'(.+)$ ]]; then
            key="${BASH_REMATCH[1]}"
            delim="${BASH_REMATCH[2]}"
            value=""
            continue
        fi
        case "$line" in
            ''|'#'*) continue ;;
            *=*) export "${line%%=*}=${line#*=}" ;;
        esac
    done < "$GITHUB_ENV"
}

step() {
    local path="$1"
    shift
    abk_log "==> $path $*"
    apply_env_file
    bash "$SCRIPT_DIR/$path" "$@"
}

# 与 build.yml「构建信息摘要」步骤等价：把工作流输入写成 ABK_INPUT_* 后由脚本导出。
export ABK_INPUT_SOURCE_MODE="aosp"
export ABK_INPUT_ANDROID_VERSION="$ANDROID_VERSION"
export ABK_INPUT_KERNEL_VERSION="$KERNEL_VERSION"
export ABK_INPUT_SUB_LEVEL="$SUB_LEVEL"
export ABK_INPUT_OS_PATCH_LEVEL="$OS_PATCH_LEVEL"
export ABK_INPUT_KSU_VARIANT="None"
export ABK_INPUT_KSU_BRANCH="Stable(标准)"
export ABK_INPUT_REVISION="$REVISION"
export ABK_INPUT_SUPP_OP="$SUPP_OP"
export ABK_INPUT_ENABLE_SUSFS="$ENABLE_SUSFS"
export ABK_INPUT_USE_ZRAM="false"
export ABK_INPUT_USE_BBG="false"
export ABK_INPUT_USE_DDK="false"
export ABK_INPUT_USE_NTSYNC="false"
export ABK_INPUT_USE_NETWORKING="false"
export ABK_INPUT_USE_KPM="false"
export ABK_INPUT_USE_REKERNEL="false"
export ABK_INPUT_VIRTUALIZATION_SUPPORT="off"
export ABK_INPUT_ZRAM_FULL_ALGO="false"
export ABK_INPUT_UPLOAD_AUX_ARTIFACTS="false"
export ABK_RUNNER_ENVIRONMENT
export KSU_VARIANT="None"
export ENABLE_SUSFS="$ENABLE_SUSFS"
export USE_ZRAM="false"
export USE_BBG="false"
export USE_DDK="false"
export USE_NTSYNC="false"
export USE_NETWORKING="false"
export USE_KPM="false"
export USE_REKERNEL="false"
export VIRTUALIZATION_SUPPORT="off"
export KERNEL_LOCAL_CACHE="${KERNEL_LOCAL_CACHE:-auto}"

step clean/env.sh summary
step clean/validate.sh validate
step clean/env.sh manager-cert
step clean/env.sh init
step clean/env.sh show

if [ "$WITH_SYSTEM_DEPS" = "1" ]; then
    step clean/toolchain.sh deps-install
fi

step clean/toolchain.sh cache-configure
step clean/toolchain.sh ccache
step clean/toolchain.sh toolchain
step clean/toolchain.sh sign-key
step clean/toolchain.sh git-config
step clean/deps.sh deps-clone
step clean/source.sh sync
step clean/prepare.sh stock-config
step clean/prepare.sh actual-sublevel

step core/glibc-compat.sh glibc
if [ "$SUPP_OP" = "true" ]; then
    step core/oneplus-8e.sh oneplus8e
fi
step core/kernel-specific.sh kernel-specific
step clean/prepare.sh backup-defconfig
if [ "$ENABLE_SUSFS" = "true" ]; then
    step core/unicode.sh unicode
fi
if [ "$KERNEL_VERSION" = "6.6" ]; then
    step core/wifi-bt-66.sh wifi-bt
fi

step clean/build.sh kernel-name
step clean/build.sh build-time
step clean/build.sh compile
step clean/package.sh boot-prep
step clean/package.sh anykernel
case "$ANDROID_VERSION" in
    android12) step clean/package.sh boot-a12 ;;
    *) step clean/package.sh boot-a13 ;;
esac
step clean/package.sh bundle
step clean/package.sh collect-rejects

abk_log "完成。产物位于 $GITHUB_WORKSPACE"