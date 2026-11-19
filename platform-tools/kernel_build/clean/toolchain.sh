#!/usr/bin/env bash
# 干净 GKI 内核：环境、校验、工具链、源码同步、编译与打包。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/clean/toolchain.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 安装编译依赖 ----
deps-install() {
    disable_broken_chrome_source() {
      local runner_environment="${ABK_RUNNER_ENVIRONMENT:-${RUNNER_ENVIRONMENT:-}}"
      if [ -z "$runner_environment" ]; then
        # GitHub-hosted runners expose RUNNER_ENVIRONMENT; local runners default to preserve sources.
        runner_environment="${GITHUB_ACTIONS:+github-hosted}"
      fi
      if [ "$runner_environment" != "github-hosted" ]; then
        echo "保留自托管 Runner 的 Chrome APT 源"
        return 0
      fi
      local source
      while IFS= read -r source; do
        echo "::warning::禁用与构建无关的 Chrome APT 源: $source"
        sudo rm -f "$source"
      done < <(sudo grep -RIl 'dl.google.com/linux/chrome' /etc/apt/sources.list.d 2>/dev/null || true)
    }

    apt_update_retry() {
      local attempt
      for attempt in 1 2 3; do
        if sudo apt-get update; then
          return 0
        fi
        echo "::warning::apt-get update 第 ${attempt} 次失败，清理索引后重试。"
        sudo apt-get clean || true
        sudo rm -rf /var/lib/apt/lists/*
        sleep "$attempt"
      done
      echo "::error::apt-get update 连续失败。"
      return 1
    }

    apt_install_retry() {
      local attempt
      local -a packages=("$@")
      for attempt in 1 2 3; do
        if sudo apt-get install -y --no-install-recommends "${packages[@]}"; then
          return 0
        fi
        echo "::warning::依赖安装第 ${attempt} 次失败，刷新 APT 索引后重试。"
        sudo apt-get clean || true
        sudo rm -rf /var/lib/apt/lists/*
        apt_update_retry || return 1
        sleep "$attempt"
      done
      echo "::error::依赖安装连续失败。"
      return 1
    }

    disable_broken_chrome_source
    apt_update_retry
    apt_install_retry ccache python3 git curl build-essential libssl-dev bison flex libelf-dev dwarves

}

# ---- 配置自托管下载缓存 ----
cache-configure() {
    python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" configure

}

# ---- 配置 ccache ----
ccache() {
    mkdir -p ~/.cache/bazel
    ccache --version
    ccache --max-size=2G
    ccache --set-config=compression=true
    echo "CCACHE_DIR=$HOME/.ccache" >> $GITHUB_ENV

}

# ---- 下载工具链 ----
toolchain() {
    AOSP_MIRROR=https://android.googlesource.com
    BRANCH=main-kernel-build-2024

    retry_toolchain_clone() {
      local repo_url="$1"
      local target_dir="$2"
      local attempt
      for attempt in 1 2 3; do
        rm -rf "$target_dir"
        if python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone \
          "$repo_url" "$target_dir" --ref "$BRANCH" --depth 1; then
          return 0
        fi
        echo "::warning::工具链克隆失败: $repo_url (attempt ${attempt}/3)"
        sleep "$((attempt * 5))"
      done
      echo "::error::工具链克隆连续失败: $repo_url"
      return 1
    }

    retry_toolchain_clone "$AOSP_MIRROR/kernel/prebuilts/build-tools" kernel-build-tools
    retry_toolchain_clone "$AOSP_MIRROR/platform/system/tools/mkbootimg" mkbootimg

}

# ---- 生成签名密钥 ----
sign-key() {
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 > $BOOT_SIGN_KEY_PATH

}

# ---- 配置 Git ----
git-config() {
    git config --global user.name "BuildBot"
    git config --global user.email "BuildGkiKernel@gmail.com"

}

case "${1:-}" in
    deps-install) deps-install ;;
    cache-configure) cache-configure ;;
    ccache) ccache ;;
    toolchain) toolchain ;;
    sign-key) sign-key ;;
    git-config) git-config ;;
    *)
        echo "用法: $0 {deps-install|cache-configure|ccache|toolchain|sign-key|git-config}" >&2
        exit 2
        ;;
esac
