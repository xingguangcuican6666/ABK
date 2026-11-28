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
