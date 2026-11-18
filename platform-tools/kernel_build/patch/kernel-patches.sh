#!/usr/bin/env bash
# KernelSU 补丁模块编排：克隆模块仓库、按依赖分组打补丁、合并工作区、生成 NyaSU 补丁。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/kernel-patches.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

PATCHER="$GITHUB_WORKSPACE/.github/scripts/kernel-patcher.py"


# ---- 克隆 KernelSU 补丁模块 ----
modules-clone() {
    set -euo pipefail

    abk_require_env GITHUB_WORKSPACE

    abk_env_add KSUHOOK_MODULE_MANIFEST "$GITHUB_WORKSPACE/ksuhook_modules.tsv"

    if [ -z "${ABK_INPUT_KSUHOOK_MODULES:-}" ]; then
        abk_log "未配置 KernelSU 补丁模块，跳过克隆。"
        : > "$KSUHOOK_MODULE_MANIFEST"
        exit 0
    fi

    python3 "$PATCHER" clone \
        --modules "$ABK_INPUT_KSUHOOK_MODULES" \
        --dest "$GITHUB_WORKSPACE" \
        --manifest "$KSUHOOK_MODULE_MANIFEST"
}


# ---- 应用 KernelSU 补丁模块 ----
apply() {
    set -euo pipefail

    abk_require_env GITHUB_WORKSPACE KERNEL_ROOT
    abk_require_input ABK_INPUT_ANDROID_VERSION ABK_INPUT_KERNEL_VERSION

    if [ -z "${ABK_INPUT_KSUHOOK_MODULES:-}" ]; then
        abk_log "未配置 KernelSU 补丁模块，跳过补丁编排。"
        exit 0
    fi

    sdk_dir="$GITHUB_WORKSPACE/ksuhook-sdk/sdk"
    if [ ! -d "$sdk_dir/ksuhook" ]; then
        abk_die "未找到 NyaSU SDK: $sdk_dir（检出步骤是否执行？）"
    fi

    python3 "$PATCHER" apply \
        --manifest "${KSUHOOK_MODULE_MANIFEST:-$GITHUB_WORKSPACE/ksuhook_modules.tsv}" \
        --kernel-root "$KERNEL_ROOT" \
        --sdk "$sdk_dir" \
        --android-version "$ABK_INPUT_ANDROID_VERSION" \
        --kernel-version "$ABK_INPUT_KERNEL_VERSION" \
        --workroot "${KSUHOOK_WORKROOT:-${RUNNER_TEMP:-/tmp}/ksuhook-work}" \
        --summary "${GITHUB_STEP_SUMMARY:-}"
}

case "${1:-}" in
    modules-clone) modules-clone ;;
    apply) apply ;;
    *)
        echo "用法: $0 {modules-clone|apply}" >&2
        exit 2
        ;;
esac
