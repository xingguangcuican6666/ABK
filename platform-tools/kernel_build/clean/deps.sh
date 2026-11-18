#!/usr/bin/env bash
# 干净 GKI 内核：环境、校验、工具链、源码同步、编译与打包。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/clean/deps.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 克隆依赖仓库 ----
deps-clone() {
    ANYKERNEL_BRANCH="gki-2.0"
    SUSFS_BRANCH="gki-${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}"

    retry_git_clone() {
      local repo_url="$1"
      local branch="$2"
      local target_dir="$3"
      local depth="${4:-1}"
      local attempt
      rm -rf "$target_dir"
      for attempt in 1 2 3; do
        if python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone \
          "$repo_url" "$target_dir" --ref "$branch" --depth "$depth"; then
          return 0
        fi
        echo "::warning::git clone 失败: $repo_url@$branch (attempt $attempt)"
        rm -rf "$target_dir"
        sleep "$attempt"
      done
      echo "::error::git clone 最终失败: $repo_url@$branch"
      return 1
    }

    echo "克隆 AnyKernel3..."
    python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone \
      https://github.com/WildKernels/AnyKernel3.git AnyKernel3 --ref "$ANYKERNEL_BRANCH"
    rm -rf AnyKernel3/.git

    if [ "${ABK_INPUT_ENABLE_SUSFS}" == "true" ]; then
      echo "克隆 SUSFS (分支: $SUSFS_BRANCH)..."
      SUSFS_DEPTH=1
      CONFIG_FILE="config/config"
      if [ "$ABK_LOCAL_CACHE_ENABLED" = "true" ] && [ -f "$CONFIG_FILE" ] && grep -q '^custom=true$' "$CONFIG_FILE"; then
        CUSTOM_COMMIT="$(sed -n "s/^${SUSFS_BRANCH}=//p" "$CONFIG_FILE" | head -n1)"
        # Resolve abbreviated commits from branch history, not remote ref names.
        # Cache the full history once so later pinned builds can reuse it.
        if [ -n "$CUSTOM_COMMIT" ]; then
          SUSFS_DEPTH=0
        fi
      fi
      retry_git_clone https://gitlab.com/simonpunk/susfs4ksu.git "$SUSFS_BRANCH" susfs4ksu "$SUSFS_DEPTH"

      CONFIG_FILE="config/config"
      if [ -f "$CONFIG_FILE" ]; then
        CUSTOM_ENABLED=$(grep "^custom=" "$CONFIG_FILE" | cut -d'=' -f2)
        if [ "$CUSTOM_ENABLED" == "true" ]; then
          CUSTOM_COMMIT=$(grep "^${SUSFS_BRANCH}=" "$CONFIG_FILE" | cut -d'=' -f2)
          if [ -n "$CUSTOM_COMMIT" ]; then
            echo "切换 SUSFS 到自定义提交: $CUSTOM_COMMIT"
            cd susfs4ksu
            # 浅克隆 (--depth 1) 只含分支 HEAD，历史里较旧的固定提交不在其中，
            # 直接 checkout 会因 "reference is not a tree" 失败，先补全历史。
            if ! git cat-file -e "${CUSTOM_COMMIT}^{commit}" 2>/dev/null; then
              git fetch --unshallow origin "$SUSFS_BRANCH" 2>/dev/null \
                || git fetch --depth=1000 origin "$SUSFS_BRANCH" 2>/dev/null \
                || true
            fi
            git checkout "$CUSTOM_COMMIT"
            cd ..
          fi
        fi
      fi

      SUSFS_LATEST_COMMIT_DATE=$(git -C susfs4ksu log -1 --date=format:'%Y-%m-%d %H:%M:%S %z' --format='%cd')
      echo "SUSFS_LATEST_COMMIT_DATE=$SUSFS_LATEST_COMMIT_DATE" >> $GITHUB_ENV
      echo "SUSFS 仓库最新提交日期: $SUSFS_LATEST_COMMIT_DATE"
    else
      echo "SUSFS 已禁用，跳过 SUSFS 仓库克隆。"
      echo "SUSFS_LATEST_COMMIT_DATE=禁用" >> $GITHUB_ENV
    fi

    echo "准备补丁资源..."
    python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone \
      https://github.com/WildKernels/kernel_patches.git kernel_patches
    python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone \
      https://github.com/ShirkNeko/SukiSU_patch.git SukiSU_patch
    echo "使用当前仓库补丁目录: $ZZH_PATCHES"
    python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone \
      https://github.com/Numbersf/Action-Build.git Action-Build --depth 1

}

case "${1:-}" in
    deps-clone) deps-clone ;;
    *)
        echo "用法: $0 {deps-clone}" >&2
        exit 2
        ;;
esac
