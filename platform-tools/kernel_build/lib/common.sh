#!/usr/bin/env bash
# 内核构建脚本共用的工具函数。
#
# 这些脚本由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/<层>/<脚本>.sh <子命令>`
# 的方式调用，也可以由 run-clean-build.sh 在本地串联执行。脚本内部只依赖环境变量，
# 不感知 GitHub Actions 的模板语法；workflow 的 inputs 由 clean/env.sh summary 导出为 ABK_INPUT_*。

set -euo pipefail

ABK_TOOL_STAGE="$(basename "${BASH_SOURCE[1]:-$0}" .sh)"

abk_log() {
    printf '[%s] %s\n' "$ABK_TOOL_STAGE" "$*"
}

abk_warn() {
    printf '::warning::%s\n' "$*" >&2
}

abk_die() {
    printf '::error::%s\n' "$*" >&2
    exit 1
}

# abk_require_env VAR [VAR...]
abk_require_env() {
    local name
    for name in "$@"; do
        if [ -z "${!name:-}" ]; then
            abk_die "缺少必需的环境变量: $name"
        fi
    done
}

# abk_require_input NAME...：检查 ABK_INPUT_* 形式的工作流输入
abk_require_input() {
    local name
    for name in "$@"; do
        if [ -z "${!name:-}" ]; then
            abk_die "缺少必需的工作流输入: $name（应由 clean/env.sh summary 导出）"
        fi
    done
}

# abk_env_add KEY VALUE：写入 $GITHUB_ENV（若存在）并导出到当前 shell
abk_env_add() {
    local key="$1"
    local value="$2"
    if [ -n "${GITHUB_ENV:-}" ]; then
        printf '%s=%s\n' "$key" "$value" >>"$GITHUB_ENV"
    fi
    export "$key=$value"
}

# abk_env_add_multiline KEY VALUE：写入可能含换行的值
abk_env_add_multiline() {
    local key="$1"
    local value="$2"
    if [ -n "${GITHUB_ENV:-}" ]; then
        {
            printf '%s<<ABK_EOF\n' "$key"
            printf '%s\n' "$value"
            printf 'ABK_EOF\n'
        } >>"$GITHUB_ENV"
    fi
    export "$key=$value"
}

# abk_summary TEXT：追加到 $GITHUB_STEP_SUMMARY（若存在）
abk_summary() {
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        printf '%s\n' "$1" >>"$GITHUB_STEP_SUMMARY"
    fi
}

# abk_clone URL DEST [--ref REF] [--depth N]：经由仓库的缓存下载器克隆
abk_clone() {
    python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone "$@"
}

# abk_feature_disabled ID：custom_git 事务目录中该特性被禁用
abk_feature_disabled() {
    [ "${ABK_INPUT_SOURCE_MODE:-}" = "custom_git" ] &&
        [ -f "${ABK_CUSTOM_SOURCE_TRANSACTION_DIR:-/nonexistent}/$1.disabled" ]
}

# abk_ensure_defconfig_value CFG VALUE：幂等地在 $DEFCONFIG 中设置一项
abk_ensure_defconfig_value() {
    local cfg="$1"
    local value="$2"
    local line="${cfg}=${value}"

    if grep -qxF "$line" "$DEFCONFIG"; then
        return 0
    fi

    if grep -Eq "^${cfg}=|^# ${cfg} is not set$" "$DEFCONFIG"; then
        sed -i -E "s|^${cfg}=.*|${line}|; s|^# ${cfg} is not set$|${line}|" "$DEFCONFIG"
    else
        printf '%s\n' "$line" >>"$DEFCONFIG"
    fi
}

# abk_disable_defconfig_symbol CFG：确保该符号显式关闭
abk_disable_defconfig_symbol() {
    local cfg="$1"
    if grep -qxF "# ${cfg} is not set" "$DEFCONFIG"; then
        return 0
    fi
    sed -i -E "/^${cfg}=/d" "$DEFCONFIG"
    printf '# %s is not set\n' "$cfg" >>"$DEFCONFIG"
}

# abk_export_inputs：把 workflow 的 inputs（已由薄壳步骤以 ABK_INPUT_* 传入）固化到 $GITHUB_ENV，
# 供后续脚本步骤读取。必须在 clean/env.sh summary 中调用一次。
abk_export_inputs() {
    local name value
    for name in \
        ABK_INPUT_SOURCE_MODE \
        ABK_INPUT_SOURCE_REPO \
        ABK_INPUT_SOURCE_REF \
        ABK_INPUT_SOURCE_PRIVATE \
        ABK_INPUT_SOURCE_COMMIT \
        ABK_INPUT_SOURCE_DEFCONFIGS \
        ABK_INPUT_SOURCE_DEVICE_LABEL \
        ABK_INPUT_SOURCE_FULL_KERNEL_VERSION \
        ABK_INPUT_SOURCE_ANDROID_VERSION \
        ABK_INPUT_KERNEL_VERSION_OVERRIDE \
        ABK_INPUT_ANDROID_VERSION \
        ABK_INPUT_KERNEL_VERSION \
        ABK_INPUT_SUB_LEVEL \
        ABK_INPUT_OS_PATCH_LEVEL \
        ABK_INPUT_KSU_VARIANT \
        ABK_INPUT_KSU_BRANCH \
        ABK_INPUT_CUSTOM_REF \
        ABK_INPUT_VERSION \
        ABK_INPUT_REVISION \
        ABK_INPUT_BUILD_TIME \
        ABK_INPUT_USE_ZRAM \
        ABK_INPUT_USE_BBG \
        ABK_INPUT_USE_DDK \
        ABK_INPUT_USE_NTSYNC \
        ABK_INPUT_USE_NETWORKING \
        ABK_INPUT_USE_KPM \
        ABK_INPUT_USE_REKERNEL \
        ABK_INPUT_ENABLE_SUSFS \
        ABK_INPUT_SUPP_OP \
        ABK_INPUT_VIRTUALIZATION_SUPPORT \
        ABK_INPUT_ZRAM_FULL_ALGO \
        ABK_INPUT_ZRAM_EXTRA_ALGOS \
        ABK_INPUT_KPM_PASSWORD \
        ABK_INPUT_CUSTOM_EXTERNAL_MODULES \
        ABK_INPUT_CUSTOM_KERNEL_OPTIONS \
        ABK_INPUT_MANAGER_PACKAGE \
        ABK_INPUT_MANAGER_CERT_SIZE \
        ABK_INPUT_MANAGER_CERT_SHA256 \
        ABK_INPUT_UPLOAD_AUX_ARTIFACTS; do
        value="${!name-}"
        if [ -n "${GITHUB_ENV:-}" ]; then
            printf '%s=%s\n' "$name" "$value" >>"$GITHUB_ENV"
        fi
    done
}

# abk_dir_of_script：当前脚本所在目录
abk_dir_of_script() {
    cd "$(dirname "${BASH_SOURCE[1]:-$0}")" && pwd
}
