#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/kernelsu.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 确定 KernelSU 分支 ----
resolve-ref() {
    cd "$GITHUB_WORKSPACE"
    bash .github/scripts/resolve-ksu-ref.sh

}

# ---- 添加 KernelSU ----
install() {
    cd "$KERNEL_ROOT"
    case "${ABK_INPUT_KSU_VARIANT}" in
      "NyaSU")
        echo "添加 NyaSU..."
        requested_sha="${RESOLVED_KSU_SHA:-$BRANCH}"
        expected_repo="xingguangcuican6666/NyaSU"
        actual_repo="${RESOLVED_KSU_REPO:-$expected_repo}"
        if [ "$actual_repo" != "$expected_repo" ]; then
          abk_die "NyaSU 来源仓库不匹配: $actual_repo"
        fi
        if ! [[ "$requested_sha" =~ ^[A-Fa-f0-9]{40}$ ]]; then
          abk_die "NyaSU 必须使用解析后的 40 位 commit SHA: $requested_sha"
        fi
        git clone "https://github.com/${expected_repo}.git" KernelSU
        git -C KernelSU checkout --detach "$requested_sha"
        resolved_head="$(git -C KernelSU rev-parse HEAD)"
        if [ "$resolved_head" != "$requested_sha" ]; then
          abk_die "NyaSU 检出 SHA 不匹配: 请求 $requested_sha，实际 $resolved_head"
        fi
        if [ ! -f KernelSU/kernel/Kbuild ] || [ ! -f KernelSU/kernel/Kconfig ]; then
          abk_die "NyaSU commit 缺少 kernel/Kbuild 或 kernel/Kconfig"
        fi
        driver_dir=""
        if [ -d common/drivers ]; then
          driver_dir="common/drivers"
        elif [ -d drivers ]; then
          driver_dir="drivers"
        else
          abk_die "GKI 源码缺少 common/drivers 和 drivers 目录"
        fi
        if [ ! -f "$driver_dir/Makefile" ] || [ ! -f "$driver_dir/Kconfig" ]; then
          abk_die "NyaSU 驱动目录缺少 Makefile 或 Kconfig: $driver_dir"
        fi
        if [ -e "$driver_dir/kernelsu" ] && [ ! -L "$driver_dir/kernelsu" ]; then
          abk_die "拒绝覆盖现有 KernelSU 驱动目录: $driver_dir/kernelsu"
        fi
        ln -sfn "$(realpath --relative-to="$driver_dir" KernelSU/kernel)" "$driver_dir/kernelsu"
        grep -qF 'obj-$(CONFIG_KSU) += kernelsu/' "$driver_dir/Makefile" || printf '\nobj-$(CONFIG_KSU) += kernelsu/\n' >> "$driver_dir/Makefile"
        grep -qF 'source "drivers/kernelsu/Kconfig"' "$driver_dir/Kconfig" || sed -i '/endmenu/i\source "drivers/kernelsu/Kconfig"' "$driver_dir/Kconfig"
        export BRANCH="$resolved_head"
        ;;
      *)
        abk_die "未知 KSU 变体: ${ABK_INPUT_KSU_VARIANT}（仅支持 NyaSU；None 由工作流 if 跳过）"
        ;;
    esac

    if [ -d "KernelSU/.git" ]; then
      KSU_GIT_VERSION=$(git -C KernelSU rev-list --count HEAD)
      KSU_VERSION=$((30000 + KSU_GIT_VERSION))
      printf 'KSU_VERSION=%s\n' "$KSU_VERSION" >> "${GITHUB_ENV:-/dev/null}"
      KSU_LATEST_COMMIT_DATE=$(git -C KernelSU log -1 --date=format:'%Y-%m-%d %H:%M:%S %z' --format:'%cd')
      echo "KSU_LATEST_COMMIT_DATE=$KSU_LATEST_COMMIT_DATE" >> $GITHUB_ENV
    else
      echo "KSU_LATEST_COMMIT_DATE=未知" >> $GITHUB_ENV
    fi

}

case "${1:-}" in
    resolve-ref) resolve-ref ;;
    install) install ;;
    *)
        echo "用法: $0 {resolve-ref|install}" >&2
        exit 2
        ;;
esac
