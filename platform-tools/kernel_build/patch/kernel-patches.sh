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

    abk_env_add PATCH_MODULE_MANIFEST "$GITHUB_WORKSPACE/patch_modules.tsv"

    if [ -z "${ABK_INPUT_PATCH_MODULES:-}" ]; then
        abk_log "未配置 KernelSU 补丁模块，跳过克隆。"
        : > "$PATCH_MODULE_MANIFEST"
        exit 0
    fi

    NYASU_SDK_DIR="$GITHUB_WORKSPACE/nyasu-sdk/sdk" python3 "$PATCHER" clone \
        --modules "$ABK_INPUT_PATCH_MODULES" \
        --dest "$GITHUB_WORKSPACE" \
        --manifest "$PATCH_MODULE_MANIFEST"
}


# ---- 应用 KernelSU 补丁模块 ----
apply() {
    set -euo pipefail

    abk_require_env GITHUB_WORKSPACE KERNEL_ROOT
    abk_require_input ABK_INPUT_ANDROID_VERSION ABK_INPUT_KERNEL_VERSION

    if [ -z "${ABK_INPUT_PATCH_MODULES:-}" ]; then
        abk_log "未配置 KernelSU 补丁模块，跳过补丁编排。"
        exit 0
    fi

    sdk_dir="$GITHUB_WORKSPACE/nyasu-sdk/sdk"
    if [ ! -d "$sdk_dir/ksuhook" ]; then
        abk_die "未找到 NyaSU SDK: $sdk_dir（检出步骤是否执行？）"
    fi

    NYASU_SDK_DIR="$sdk_dir" python3 "$PATCHER" apply \
        --manifest "${PATCH_MODULE_MANIFEST:-$GITHUB_WORKSPACE/patch_modules.tsv}" \
        --kernel-root "$KERNEL_ROOT" \
        --sdk "$sdk_dir" \
        --android-version "$ABK_INPUT_ANDROID_VERSION" \
        --kernel-version "$ABK_INPUT_KERNEL_VERSION" \
        --manager-cert-size "${ABK_MANAGER_CERT_SIZE:-1407}" \
        --manager-cert-sha256 "${ABK_MANAGER_CERT_SHA256:-34e5e843952277759603cd0f949770b24c868530d80d7baeff08776a7e132b16}" \
        --workroot "${NYASU_WORKROOT:-${RUNNER_TEMP:-/tmp}/nyasu-patch-work}" \
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
