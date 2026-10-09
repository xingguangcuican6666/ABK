#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="${ABK_LOCAL_BUILD_STATE_DIR:-$ROOT_DIR/.local-build}"
ENV_FILE="$STATE_DIR/env.sh"
export ABK_LOCAL_BUILD_WORKSPACE_DIR="${ABK_LOCAL_BUILD_WORKSPACE_DIR:-}"

CLEAN_OUT=0
RESEED=0
NO_PACKAGE=0
PRINT_ENV=0

usage() {
    cat <<'EOF'
Usage: ./rebuild.sh [--clean-out] [--reseed] [--no-package] [--print-env]

  --clean-out   Remove build outputs before compiling.
  --reseed      Reset the workspace in place back to the template baseline.
  --no-package  Stop after kernel build, skip AnyKernel3 and boot image packaging.
  --print-env   Print loaded environment and exit.
EOF
}

log_info() {
    printf '[rebuild] %s\n' "$*"
}

log_warn() {
    printf '[rebuild][warn] %s\n' "$*" >&2
}

log_error() {
    printf '[rebuild][error] %s\n' "$*" >&2
    exit 1
}

require_dir() {
    local path="$1"
    [[ -d "$path" ]] || log_error "missing directory: $path"
}

require_file() {
    local path="$1"
    [[ -f "$path" ]] || log_error "missing file: $path"
}

read_shell_kv() {
    local file="$1"
    local key="$2"

    [[ -f "$file" ]] || return 0
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

bool_is_true() {
    case "${1,,}" in
        1|true|yes|on) return 0 ;;
        *) return 1 ;;
    esac
}

normalize_variant() {
    case "${1,,}" in
        none) printf 'None\n' ;;
        official) printf 'Official\n' ;;
        sukisu) printf 'SukiSU\n' ;;
        resukisu) printf 'ReSukiSU\n' ;;
        *) log_error "unsupported KSU_VARIANT: $1" ;;
    esac
}

normalize_track() {
    case "${1,,}" in
        stable|"stable(标准)") printf 'Stable(标准)\n' ;;
        dev|"dev(开发)") printf 'Dev(开发)\n' ;;
        custom|"custom(自定义)") printf 'Custom(自定义)\n' ;;
        latest|"latest(最新)")
            log_error "KSU_TRACK=Latest(最新) is not supported in local rebuild.sh. Use Stable(标准), Dev(开发), or Custom(自定义)."
            ;;
        *) log_error "unsupported KSU_TRACK: $1" ;;
    esac
}

parse_args() {
    while (($# > 0)); do
        case "$1" in
            --clean-out)
                CLEAN_OUT=1
                ;;
            --reseed)
                RESEED=1
                ;;
            --no-package)
                NO_PACKAGE=1
                ;;
            --print-env)
                PRINT_ENV=1
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                log_error "unknown argument: $1"
                ;;
        esac
        shift
    done
}

source_env() {
    [[ -f "$ENV_FILE" ]] || log_error "missing $ENV_FILE. Run ./init.sh first."
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    : "${TEMPLATE_NAME:=$(basename "$TEMPLATE_ROOT")}"
    : "${TEMPLATE_BRANCH:=unknown}"
    : "${TEMPLATE_COMMON_BRANCH:=unknown}"
    : "${USE_CUSTOM_EXTERNAL_MODULES:=false}"
    : "${CUSTOM_EXTERNAL_MODULES:=}"
    : "${CUSTOM_EXTERNAL_MODULES_MANIFEST:=$STATE_DATA_DIR/custom_external_modules.tsv}"
    : "${CUSTOM_EXTERNAL_MODULES_ROOT:=$WORKSPACE_DIR/custom_modules}"
    : "${VIRTUALIZATION_SUPPORT:=off}"
    : "${VIRTUALIZATION_SOURCE:=$SOURCES_DIR/Droidspaces-OSS}"
    : "${VIRTUALIZATION_SUPPORT_PATCHES:=$VIRTUALIZATION_SOURCE/Documentation/resources/kernel-patches/GKI}"
    : "${REVISION:=r1}"
    : "${ABK_MANAGER_CERT_ENV_FILE:=$ABK_SOURCE/app/signing/abk-manager-cert.env}"
    : "${ABK_MANAGER_PACKAGE:=$(read_shell_kv "$ABK_MANAGER_CERT_ENV_FILE" ABK_MANAGER_PACKAGE)}"
    : "${ABK_MANAGER_CERT_SIZE:=$(read_shell_kv "$ABK_MANAGER_CERT_ENV_FILE" ABK_MANAGER_CERT_SIZE)}"
    : "${ABK_MANAGER_CERT_SHA256:=$(read_shell_kv "$ABK_MANAGER_CERT_ENV_FILE" ABK_MANAGER_CERT_SHA256)}"
    : "${ABK_MANAGER_PACKAGE:=com.abk.kernel}"
    : "${ABK_MANAGER_CERT_SIZE:=1407}"
    : "${ABK_MANAGER_CERT_SHA256:=34e5e843952277759603cd0f949770b24c868530d80d7baeff08776a7e132b16}"
}

setup_logging() {
    mkdir -p "$LOGS_DIR"
    BUILD_LOG="$LOGS_DIR/rebuild-$(date -u +%Y%m%d-%H%M%S).log"
    exec > >(tee -a "$BUILD_LOG") 2>&1
    log_info "logging to $BUILD_LOG"
}

assert_supported_template() {
    [[ "$ANDROID_VERSION" == "$TEMPLATE_ANDROID_VERSION" ]] || log_error "ANDROID_VERSION=$ANDROID_VERSION does not match template $TEMPLATE_ANDROID_VERSION"
    [[ "$KERNEL_VERSION" == "$TEMPLATE_KERNEL_VERSION" ]] || log_error "KERNEL_VERSION=$KERNEL_VERSION does not match template $TEMPLATE_KERNEL_VERSION"
    [[ "$SUB_LEVEL" == "$TEMPLATE_SUB_LEVEL" ]] || log_error "SUB_LEVEL=$SUB_LEVEL does not match template $TEMPLATE_SUB_LEVEL"
    [[ "$OS_PATCH_LEVEL" == "$TEMPLATE_OS_PATCH_LEVEL" ]] || log_error "OS_PATCH_LEVEL=$OS_PATCH_LEVEL does not match template $TEMPLATE_OS_PATCH_LEVEL"
}

clean_bazel_artifacts() {
    local root="$1"

    rm -rf "$root/out" \
           "$root/bazel-bin" \
           "$root/bazel-out" \
           "$root/bazel-testlogs"

    find "$root" -mindepth 1 -maxdepth 1 -type d -name 'bazel-*' -exec rm -rf {} + 2>/dev/null || true
}

ensure_layout() {
    require_dir "$TEMPLATE_ROOT"
    require_dir "$SOURCES_DIR"
    require_dir "$ABK_SOURCE"
    require_dir "$ANYKERNEL3_SOURCE"
    require_dir "$KERNEL_PATCHES_SOURCE"
    require_dir "$SUKISU_PATCHES_SOURCE"
    require_dir "$ACTION_BUILD_SOURCE"
    require_dir "$GCC_SOURCE"
    if [[ "${VIRTUALIZATION_SUPPORT:-off}" != "off" ]]; then
        require_dir "$VIRTUALIZATION_SOURCE"
    fi

    if [[ ! -d "$KERNEL_ROOT" ]]; then
        (( RESEED == 1 )) || log_error "missing $KERNEL_ROOT. Run ./init.sh again or use --reseed."
        return 0
    fi

    require_dir "$KERNEL_ROOT"
    require_file "$DEFCONFIG"
    require_file "$KERNEL_ROOT/build/kernel/kleaf/impl/stamp.bzl"
    require_file "$AVBTOOL"
    require_file "$MKBOOTIMG"
    require_file "$UNPACK_BOOTIMG"
    require_file "$BOOT_SIGN_KEY_PATH"
    mkdir -p "$ARTIFACTS_DIR" "$CACHE_DIR" "$STATE_DATA_DIR"
}

seed_workspace() {
    log_info "re-seeding workspace from template"

    rm -rf "$KERNEL_ROOT"
    mkdir -p "$WORKSPACE_DIR"

    rsync -a --delete \
        --exclude='out/' \
        --exclude='bazel-bin/' \
        --exclude='bazel-out/' \
        --exclude='bazel-testlogs/' \
        --exclude='bazel-*/' \
        "$TEMPLATE_ROOT"/ \
        "$KERNEL_ROOT"/

    clean_bazel_artifacts "$KERNEL_ROOT"

    rm -rf "$KERNEL_ROOT/gcc"
    rsync -a --delete "$GCC_SOURCE"/ "$KERNEL_ROOT/gcc"/

    rm -f "$STATE_DATA_DIR/profile.env"
    rm -f "$STATE_DATA_DIR/current-profile.env"
}

reset_workspace_state() {
    local repo_tool="$KERNEL_ROOT/.repo/repo/repo"
    local project_list="$KERNEL_ROOT/.repo/project.list"
    local -a missing_projects=()

    log_info "resetting workspace repositories in place"

    require_file "$repo_tool"
    require_file "$project_list"

    if [[ -d "$KERNEL_ROOT/KernelSU" ]]; then
        rm -rf "$KERNEL_ROOT/KernelSU"
    fi
    if [[ -d "$KERNEL_ROOT/Baseband-guard" ]]; then
        rm -rf "$KERNEL_ROOT/Baseband-guard"
    fi
    if [[ -d "$KERNEL_ROOT/gcc" ]]; then
        rm -rf "$KERNEL_ROOT/gcc"
    fi
    if [[ -d "$CUSTOM_EXTERNAL_MODULES_ROOT" ]]; then
        rm -rf "$CUSTOM_EXTERNAL_MODULES_ROOT"
    fi
    rm -rf "$WORKSPACE_DIR/staging"
    rm -f "$CUSTOM_EXTERNAL_MODULES_MANIFEST"

    while IFS= read -r rel_path; do
        [[ -n "$rel_path" ]] || continue
        if [[ -d "$KERNEL_ROOT/$rel_path/.git" || -L "$KERNEL_ROOT/$rel_path/.git" ]]; then
            git -C "$KERNEL_ROOT/$rel_path" reset --hard HEAD >/dev/null
            git -C "$KERNEL_ROOT/$rel_path" clean -fdx >/dev/null
        else
            missing_projects+=("$rel_path")
        fi
    done <"$project_list"

    if ((${#missing_projects[@]} > 0)); then
        log_info "restoring missing repo projects (${#missing_projects[@]})"
        (
            cd "$KERNEL_ROOT"
            "$repo_tool" sync -c -j4 --force-sync --no-clone-bundle --no-tags "${missing_projects[@]}"
        )
    fi

    rsync -a --delete "$GCC_SOURCE"/ "$KERNEL_ROOT/gcc"/

    clean_bazel_artifacts "$KERNEL_ROOT"
    mkdir -p "$ARTIFACTS_DIR" "$CACHE_DIR" "$STATE_DATA_DIR"
    rm -f "$STATE_DATA_DIR/profile.env" "$STATE_DATA_DIR/current-profile.env"
}

clean_outputs() {
    log_info "cleaning build outputs"
    clean_bazel_artifacts "$KERNEL_ROOT"
    rm -rf "$ARTIFACTS_DIR"/*
    mkdir -p "$ARTIFACTS_DIR" "$CACHE_DIR"
}

resolve_ksu_ref() {
    local variant track
    variant="$(normalize_variant "$KSU_VARIANT")"
    track="$(normalize_track "$KSU_TRACK")"

    case "$variant:$track" in
        Official:'Stable(标准)') printf '%s\n' '3f388ef137c78e1ca0c92c0ada3b8717cdcc4302' ;;
        Official:'Dev(开发)') printf '%s\n' '290609c945728fc93d85735918793567588103c1' ;;
        SukiSU:'Stable(标准)') printf '%s\n' 'c5af9eadac43b1f0b9751471be78e6eef681554b' ;;
        SukiSU:'Dev(开发)') printf '%s\n' 'aac170bcb86ce45516b3e2c0e2b32b57004b8f73' ;;
        ReSukiSU:'Stable(标准)') printf '%s\n' 'b6706363b95525acd4007da19edadb1d41b2ea27' ;;
        ReSukiSU:'Dev(开发)') printf '%s\n' 'fb771414f249ab886c09f2cfcbbf91c4b4dab2d1' ;;
        None:'Stable(标准)'|None:'Dev(开发)'|None:'Custom(自定义)') printf '%s\n' '' ;;
        *:'Custom(自定义)')
            [[ -n "$KSU_CUSTOM_REF" ]] || log_error "KSU_CUSTOM_REF must be set when KSU_TRACK=Custom(自定义)"
            printf '%s\n' "$KSU_CUSTOM_REF"
            ;;
        *)
            log_error "unsupported KSU variant/track combination: $variant / $track"
            ;;
    esac
}

resolve_ksu_setup_url() {
    local variant
    variant="$(normalize_variant "$KSU_VARIANT")"
    case "$variant" in
        Official) printf '%s\n' 'https://raw.githubusercontent.com/tiann/KernelSU/main/kernel/setup.sh' ;;
        SukiSU) printf '%s\n' 'https://raw.githubusercontent.com/SukiSU-Ultra/SukiSU-Ultra/main/kernel/setup.sh' ;;
        ReSukiSU) printf '%s\n' 'https://raw.githubusercontent.com/ReSukiSU/ReSukiSU/main/kernel/setup.sh' ;;
        None) printf '%s\n' '' ;;
        *) log_error "unsupported KSU_VARIANT: $variant" ;;
    esac
}

resolve_ksu_repo_url() {
    local variant
    variant="$(normalize_variant "$KSU_VARIANT")"
    case "$variant" in
        Official) printf '%s\n' 'https://github.com/tiann/KernelSU.git' ;;
        SukiSU) printf '%s\n' 'https://github.com/SukiSU-Ultra/SukiSU-Ultra.git' ;;
        ReSukiSU) printf '%s\n' 'https://github.com/ReSukiSU/ReSukiSU.git' ;;
        None) printf '%s\n' '' ;;
        *) log_error "unsupported KSU_VARIANT: $variant" ;;
    esac
}

render_profile_snapshot() {
    cat >"$STATE_DATA_DIR/current-profile.env" <<EOF
ANDROID_VERSION=$ANDROID_VERSION
KERNEL_VERSION=$KERNEL_VERSION
SUB_LEVEL=$SUB_LEVEL
OS_PATCH_LEVEL=$OS_PATCH_LEVEL
REVISION=$REVISION
KSU_VARIANT=$(normalize_variant "$KSU_VARIANT")
KSU_TRACK=$(normalize_track "$KSU_TRACK")
KSU_CUSTOM_REF=$KSU_CUSTOM_REF
ENABLE_SUSFS=$ENABLE_SUSFS
VIRTUALIZATION_SUPPORT=$VIRTUALIZATION_SUPPORT
USE_ZRAM=$USE_ZRAM
ZRAM_FULL_ALGO=$ZRAM_FULL_ALGO
ZRAM_EXTRA_ALGOS=$ZRAM_EXTRA_ALGOS
USE_BBG=$USE_BBG
USE_DDK=$USE_DDK
USE_NTSYNC=$USE_NTSYNC
USE_NETWORKING=$USE_NETWORKING
USE_KPM=$USE_KPM
KPM_PASSWORD=$KPM_PASSWORD
USE_REKERNEL=$USE_REKERNEL
SUPP_OP=$SUPP_OP
USE_CUSTOM_EXTERNAL_MODULES=$USE_CUSTOM_EXTERNAL_MODULES
CUSTOM_EXTERNAL_MODULES=$CUSTOM_EXTERNAL_MODULES
VERSION_INPUT=$VERSION_INPUT
BUILD_TIME=$BUILD_TIME
ABK_MANAGER_PACKAGE=$ABK_MANAGER_PACKAGE
ABK_MANAGER_CERT_SIZE=$ABK_MANAGER_CERT_SIZE
ABK_MANAGER_CERT_SHA256=$(printf '%s' "$ABK_MANAGER_CERT_SHA256" | tr '[:upper:]' '[:lower:]')
EOF
}

check_profile_compatibility() {
    render_profile_snapshot
    if [[ ! -f "$STATE_DATA_DIR/profile.env" ]]; then
        return 0
    fi
    if cmp -s "$STATE_DATA_DIR/profile.env" "$STATE_DATA_DIR/current-profile.env"; then
        return 0
    fi
    log_error "build knobs changed since the last seeded profile. Re-run with --reseed."
}

store_profile_snapshot() {
    cp "$STATE_DATA_DIR/current-profile.env" "$STATE_DATA_DIR/profile.env"
}

ensure_defconfig_value() {
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

disable_defconfig_symbol() {
    local cfg="$1"
    local line="# ${cfg} is not set"

    if grep -qxF "$line" "$DEFCONFIG"; then
        return 0
    fi

    if grep -Eq "^${cfg}=|^# ${cfg} is not set$" "$DEFCONFIG"; then
        sed -i -E "s|^${cfg}=.*|${line}|; s|^# ${cfg} is not set$|${line}|" "$DEFCONFIG"
    else
        printf '%s\n' "$line" >>"$DEFCONFIG"
    fi
}

ensure_line_once() {
    local file="$1"
    local line="$2"
    grep -qF "$line" "$file" || printf '%s\n' "$line" >>"$file"
}

remove_line_if_present() {
    local file="$1"
    local line="$2"
    grep -qF "$line" "$file" || return 0
    python3 - "$file" "$line" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
line = sys.argv[2]
lines = path.read_text().splitlines()
path.write_text("\n".join([l for l in lines if l != line]) + "\n")
PY
}

trim_value() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s\n' "$value"
}

patch_kallsyms_filters() {
    local path="$KERNEL_ROOT/common/scripts/kallsyms.c"
    require_file "$path"

    python3 - "$path" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text()
needle = '\t\t".plt_branch.",\t\t/* ppc stub */\n'
insert = needle + '\t\t"__kcfi_typeid_",\t/* CFI type identifiers, including namespaced */\n'
if '"__kcfi_typeid_",\t/* CFI type identifiers, including namespaced */' not in text and needle in text:
    text = text.replace(needle, insert, 1)
    path.write_text(text)
PY
}

zram_uses_backend_objects() {
    local zram_kconfig="$KERNEL_ROOT/common/drivers/block/zram/Kconfig"
    [[ -f "$zram_kconfig" ]] && grep -q '^config ZRAM_BACKEND_ZSTDP$' "$zram_kconfig"
}

enable_zstdp_config() {
    ensure_defconfig_value CONFIG_CRYPTO_ZSTDP y
    disable_defconfig_symbol CONFIG_ZRAM_DEF_COMP_ZSTDP

    if zram_uses_backend_objects; then
        ensure_defconfig_value CONFIG_ZRAM_BACKEND_ZSTDP y
    fi
}

normalize_custom_stage() {
    local raw stage
    raw="$1"
    stage="$(printf '%s' "$raw" | tr '[:upper:] -' '[:lower:]__')"
    case "$stage" in
        after_patch) printf '%s\n' "after_patch" ;;
        before_build|befor_build) printf '%s\n' "before_build" ;;
        *) return 1 ;;
    esac
}

read_actual_sublevel() {
    local value
    value="$(awk '/^SUBLEVEL = / {print $3}' "$KERNEL_ROOT/common/Makefile" 2>/dev/null || true)"
    printf '%s\n' "${value:-$SUB_LEVEL}"
}

prepare_custom_external_modules() {
    local modules_input manifest root index block lhs repo_url stage_raw stage source_type source_path module_name module_dir
    local entry_kind group_repo_url child_id descriptor

    bool_is_true "$USE_CUSTOM_EXTERNAL_MODULES" || {
        rm -f "$CUSTOM_EXTERNAL_MODULES_MANIFEST"
        rm -rf "$CUSTOM_EXTERNAL_MODULES_ROOT"
        return 0
    }

    modules_input="$(printf '%s' "$CUSTOM_EXTERNAL_MODULES" | tr -d '\r')"
    manifest="$CUSTOM_EXTERNAL_MODULES_MANIFEST"
    root="$CUSTOM_EXTERNAL_MODULES_ROOT"

    rm -rf "$root"
    mkdir -p "$root" "$(dirname "$manifest")"
    : >"$manifest"

    if [[ -z "$(trim_value "$modules_input")" ]]; then
        log_warn "USE_CUSTOM_EXTERNAL_MODULES=true but CUSTOM_EXTERNAL_MODULES is empty; skipping module preparation"
        return 0
    fi

    IFS='|' read -r -a module_blocks <<<"$modules_input"
    index=0
    for block in "${module_blocks[@]}"; do
        block="$(trim_value "$block")"
        [[ -n "$block" ]] || continue

        [[ "$block" == *";"* ]] || log_error "invalid custom module entry (missing ';'): $block"

        lhs="$(trim_value "${block%%;*}")"
        stage_raw="$(trim_value "${block#*;}")"
        [[ -n "$lhs" && -n "$stage_raw" ]] || log_error "invalid custom module entry: $block"

        if ! stage="$(normalize_custom_stage "$stage_raw")"; then
            log_error "unsupported custom module stage: $stage_raw"
        fi

        entry_kind="module"
        group_repo_url=""
        child_id=""

        case "$lhs" in
            module:*)
                repo_url="$(trim_value "${lhs#module:}")"
                ;;
            set:*)
                descriptor="$(trim_value "${lhs#set:}")"
                [[ "$descriptor" == *"#"* ]] || log_error "invalid module-set entry (missing child id): $block"
                group_repo_url="$(trim_value "${descriptor%%#*}")"
                child_id="$(trim_value "${descriptor#*#}")"
                repo_url="$group_repo_url"
                entry_kind="module_set_child"
                ;;
            *)
                repo_url="$lhs"
                ;;
        esac

        [[ -n "$repo_url" ]] || log_error "invalid custom module repo/path: $block"
        if [[ "$entry_kind" == "module_set_child" && -z "$child_id" ]]; then
            log_error "module-set child id is empty: $block"
        fi

        source_type="remote"
        source_path="$repo_url"
        case "$repo_url" in
            http://*|https://*|git://*|ssh://*|git@*) ;;
            *)
                source_type="local"
                if [[ "$repo_url" = /* ]]; then
                    source_path="$repo_url"
                else
                    source_path="$ROOT_DIR/$repo_url"
                fi
                [[ -d "$source_path" ]] || log_error "custom module local path does not exist: $source_path"
                ;;
        esac

        index=$((index + 1))
        module_name="${repo_url##*/}"
        module_name="${module_name%.git}"
        module_name="$(printf '%s' "$module_name" | sed 's/[^A-Za-z0-9._-]/_/g')"
        [[ -n "$module_name" ]] || module_name="module"
        module_dir="$root/$(printf '%02d' "$index")-$module_name"

        log_info "preparing custom module #$index ($stage/$entry_kind): $repo_url"
        if [[ "$source_type" == "remote" ]]; then
            git clone --depth 1 "$source_path" "$module_dir"
        else
            rsync -a --delete --exclude='.git' "$source_path"/ "$module_dir"/
        fi

        printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
            "$stage" \
            "$module_dir" \
            "$repo_url" \
            "$entry_kind" \
            "$group_repo_url" \
            "$child_id" >>"$manifest"
    done

    if [[ ! -s "$manifest" ]]; then
        log_warn "no valid custom modules resolved from CUSTOM_EXTERNAL_MODULES"
    fi
}

run_custom_external_modules() {
    local stage="$1"
    local manifest="$CUSTOM_EXTERNAL_MODULES_MANIFEST"
    local executed=0 entry_stage module_dir repo_or_path entry_kind group_repo_url child_id setup_path
    local actual_sublevel

    bool_is_true "$USE_CUSTOM_EXTERNAL_MODULES" || return 0
    [[ -s "$manifest" ]] || {
        log_info "no custom module manifest found for stage $stage"
        return 0
    }

    actual_sublevel="$(read_actual_sublevel)"

    while IFS=$'\t' read -r entry_stage module_dir repo_or_path entry_kind group_repo_url child_id; do
        [[ "$entry_stage" == "$stage" ]] || continue

        setup_path="$module_dir/setup.sh"
        [[ -f "$setup_path" ]] || log_error "custom module missing setup.sh: $repo_or_path ($module_dir)"

        chmod +x "$setup_path"
        log_info "running custom module [$stage]: $repo_or_path"
        (
            export CUSTOM_EXTERNAL_MODULE_STAGE="$stage"
            export ABK_BUILD_ANDROID_VERSION="$ANDROID_VERSION"
            export ABK_BUILD_KERNEL_VERSION="$KERNEL_VERSION"
            export ABK_BUILD_SUB_LEVEL="$SUB_LEVEL"
            export ABK_BUILD_OS_PATCH_LEVEL="$OS_PATCH_LEVEL"
            export ABK_BUILD_REVISION="$REVISION"
            export ABK_BUILD_KSU_VARIANT="$(normalize_variant "$KSU_VARIANT")"
            export ABK_BUILD_KSU_BRANCH="$(normalize_track "$KSU_TRACK")"
            export ABK_BUILD_KSU_REF="$(resolve_ksu_ref)"
            export ABK_BUILD_VERSION="$VERSION_INPUT"
            export ABK_BUILD_TIME="$BUILD_TIME"
            export ABK_BUILD_VIRTUALIZATION_SUPPORT="$VIRTUALIZATION_SUPPORT"
            export ABK_BUILD_ZRAM_EXTRA_ALGOS="$ZRAM_EXTRA_ALGOS"
            export ABK_FEATURE_USE_ZRAM="$USE_ZRAM"
            export ABK_FEATURE_USE_BBG="$USE_BBG"
            export ABK_FEATURE_USE_DDK="$USE_DDK"
            export ABK_FEATURE_USE_NTSYNC="$USE_NTSYNC"
            export ABK_FEATURE_USE_NETWORKING="$USE_NETWORKING"
            export ABK_FEATURE_USE_KPM="$USE_KPM"
            export ABK_FEATURE_USE_REKERNEL="$USE_REKERNEL"
            export ABK_FEATURE_ENABLE_SUSFS="$ENABLE_SUSFS"
            export ABK_FEATURE_SUPP_OP="$SUPP_OP"
            export ABK_FEATURE_ZRAM_FULL_ALGO="$ZRAM_FULL_ALGO"
            export ABK_MANAGER_PACKAGE="$ABK_MANAGER_PACKAGE"
            export ABK_MANAGER_CERT_SIZE="$ABK_MANAGER_CERT_SIZE"
            export ABK_MANAGER_CERT_SHA256="$(printf '%s' "$ABK_MANAGER_CERT_SHA256" | tr '[:upper:]' '[:lower:]')"
            export REMOTE_BRANCH="${TEMPLATE_COMMON_BRANCH:-}"
            export ACTUAL_SUBLEVEL="$actual_sublevel"
            export ABK_MODULE_ENTRY_KIND="${entry_kind:-module}"
            export ABK_MODULE_GROUP_REPO_URL="${group_repo_url:-}"
            export ABK_MODULE_CHILD_ID="${child_id:-}"
            export ROOT_DIR STATE_DIR SOURCES_DIR WORKSPACE_DIR ARTIFACTS_DIR LOGS_DIR CACHE_DIR KEYS_DIR STATE_DATA_DIR
            export TEMPLATE_ROOT KERNEL_ROOT DEFCONFIG ABK_SOURCE ANYKERNEL3_SOURCE KERNEL_PATCHES_SOURCE SUKISU_PATCHES_SOURCE ACTION_BUILD_SOURCE SUSFS_SOURCE GCC_SOURCE VIRTUALIZATION_SOURCE VIRTUALIZATION_SUPPORT_PATCHES
            cd "$module_dir"
            bash "$setup_path"
        )
        executed=$((executed + 1))
    done <"$manifest"

    if (( executed == 0 )); then
        log_info "no custom modules configured for stage $stage"
    fi
}

apply_stock_config() {
    local stock_src="$ABK_SOURCE/config/stock_defconfig"
    local stock_dst="$KERNEL_ROOT/common/arch/arm64/configs/stock_defconfig"
    local target_makefile="$KERNEL_ROOT/common/kernel/Makefile"

    [[ -f "$stock_src" ]] || return 0

    mkdir -p "$(dirname "$stock_dst")"
    cp "$stock_src" "$stock_dst"

    if grep -qF '$(obj)/config_data: $(KCONFIG_CONFIG) FORCE' "$target_makefile"; then
        sed -i 's|$(obj)/config_data: $(KCONFIG_CONFIG) FORCE|$(obj)/config_data: arch/arm64/configs/stock_defconfig FORCE|' "$target_makefile"
    fi
}

apply_glibc_fix() {
    local current_sub="$SUB_LEVEL"
    local glibc_version

    [[ "$current_sub" =~ ^[0-9]+$ ]] || current_sub=99999
    if ! {
        [[ "$ANDROID_VERSION" == "android13" && "$KERNEL_VERSION" == "5.10" && "$current_sub" -le 186 ]] ||
        [[ "$ANDROID_VERSION" == "android13" && "$KERNEL_VERSION" == "5.15" && "$current_sub" -le 119 ]] ||
        [[ "$ANDROID_VERSION" == "android14" && "$KERNEL_VERSION" == "6.1" && "$current_sub" -le 43 ]]
    }; then
        return 0
    fi

    glibc_version="$(ldd --version 2>/dev/null | head -n 1 | awk '{print $NF}')"
    if [[ "$(printf '%s\n' "2.38" "$glibc_version" | sort -V | head -n 1)" != "2.38" ]]; then
        return 0
    fi

    log_info "applying glibc 2.38 compatibility fix"
    (
        cd "$KERNEL_ROOT/common"
        sed -i '/\$(Q)\$(MAKE) -C \$(SUBCMD_SRC) OUTPUT=\$(abspath \$(dir \$@))\/ \$(abspath \$@)/s//$(Q)$(MAKE) -C $(SUBCMD_SRC) EXTRA_CFLAGS="$(CFLAGS)" OUTPUT=$(abspath $(dir $@))\/ $(abspath $@)/' tools/bpf/resolve_btfids/Makefile || true
    )
}

apply_supp_op() {
    bool_is_true "$SUPP_OP" || return 0
    log_info "injecting OnePlus 8E support"
    cp "$ABK_SOURCE/hmbird_patch.c" "$KERNEL_ROOT/common/drivers/hmbird_patch.c"
    ensure_line_once "$KERNEL_ROOT/common/drivers/Makefile" 'obj-y += hmbird_patch.o'
}

inject_kernelsu() {
    local variant ref setup_url repo_url actual_head
    variant="$(normalize_variant "$KSU_VARIANT")"
    [[ "$variant" == "None" ]] && return 0

    ref="$(resolve_ksu_ref)"
    setup_url="$(resolve_ksu_setup_url)"
    repo_url="$(resolve_ksu_repo_url)"

    log_info "injecting $variant ($ref)"
    (
        cd "$KERNEL_ROOT"
        curl -LSs "$setup_url" | bash
    )

    if [[ -d "$KERNEL_ROOT/KernelSU/.git" && -n "$ref" ]]; then
        log_info "refreshing KernelSU repository for clean pin"
        rm -rf "$KERNEL_ROOT/KernelSU"
        git clone --no-checkout "$repo_url" "$KERNEL_ROOT/KernelSU"
        log_info "pinning KernelSU repository to $ref"
        git -C "$KERNEL_ROOT/KernelSU" fetch origin "$ref"
        git -C "$KERNEL_ROOT/KernelSU" checkout --detach "$ref"
        actual_head="$(git -C "$KERNEL_ROOT/KernelSU" rev-parse HEAD)"
        if [[ "$ref" =~ ^[0-9a-fA-F]{40}$ && "$actual_head" != "$ref" ]]; then
            log_error "KernelSU HEAD mismatch after pinning: expected $ref, got $actual_head"
        fi
    fi

    if [[ "$variant" == "Official" && -f "$KERNEL_ROOT/KernelSU/kernel/Kbuild" ]]; then
        local git_version ksu_version
        git_version="$(git -C "$KERNEL_ROOT/KernelSU" rev-list --count HEAD)"
        ksu_version="$((20000 + git_version))"
        sed -i "s/DKSU_VERSION=16/DKSU_VERSION=${ksu_version}/" "$KERNEL_ROOT/KernelSU/kernel/Kbuild"
    fi
}

current_sublevel() {
    local value="${SUB_LEVEL:-99999}"
    [[ "$value" =~ ^[0-9]+$ ]] || value=99999
    printf '%s\n' "$value"
}

sub_in_range() {
    local value="$1"
    local min="$2"
    local max="$3"
    [[ "$value" =~ ^[0-9]+$ ]] || return 1
    (( value >= min && value <= max ))
}

fix_task_mmu_show_pad() {
    local file="$KERNEL_ROOT/common/fs/proc/task_mmu.c"
    [[ -f "$file" ]] || return 0

    if grep -qF 'goto show_pad;' "$file"; then
        sed -i -e 's/goto show_pad;/return 0;/' "$file"
    fi

    if grep -q '^[[:space:]]*show_pad:[[:space:]]*$' "$file" && ! grep -qF 'goto show_pad;' "$file"; then
        sed -i '/^[[:space:]]*show_pad:[[:space:]]*$/d' "$file"
    fi
}

apply_unicode_bypass_fix() {
    bool_is_true "$ENABLE_SUSFS" || return 0
    (
        cd "$KERNEL_ROOT/common"
        patch -p1 --forward <"$ACTION_BUILD_SOURCE/patches/unicode_bypass_fix_6.1+.patch" || true
    )
}

apply_resukisu_susfs_pre_compat() {
    [[ "$(normalize_variant "$KSU_VARIANT")" == "ReSukiSU" ]] || return 0
    bool_is_true "$ENABLE_SUSFS" || return 0

    (
        cd "$KERNEL_ROOT"
        python3 - <<'PY'
from pathlib import Path

roots = [Path("KernelSU/kernel"), Path("common/drivers/kernelsu"), Path("drivers/kernelsu")]
candidates = []
seen = set()
for base in roots:
    if not base.exists():
        continue
    resolved = base.resolve()
    if resolved in seen:
        continue
    markers = ["Kbuild", "core/init.c", "feature/sucompat.c", "sulog/event.c"]
    if any((base / marker).exists() for marker in markers):
        candidates.append(base)
        seen.add(resolved)

if not candidates:
    raise SystemExit("未找到实际 ReSukiSU 源码目录，无法应用 SUSFS 兼容修复")

for kernel_dir in candidates:
    tree_text = "\n".join(
        path.read_text(errors="ignore")
        for path in kernel_dir.rglob("*")
        if path.is_file()
    )
    ksu_c = kernel_dir / "ksu.c"
    if ksu_c.exists():
        text = ksu_c.read_text()
        anchor = '#include "infra/kernel_compat.h"\n'
        if (
            anchor in text
            and "DEFINE_STATIC_KEY_TRUE(ksu_is_init_rc_hook_enabled);" not in tree_text
        ):
            block = (
                "\n/* ABK: ReSukiSU builtin SUSFS expects these inline hook static keys. */\n"
                "#ifdef CONFIG_KSU_SUSFS\n"
                "DEFINE_STATIC_KEY_TRUE(ksu_is_init_rc_hook_enabled);\n"
                "DEFINE_STATIC_KEY_TRUE(ksu_is_input_hook_enabled);\n"
                "#if defined(KSU_COMPAT_USE_STATIC_KEY)\n"
                "DEFINE_STATIC_KEY_FALSE(ksu_init_rc_hook_key_false);\n"
                "DEFINE_STATIC_KEY_FALSE(ksu_input_hook_key_false);\n"
                "#endif\n"
                "#endif\n"
            )
            ksu_c.write_text(text.replace(anchor, anchor + block, 1))

    selinux_c = kernel_dir / "selinux/selinux.c"
    if selinux_c.exists():
        text = selinux_c.read_text()
        compat_symbols = [
            "ksu_selinux_hide_running",
            "struct selinux_state fake_state",
            "struct selinux_policy *backup_sepolicy",
            "security_context_to_sid_with_policy",
            "security_sid_to_context_with_policy",
            "security_compute_av_user_with_policy",
        ]
        if not all(symbol in tree_text for symbol in compat_symbols):
            anchor = 'u32 ksu_file_sid __read_mostly = 0;\n'
            block = (
                "\n/* ABK: simonpunk SUSFS SELinux hooks reference KernelSU selinux_hide globals. */\n"
                "#ifdef CONFIG_KSU_SUSFS\n"
                "struct selinux_state fake_state;\n"
                "bool ksu_selinux_hide_running __read_mostly = false;\n"
                "struct selinux_policy *backup_sepolicy;\n"
                "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 6, 0)\n"
                "int security_context_to_sid_with_policy(struct selinux_policy *policy, const char *scontext, u32 scontext_len,\n"
                "                                        u32 *sid, u32 def_sid, unsigned int gfp_flags)\n"
                "{\n"
                "    return -22;\n"
                "}\n"
                "int security_sid_to_context_with_policy(struct selinux_policy *policy, u32 sid, char **scontext,\n"
                "                                        u32 *scontext_len)\n"
                "{\n"
                "    return -22;\n"
                "}\n"
                "void security_compute_av_user_with_policy(struct selinux_policy *policy, u32 ssid, u32 tsid, u16 tclass,\n"
                "                                          struct av_decision *avd)\n"
                "{\n"
                "}\n"
                "#endif\n"
                "#endif\n"
            )
            if anchor in text:
                selinux_c.write_text(text.replace(anchor, anchor + block, 1))

    selinux_hide_c = kernel_dir / "feature/selinux_hide.c"
    if selinux_hide_c.exists():
        text = selinux_hide_c.read_text()

        if "#include <linux/jump_label.h>\n" not in text and "#include <linux/version.h>\n" in text:
            text = text.replace(
                "#include <linux/version.h>\n",
                "#include <linux/version.h>\n#include <linux/jump_label.h>\n",
                1,
            )

        for old in (
            "__maybe_bool ksu_selinux_hide_enabled __read_mostly = false;\n",
            "__maybe_static bool ksu_selinux_hide_enabled __read_mostly = false;\n",
            "static bool ksu_selinux_hide_enabled __read_mostly = false;\n",
        ):
            if old in text:
                text = text.replace(old, "bool ksu_selinux_hide_enabled __read_mostly = false;\n", 1)

        old_static_key_block = (
            "#ifdef KSU_COMPAT_USE_STATIC_KEY\n"
            "// We should talk to you, susfs\n"
            "// Why use manual hook instead of auto hook\n"
            "__maybe_static DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);\n"
            "#else\n"
            "static bool fake_status_initialize_key __read_mostly = false;\n"
            "#endif\n"
        )
        if old_static_key_block in text:
            text = text.replace(
                old_static_key_block,
                "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);\n",
                1,
            )

        for old in (
            "__maybe_static struct page *fake_status = NULL;\n",
            "static struct page *fake_status = NULL;\n",
        ):
            if old in text:
                text = text.replace(old, "struct page *fake_status = NULL;\n", 1)

        for old, new in (
            ("__maybe_static void initialize_fake_status();\n", "void initialize_fake_status(void);\n"),
            ("static void initialize_fake_status();\n", "void initialize_fake_status(void);\n"),
            ("__maybe_static void initialize_fake_status()\n", "void initialize_fake_status(void)\n"),
            ("static void initialize_fake_status()\n", "void initialize_fake_status(void)\n"),
        ):
            if old in text:
                text = text.replace(old, new, 1)

        anchor = "bool ksu_selinux_hide_running __read_mostly = false;\n"
        inject = (
            "bool ksu_selinux_hide_running __read_mostly = false;\n"
            "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);\n"
            "struct page *fake_status = NULL;\n"
        )
        if anchor in text and "fake_status_initialize_key" not in text:
            text = text.replace(anchor, inject, 1)

        if "initialize_fake_status(" not in text:
            text += (
                "\nvoid initialize_fake_status(void)\n"
                "{\n"
                "}\n"
            )

        selinux_hide_c.write_text(text)

    sucompat_c = kernel_dir / "feature/sucompat.c"
    if sucompat_c.exists():
        text = sucompat_c.read_text()
        marker = "\n// dead code\n"
        guard = "#endif /* CONFIG_KSU_SUSFS */\n"
        if marker in text and guard not in text:
            sucompat_c.write_text(text.replace(marker, "\n" + guard + marker, 1))

    sulog_event_c = kernel_dir / "sulog/event.c"
    if sulog_event_c.exists():
        text = sulog_event_c.read_text()
        if "builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer" not in text:
            for old in (
                "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, USER_ARG_NULL, gfp);",
                "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, &USER_ARG_NULL, gfp);",
            ):
                if old in text:
                    new = (
                        "    /* ABK: builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer. */\n"
                        "#ifdef CONFIG_KSU_SUSFS\n"
                        "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, NULL, gfp);\n"
                        "#else\n"
                        f"{old}\n"
                        "#endif"
                    )
                    sulog_event_c.write_text(text.replace(old, new, 1))
                    break
PY
    )
}

apply_susfs_patch_stack() {
    local variant current_sub susfs_version susfs_fix_root susfs_ksu_fix_root patch_source
    variant="$(normalize_variant "$KSU_VARIANT")"
    bool_is_true "$ENABLE_SUSFS" || return 0
    [[ "$variant" != "None" ]] || return 0

    susfs_version="$(
        grep -E '^#define[[:space:]]+SUSFS_VERSION' "$SUSFS_SOURCE/kernel_patches/include/linux/susfs.h" \
        | awk '{print $3}' | tr -d '"' | sed 's/^v//'
    )"
    [[ -n "$susfs_version" ]] || susfs_version="2.0.0"

    for candidate in \
        "$KERNEL_PATCHES_SOURCE/wild/susfs_fix_patches/v${susfs_version}" \
        "$KERNEL_PATCHES_SOURCE/wild/archived/susfs_fix_patches/v${susfs_version}" \
        "$KERNEL_PATCHES_SOURCE/ksu/susfs_fix_patches/v${susfs_version}"
    do
        [[ -d "$candidate" ]] || continue
        susfs_fix_root="$candidate"
        break
    done
    [[ -n "${susfs_fix_root:-}" ]] || susfs_fix_root="$KERNEL_PATCHES_SOURCE/wild/archived/susfs_fix_patches/v2.0.0"
    [[ -d "$KERNEL_PATCHES_SOURCE/ksu/susfs_fix_patches/v${susfs_version}" ]] && susfs_ksu_fix_root="$KERNEL_PATCHES_SOURCE/ksu/susfs_fix_patches/v${susfs_version}"

    cp "$SUSFS_SOURCE/kernel_patches/50_add_susfs_in_gki-${ANDROID_VERSION}-${KERNEL_VERSION}.patch" "$KERNEL_ROOT/common/"
    cp "$SUSFS_SOURCE/kernel_patches/fs/"* "$KERNEL_ROOT/common/fs/"
    cp "$SUSFS_SOURCE/kernel_patches/include/linux/"* "$KERNEL_ROOT/common/include/linux/"

    (
        cd "$KERNEL_ROOT/common"
        patch -p1 <"50_add_susfs_in_gki-${ANDROID_VERSION}-${KERNEL_VERSION}.patch" || true
    )

    if [[ "$variant" == "SukiSU" || "$variant" == "ReSukiSU" ]]; then
        [[ -f "$KERNEL_ROOT/common/fs/susfs.c" ]] && \
            sed -i 's/^static void susfs_run_sus_path_loop(/void susfs_run_sus_path_loop(/' "$KERNEL_ROOT/common/fs/susfs.c"
    fi

    current_sub="$(current_sublevel)"
    if [[ "$ANDROID_VERSION" == "android14" && "$KERNEL_VERSION" == "6.1" ]] && sub_in_range "$current_sub" 25 162; then
        patch_source=""
        [[ -f "$susfs_fix_root/a14-6.1/base.c.patch" ]] && patch_source="$susfs_fix_root/a14-6.1/base.c.patch"
        [[ -z "$patch_source" && -n "${susfs_ksu_fix_root:-}" && -f "$susfs_ksu_fix_root/1_fix_base.c.patch" ]] && patch_source="$susfs_ksu_fix_root/1_fix_base.c.patch"
        if [[ -n "$patch_source" ]]; then
            (
                cd "$KERNEL_ROOT/common"
                patch -p1 <"$patch_source" || true
            )
        fi

        if grep -Eq 'susfs_is_current_proc_umounted|SUSFS_IS_INODE_SUS_MAP|SUSFS_IS_INODE_OPEN_REDIRECT' "$KERNEL_ROOT/common/fs/proc/base.c" \
            && ! grep -qF '#include <linux/susfs_def.h>' "$KERNEL_ROOT/common/fs/proc/base.c"; then
            if grep -qF '#include <linux/dma-buf.h>' "$KERNEL_ROOT/common/fs/proc/base.c"; then
                sed -i '/#include <linux\/dma-buf.h>/a #if defined(CONFIG_KSU_SUSFS_SUS_MAP) || defined(CONFIG_KSU_SUSFS_OPEN_REDIRECT)\n#include <linux\/susfs_def.h>\n#endif' "$KERNEL_ROOT/common/fs/proc/base.c"
            else
                sed -i '/#include <linux\/cpufreq_times.h>/a #if defined(CONFIG_KSU_SUSFS_SUS_MAP) || defined(CONFIG_KSU_SUSFS_OPEN_REDIRECT)\n#include <linux\/susfs_def.h>\n#endif' "$KERNEL_ROOT/common/fs/proc/base.c"
            fi
        fi

        if [[ "$OS_PATCH_LEVEL" != "2024-05" ]]; then
            fix_task_mmu_show_pad
        fi

        if grep -Eq 'DEFAULT_KSU_MNT_ID|susfs_mnt_id_ida' "$KERNEL_ROOT/common/fs/namespace.c" \
            && ! grep -qF '#include <linux/susfs_def.h>' "$KERNEL_ROOT/common/fs/namespace.c"; then
            sed -i '/#include <linux\/mnt_idmapping.h>/a #ifdef CONFIG_KSU_SUSFS_SUS_MOUNT\n#include <linux\/susfs_def.h>\n#endif' "$KERNEL_ROOT/common/fs/namespace.c"
            if ! grep -q 'extern bool susfs_is_current_ksu_domain' "$KERNEL_ROOT/common/fs/namespace.c"; then
                sed -i '/#include "internal.h"/a \\n#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT\nextern bool susfs_is_current_ksu_domain(void);\nextern struct static_key_true susfs_is_sdcard_android_data_not_decrypted;\n\n#define CL_COPY_MNT_NS BIT(25)\n\n#endif' "$KERNEL_ROOT/common/fs/namespace.c"
            fi
        fi
    fi
}

finalize_sukisu_resukisu_susfs_compat() {
    local variant
    variant="$(normalize_variant "$KSU_VARIANT")"
    [[ "$variant" == "SukiSU" || "$variant" == "ReSukiSU" ]] || return 0

    (
        cd "$KERNEL_ROOT"
        python3 - <<'PY'
from pathlib import Path
import re

def repair_sucompat(path: Path):
    text = path.read_text()
    changed = False
    marker = "\n// dead code\n"
    guard = "#endif /* CONFIG_KSU_SUSFS */\n"
    if marker in text and guard not in text:
        text = text.replace(marker, "\n" + guard + marker, 1)
        changed = True
    depth = 0
    out = []
    for line in text.splitlines(keepends=True):
        if re.match(r"^\s*#\s*(if|ifdef|ifndef)\b", line):
            depth += 1
            out.append(line)
        elif re.match(r"^\s*#\s*endif\b", line):
            if depth == 0:
                continue
            depth -= 1
            out.append(line)
        else:
            out.append(line)
    text = "".join(out)
    if changed:
        path.write_text(text)

for base in (Path("common/drivers/kernelsu"), Path("drivers/kernelsu"), Path("KernelSU/kernel")):
    if not base.exists():
        continue
    sucompat = base / "feature/sucompat.c"
    if sucompat.exists():
        repair_sucompat(sucompat)
    sulog = base / "sulog/event.c"
    if sulog.exists():
        text = sulog.read_text()
        if "builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer" not in text:
            for old in (
                "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, USER_ARG_NULL, gfp);",
                "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, &USER_ARG_NULL, gfp);",
            ):
                if old in text:
                    new = (
                        "    /* ABK: builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer. */\n"
                        "#ifdef CONFIG_KSU_SUSFS\n"
                        "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, NULL, gfp);\n"
                        "#else\n"
                        f"{old}\n"
                        "#endif"
                    )
                    sulog.write_text(text.replace(old, new, 1))
                    break
PY
    )
}

prepare_profile() {
    prepare_custom_external_modules
    apply_stock_config
    apply_glibc_fix
    apply_supp_op
    inject_kernelsu
    apply_resukisu_susfs_pre_compat
    apply_susfs_patch_stack

    cp "$DEFCONFIG" "$DEFCONFIG.orig"
}

apply_ntsync() {
    bool_is_true "$USE_NTSYNC" || return 0
    log_info "applying NTsync patch stack"
    (
        cd "$KERNEL_ROOT/common"
        patch -p1 --forward <"$KERNEL_PATCHES_SOURCE/common/ntsync/ntsync_base.patch" || true
        patch -p1 --forward <"$KERNEL_PATCHES_SOURCE/common/ntsync/ntsync_compat_${ANDROID_VERSION}-${KERNEL_VERSION}.patch" || true
    )
    if [[ -f "$KERNEL_ROOT/common/drivers/misc/Kconfig" ]]; then
        sed -i '/^config NTSYNC$/,/^[[:space:]]*help$/{ s/^[[:space:]]*default[[:space:]]\+m$/\tdefault\ty/; }' "$KERNEL_ROOT/common/drivers/misc/Kconfig"
    fi
    ensure_defconfig_value CONFIG_NTSYNC y
}

apply_networking() {
    bool_is_true "$USE_NETWORKING" || return 0
    log_info "enabling networking enhancements"

    ensure_defconfig_value CONFIG_IP_SET y
    ensure_defconfig_value CONFIG_IP_SET_MAX 65534
    ensure_defconfig_value CONFIG_IP_SET_BITMAP_IP y
    ensure_defconfig_value CONFIG_IP_SET_BITMAP_IPMAC y
    ensure_defconfig_value CONFIG_IP_SET_BITMAP_PORT y
    ensure_defconfig_value CONFIG_IP_SET_HASH_IP y
    ensure_defconfig_value CONFIG_IP_SET_HASH_IPMARK y
    ensure_defconfig_value CONFIG_IP_SET_HASH_IPPORT y
    ensure_defconfig_value CONFIG_IP_SET_HASH_IPPORTIP y
    ensure_defconfig_value CONFIG_IP_SET_HASH_IPPORTNET y
    ensure_defconfig_value CONFIG_IP_SET_HASH_IPMAC y
    ensure_defconfig_value CONFIG_IP_SET_HASH_MAC y
    ensure_defconfig_value CONFIG_IP_SET_HASH_NETPORTNET y
    ensure_defconfig_value CONFIG_IP_SET_HASH_NET y
    ensure_defconfig_value CONFIG_IP_SET_HASH_NETNET y
    ensure_defconfig_value CONFIG_IP_SET_HASH_NETPORT y
    ensure_defconfig_value CONFIG_IP_SET_HASH_NETIFACE y
    ensure_defconfig_value CONFIG_IP_SET_LIST_SET y
    ensure_defconfig_value CONFIG_NETFILTER_XT_MATCH_ADDRTYPE y
    ensure_defconfig_value CONFIG_NETFILTER_XT_SET y
    ensure_defconfig_value CONFIG_IP6_NF_NAT y
    ensure_defconfig_value CONFIG_IP6_NF_TARGET_MASQUERADE y
    ensure_defconfig_value CONFIG_TCP_CONG_ADVANCED y
    ensure_defconfig_value CONFIG_TCP_CONG_BIC y
    ensure_defconfig_value CONFIG_TCP_CONG_BBR y
    ensure_defconfig_value CONFIG_TCP_CONG_CUBIC y
    ensure_defconfig_value CONFIG_TCP_CONG_WESTWOOD y
    ensure_defconfig_value CONFIG_TCP_CONG_HTCP y
    ensure_defconfig_value CONFIG_DEFAULT_BBR y
    ensure_defconfig_value CONFIG_DEFAULT_TCP_CONG '"bbr"'
    ensure_defconfig_value CONFIG_NET_SCH_FQ y
    ensure_defconfig_value CONFIG_NET_SCH_FQ_CODEL y
}

apply_virtualization_support() {
    local slot="${VIRTUALIZATION_SUPPORT:-off}"
    local patch_file slot_name

    [[ "$slot" != "off" ]] || return 0
    [[ "$ANDROID_VERSION" == "android14" && "$KERNEL_VERSION" == "6.1" ]] || \
        log_error "virtualization support is currently implemented only for android14-6.1"

    case "$slot" in
        678|123|345) ;;
        *) log_error "VIRTUALIZATION_SUPPORT must be one of: off, 678, 123, 345" ;;
    esac

    if [[ ! -d "$VIRTUALIZATION_SOURCE/.git" ]]; then
        log_info "cloning virtualization support patches"
        git clone --depth 1 https://github.com/ravindu644/Droidspaces-OSS.git "$VIRTUALIZATION_SOURCE"
    fi

    slot_name="$(sed 's/\(.\)/\1_/g; s/_$//' <<<"$slot")"
    patch_file="$VIRTUALIZATION_SUPPORT_PATCHES/below-kernel-6.12/001.GKI-below-6.12-fix_sysvipc_kabi_${slot_name}.patch"

    (
        cd "$KERNEL_ROOT/common"
        if patch -p1 --forward --dry-run <"$patch_file" >/dev/null 2>&1; then
            patch -p1 --forward <"$patch_file"
        elif patch -p1 --reverse --dry-run <"$patch_file" >/dev/null 2>&1; then
            :
        elif [[ "$slot" == "678" ]]; then
            perl -0pi -e 's/#ifdef CONFIG_SYSVIPC\n\tstruct sysv_sem\s+sysvsem;\n\tstruct sysv_shm\s+sysvshm;\n#endif/#ifdef CONFIG_SYSVIPC\n\t\/\/ struct sysv_sem\t\t\tsysvsem;\n\t\/\/ struct sysv_shm\t\t\tsysvshm;\n#endif/s' include/linux/sched.h
            perl -0pi -e 's/\tANDROID_KABI_RESERVE\(6\);\n\tANDROID_KABI_RESERVE\(7\);\n\tANDROID_KABI_RESERVE\(8\);/\n#ifdef CONFIG_SYSVIPC\n\tANDROID_KABI_USE(6, struct sysv_sem sysvsem);\n\t_ANDROID_KABI_REPLACE(ANDROID_KABI_RESERVE(7); ANDROID_KABI_RESERVE(8), struct sysv_shm sysvshm);\n#else\n\tANDROID_KABI_RESERVE(6);\n\tANDROID_KABI_RESERVE(7);\n\tANDROID_KABI_RESERVE(8);\n#endif/s' include/linux/sched.h
        else
            log_error "virtualization SYSVIPC patch failed for slot $slot"
        fi
    )

    enable_virtualization_config() {
        local cfg="$1"
        if grep -q "^${cfg}=y" "$DEFCONFIG"; then
            return 0
        elif grep -q "^# ${cfg} is not set" "$DEFCONFIG"; then
            sed -i "s/^# ${cfg} is not set$/${cfg}=y/" "$DEFCONFIG"
        else
            echo "${cfg}=y" >> "$DEFCONFIG"
        fi
    }

    enable_virtualization_config CONFIG_SYSVIPC
    enable_virtualization_config CONFIG_POSIX_MQUEUE
    enable_virtualization_config CONFIG_IPC_NS
    enable_virtualization_config CONFIG_PID_NS
    enable_virtualization_config CONFIG_DEVTMPFS
    enable_virtualization_config CONFIG_USER_NS
}

apply_zram_stack() {
    bool_is_true "$USE_ZRAM" || return 0
    [[ "$KERNEL_VERSION" != "6.12" ]] || log_error "ZRAM enhancement is disabled for kernel 6.12"

    log_info "applying ZRAM patch stack"
    (
        cd "$KERNEL_ROOT/common"
        rm -f lib/lz4/lz4_compress.c lib/lz4/lz4_decompress.c lib/lz4/lz4defs.h lib/lz4/lz4hc_compress.c
        cp -r "$ABK_SOURCE/zram/lz4/." ./lib/lz4/
        cp -r "$ABK_SOURCE/zram/include/linux/." ./include/linux/
        bash "$ABK_SOURCE/zram/apply_lz4_neon.sh"

        if [[ -f "fs/f2fs/Makefile" ]] && ! grep -qF 'f2fs-$(CONFIG_F2FS_IOSTAT) += iostat.o' "fs/f2fs/Makefile"; then
            printf '%s\n' 'f2fs-$(CONFIG_F2FS_IOSTAT) += iostat.o' >>"fs/f2fs/Makefile"
        fi

        cp -r "$SUKISU_PATCHES_SOURCE/other/zram/lz4k/include/linux/." ./include/linux/
        cp -r "$SUKISU_PATCHES_SOURCE/other/zram/lz4k/lib/." ./lib/
        cp -r "$SUKISU_PATCHES_SOURCE/other/zram/lz4k/crypto/." ./crypto/
        cp -r "$SUKISU_PATCHES_SOURCE/other/zram/lz4k_oplus" ./lib/

        for patch_name in lz4kd.patch lz4k_oplus.patch; do
            local_patch="$SUKISU_PATCHES_SOURCE/other/zram/zram_patch/${KERNEL_VERSION}/${patch_name}"
            [[ -f "$local_patch" ]] || continue
            cp "$local_patch" ./
            patch -p1 -F 3 <"$patch_name" || true
        done

        log_info "integrating zstdp support"
        mkdir -p "$CACHE_DIR"
        export RUNNER_TEMP="$CACHE_DIR"
        export ZZH_PATCHES="$ABK_SOURCE"
        bash "$ABK_SOURCE/zram/setup_zstdp.sh" integrate "$PWD"
    )
}

configure_zram_options() {
    local modules_bzl="$KERNEL_ROOT/common/modules.bzl"

    bool_is_true "$USE_ZRAM" || return 0

    if [[ "$KERNEL_VERSION" == "6.1" ]]; then
        ensure_defconfig_value CONFIG_ZSMALLOC y
        ensure_defconfig_value CONFIG_ZRAM y
    fi

    if grep -q '^CONFIG_ZSMALLOC=y$' "$DEFCONFIG" && grep -q '^CONFIG_ZRAM=y$' "$DEFCONFIG"; then
        cat "$ABK_SOURCE/config/zram.config" >>"$DEFCONFIG"
    fi

    if [[ -f "$modules_bzl" ]]; then
        if grep -q '^CONFIG_ZSMALLOC=y$' "$DEFCONFIG" && grep -q '^CONFIG_ZRAM=y$' "$DEFCONFIG"; then
            remove_line_if_present "$modules_bzl" '    "drivers/block/zram/zram.ko",'
            remove_line_if_present "$modules_bzl" '    "mm/zsmalloc.ko",'
        else
            ensure_line_once "$modules_bzl" '    "drivers/block/zram/zram.ko",'
            ensure_line_once "$modules_bzl" '    "mm/zsmalloc.ko",'
        fi
    fi

    if bool_is_true "$ZRAM_FULL_ALGO"; then
        ensure_defconfig_value CONFIG_CRYPTO_LZO y
        ensure_defconfig_value CONFIG_CRYPTO_LZ4 y
        ensure_defconfig_value CONFIG_CRYPTO_DEFLATE y
        ensure_defconfig_value CONFIG_CRYPTO_ZSTD y
        enable_zstdp_config
        ensure_defconfig_value CONFIG_ZRAM_DEF_COMP_LZORLE y
        ensure_defconfig_value CONFIG_ZRAM_DEF_COMP '"lzo-rle"'
    elif [[ -n "$ZRAM_EXTRA_ALGOS" ]]; then
        local algo
        IFS=',' read -r -a algos <<<"$ZRAM_EXTRA_ALGOS"
        for raw_algo in "${algos[@]}"; do
            algo="$(trim_value "$raw_algo")"
            [[ -n "$algo" ]] || continue
            case "${algo,,}" in
                lzo) ensure_defconfig_value CONFIG_CRYPTO_LZO y ;;
                lz4) ensure_defconfig_value CONFIG_CRYPTO_LZ4 y ;;
                lz4hc) ensure_defconfig_value CONFIG_CRYPTO_LZ4HC y ;;
                lz4k) ensure_defconfig_value CONFIG_CRYPTO_LZ4K y ;;
                lz4k_oplus) ensure_defconfig_value CONFIG_CRYPTO_LZ4K_OPLUS y ;;
                lz4kd) ensure_defconfig_value CONFIG_CRYPTO_LZ4KD y ;;
                deflate) ensure_defconfig_value CONFIG_CRYPTO_DEFLATE y ;;
                842) ensure_defconfig_value CONFIG_CRYPTO_842 y ;;
                zstd) ensure_defconfig_value CONFIG_CRYPTO_ZSTD y ;;
                zstdp) enable_zstdp_config ;;
                *) log_warn "unknown ZRAM algorithm: $algo" ;;
            esac
        done
    fi
}

apply_bbg() {
    bool_is_true "$USE_BBG" || return 0
    log_info "applying Baseband Guard"
    (
        cd "$KERNEL_ROOT"
        wget -qO- https://github.com/vc-teahouse/Baseband-guard/raw/main/setup.sh | bash
    )
    ensure_defconfig_value CONFIG_BBG y
    sed -i '/^config LSM$/,/^help$/{ /^[[:space:]]*default/ { /baseband_guard/! s/selinux/selinux,baseband_guard/ } }' "$KERNEL_ROOT/common/security/Kconfig"
}

apply_ddk() {
    bool_is_true "$USE_DDK" || return 0
    log_info "applying DDK patch stack"
    (
        cd "$KERNEL_ROOT"
        bash "$ABK_SOURCE/ddk/setup.sh"
    )
    require_file "$KERNEL_ROOT/common/include/linux/xingguang_ddk.h"
    ensure_defconfig_value CONFIG_XINGGUANG_DDK y
}

apply_rekernel() {
    bool_is_true "$USE_REKERNEL" || return 0

    local tmp_rekernel="$STATE_DATA_DIR/rekernel"
    log_info "integrating Re-Kernel"
    rm -rf "$tmp_rekernel"
    git clone --depth 1 https://github.com/Sakion-Team/Re-Kernel.git "$tmp_rekernel"

    mkdir -p "$KERNEL_ROOT/common/drivers/rekernel"
    cp "$tmp_rekernel/LKM-Source/rekernel.c" "$KERNEL_ROOT/common/drivers/rekernel/"
    cp "$tmp_rekernel/LKM-Source/rekernel.h" "$KERNEL_ROOT/common/drivers/rekernel/"

    cat >"$KERNEL_ROOT/common/drivers/rekernel/Kconfig" <<'EOF'
menu "Re:Kernel"
config REKERNEL
    bool "Re:Kernel support (GKI Vendor Hooks)"
    default y
    help
      Enable Re-Kernel support via GKI Vendor Hooks.

config REKERNEL_NETWORK
    bool "Re:Kernel NetReceive unfreeze support"
    depends on REKERNEL
    default n
endmenu
EOF

    cat >"$KERNEL_ROOT/common/drivers/rekernel/Makefile" <<'EOF'
obj-$(CONFIG_REKERNEL) += rekernel.o
ccflags-$(CONFIG_REKERNEL_NETWORK) += -DNETWORK_FILTER
EOF

    if ! grep -qF 'source "drivers/rekernel/Kconfig"' "$KERNEL_ROOT/common/drivers/Kconfig"; then
        sed -i '/^endmenu$/i source "drivers/rekernel/Kconfig"' "$KERNEL_ROOT/common/drivers/Kconfig"
    fi
    ensure_line_once "$KERNEL_ROOT/common/drivers/Makefile" 'obj-$(CONFIG_REKERNEL) += rekernel/'

    sed -i 's|#include <../android/binder_internal.h>|#include "../../drivers/android/binder_internal.h"|g' "$KERNEL_ROOT/common/drivers/rekernel/rekernel.c"
    grep -qF '#include <linux/seq_file.h>' "$KERNEL_ROOT/common/drivers/rekernel/rekernel.c" \
        || sed -i '/#include <trace\/hooks\/signal.h>/a #include <linux/seq_file.h>' "$KERNEL_ROOT/common/drivers/rekernel/rekernel.c"

    ensure_defconfig_value CONFIG_REKERNEL y
    ensure_defconfig_value CONFIG_REKERNEL_NETWORK y
}

configure_kernel_options() {
    local variant
    variant="$(normalize_variant "$KSU_VARIANT")"

    # zstdp symbol namespacing introduces names like
    # abk_zstdp___kcfi_typeid_*, which bypass the stock __kcfi_typeid_
    # prefix filter in scripts/kallsyms.c and break base-relative kallsyms.
    patch_kallsyms_filters

    if [[ "$variant" != "None" ]]; then
        ensure_defconfig_value CONFIG_KSU y
        ensure_defconfig_value CONFIG_TMPFS_XATTR y
        ensure_defconfig_value CONFIG_TMPFS_POSIX_ACL y
    fi

    if [[ "$variant" == "SukiSU" || "$variant" == "ReSukiSU" ]]; then
        if bool_is_true "$USE_KPM"; then
            ensure_defconfig_value CONFIG_KPM y
        else
            disable_defconfig_symbol CONFIG_KPM
        fi
    fi

    if bool_is_true "$ENABLE_SUSFS" && [[ "$variant" != "None" ]]; then
        ensure_defconfig_value CONFIG_KSU_SUSFS y
        ensure_defconfig_value CONFIG_KSU_SUSFS_SUS_PATH y
        ensure_defconfig_value CONFIG_KSU_SUSFS_SUS_MOUNT y
        ensure_defconfig_value CONFIG_KSU_SUSFS_SUS_KSTAT y
        ensure_defconfig_value CONFIG_KSU_SUSFS_SPOOF_UNAME y
        ensure_defconfig_value CONFIG_KSU_SUSFS_ENABLE_LOG y
        ensure_defconfig_value CONFIG_KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS y
        ensure_defconfig_value CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG y
        ensure_defconfig_value CONFIG_KSU_SUSFS_OPEN_REDIRECT y
        ensure_defconfig_value CONFIG_KSU_SUSFS_SUS_MAP y
    else
        disable_defconfig_symbol CONFIG_KSU_SUSFS
        disable_defconfig_symbol CONFIG_KSU_SUSFS_SUS_PATH
        disable_defconfig_symbol CONFIG_KSU_SUSFS_SUS_MOUNT
        disable_defconfig_symbol CONFIG_KSU_SUSFS_SUS_KSTAT
        disable_defconfig_symbol CONFIG_KSU_SUSFS_SPOOF_UNAME
        disable_defconfig_symbol CONFIG_KSU_SUSFS_ENABLE_LOG
        disable_defconfig_symbol CONFIG_KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS
        disable_defconfig_symbol CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG
        disable_defconfig_symbol CONFIG_KSU_SUSFS_OPEN_REDIRECT
        disable_defconfig_symbol CONFIG_KSU_SUSFS_SUS_MAP
    fi

    sed -i 's/check_defconfig//' "$KERNEL_ROOT/common/build.config.gki"
}

configure_kernel_name() {
    local clean_version ghash bid kmi_local

    sed -i '/^[[:space:]]*"protected_exports_list"[[:space:]]*:[[:space:]]*"android\/abi_gki_protected_exports_aarch64",$/d' "$KERNEL_ROOT/common/BUILD.bazel"
    sed -i '/kmi_symbol_list_strict_mode/d' "$KERNEL_ROOT/common/BUILD.bazel"
    rm -rf "$KERNEL_ROOT/common/android/abi_gki_protected_exports_"*
    sed -i '/stable_scmversion_cmd/s/-maybe-dirty//g' "$KERNEL_ROOT/build/kernel/kleaf/impl/stamp.bzl"

    clean_version="${VERSION_INPUT//[[:space:]]/}"
    if [[ -n "$clean_version" ]]; then
        clean_version="$(sed -E 's/^[0-9]+\.[0-9]+\.[0-9]+//' <<<"$clean_version")"
        sed -i "\$s|echo \"\$res\"|echo \"${clean_version}\"|" "$KERNEL_ROOT/common/scripts/setlocalversion" || true
        sed -i "/^CONFIG_LOCALVERSION=/ s/=\"[^\"]*\"/=\"${clean_version}\"/" "$KERNEL_ROOT/common/arch/arm64/configs/gki_defconfig" || true
        return 0
    fi

    ghash="$(git -C "$KERNEL_ROOT/common" rev-parse --verify HEAD | cut -c1-13)"
    bid="ab$((RANDOM % 90000000 + 10000000))"
    kmi_local="-${ANDROID_VERSION}-11-g${ghash}-${bid}-4k"
    sed -i "\$s|echo \"\$res\"|echo \"${kmi_local}\"|" "$KERNEL_ROOT/common/scripts/setlocalversion" || true
    sed -i "/^CONFIG_LOCALVERSION=/ s/=\"[^\"]*\"/=\"${kmi_local}\"/" "$KERNEL_ROOT/common/arch/arm64/configs/gki_defconfig" || true
}

set_build_time_override() {
    local datestr
    if [[ -n "$BUILD_TIME" && "$BUILD_TIME" != "N" && "$BUILD_TIME" != "n" ]]; then
        datestr="$BUILD_TIME"
    else
        datestr="$(TZ='UTC' date +'%a %b %d %T %Z %Y')"
    fi

    BUILD_TIMESTAMP="$datestr"
    export KBUILD_BUILD_TIMESTAMP="$datestr"
    export KBUILD_BUILD_VERSION=1

    if grep -q 'UTS_VERSION=' "$KERNEL_ROOT/common/scripts/mkcompile_h"; then
        perl -pi -e "s{UTS_VERSION=\"\\\$\\\(.*?\\\)\"}{UTS_VERSION=\"#1 SMP PREEMPT $datestr\"}" "$KERNEL_ROOT/common/scripts/mkcompile_h"
    else
        perl -0777 -pi -e "s{cat <<EOF}{cat <<EOF\n#undef UTS_VERSION\n#define UTS_VERSION \"#1 SMP PREEMPT $datestr\" } unless /UTS_VERSION/" "$KERNEL_ROOT/common/scripts/mkcompile_h"
    fi
}

finalize_profile() {
    apply_ntsync
    apply_networking
    apply_virtualization_support
    apply_unicode_bypass_fix
    apply_zram_stack
    configure_zram_options
    apply_bbg
    apply_ddk
    apply_rekernel
    run_custom_external_modules after_patch
    configure_kernel_options
    configure_kernel_name
    set_build_time_override
}

prepare_defconfig_fragment() {
    FRAG_PATH="$KERNEL_ROOT/common/arch/arm64/configs/ksu.fragment"
    diff "$DEFCONFIG.orig" "$DEFCONFIG" | grep '^>' | sed 's/^> //; s/^[[:space:]]*//' >"$FRAG_PATH" || true
    cp "$DEFCONFIG.orig" "$DEFCONFIG"
}

kernel_dist_dir() {
    case "$ANDROID_VERSION" in
        android12|android13)
            printf '%s\n' "$KERNEL_ROOT/out/${ANDROID_VERSION}-${KERNEL_VERSION}/dist"
            ;;
        *)
            printf '%s\n' "$KERNEL_ROOT/bazel-bin/common/kernel_aarch64"
            ;;
    esac
}

run_kernel_build() {
    local frag_flag=""
    local lto_flag="--lto=thin"
    local bazel_help=""
    local -a build_args
    local dist_dir=""

    mkdir -p "$CCACHE_DIR" "$BAZEL_DISK_CACHE"
    ccache --max-size=15G >/dev/null
    ccache --set-config=compression=true >/dev/null

    sed -i 's/BUILD_SYSTEM_DLKM=1/BUILD_SYSTEM_DLKM=0/' "$KERNEL_ROOT/common/build.config.gki.aarch64"
    sed -i '/MODULES_ORDER=android\/gki_aarch64_modules/d' "$KERNEL_ROOT/common/build.config.gki.aarch64"
    sed -i '/KMI_SYMBOL_LIST_STRICT_MODE/d' "$KERNEL_ROOT/common/build.config.gki.aarch64"

    (
        cd "$KERNEL_ROOT"
        if [[ -f "build/build.sh" ]]; then
            log_info "building kernel with legacy build/build.sh"
            LTO=thin BUILD_CONFIG=common/build.config.gki.aarch64 build/build.sh CC="/usr/bin/ccache clang"
        else
            log_info "building kernel with Bazel"
            if [[ -s "$FRAG_PATH" ]]; then
                frag_flag="--defconfig_fragment=//common:arch/arm64/configs/ksu.fragment"
            fi
            if [[ "$KERNEL_VERSION" == "6.12" ]]; then
                lto_flag="--lto=none"
            fi
            bazel_help="$(tools/bazel help build 2>&1 || true)"
            build_args=(
                "--disk_cache=$BAZEL_DISK_CACHE"
                "--config=fast"
                "$lto_flag"
            )
            if grep -Fq "kmi_symbol_list_strict_mode" <<<"$bazel_help"; then
                build_args+=("--nokmi_symbol_list_strict_mode")
            else
                log_warn "skipping unsupported Bazel flag: --nokmi_symbol_list_strict_mode"
            fi
            if grep -Fq "kmi_symbol_list_violations_check" <<<"$bazel_help"; then
                build_args+=("--nokmi_symbol_list_violations_check")
            else
                log_warn "skipping unsupported Bazel flag: --nokmi_symbol_list_violations_check"
            fi
            if [[ -n "$frag_flag" ]]; then
                build_args+=("$frag_flag")
            fi
            build_args+=("//common:kernel_aarch64_dist")
            tools/bazel build "${build_args[@]}"
        fi
    )

    dist_dir="$(kernel_dist_dir)"
    require_file "$dist_dir/Image"
}

apply_kpm_patch() {
    local target_path
    bool_is_true "$USE_KPM" || return 0

    case "$(normalize_variant "$KSU_VARIANT")" in
        SukiSU|ReSukiSU) ;;
        *) return 0 ;;
    esac

    target_path="$(kernel_dist_dir)"
    require_dir "$target_path"
    cp -r "$SUKISU_PATCHES_SOURCE/kpm/patch_linux" "$target_path/patch"
    (
        cd "$target_path"
        chmod 0755 patch
        if [[ -n "$KPM_PASSWORD" ]]; then
            ./patch -s "$KPM_PASSWORD"
        else
            ./patch
        fi
        rm -f Image
        mv oImage Image
    )
}

write_metadata() {
    local meta="$ARTIFACTS_DIR/build-meta.txt"
    local ksu_dir="$KERNEL_ROOT/KernelSU"
    local ksu_commit="disabled"
    local build_timestamp="${BUILD_TIMESTAMP:-${KBUILD_BUILD_TIMESTAMP:-${BUILD_TIME:-unknown}}}"

    if [[ -d "$ksu_dir/.git" ]]; then
        ksu_commit="$(git -C "$ksu_dir" rev-parse HEAD)"
    fi

    cat >"$meta" <<EOF
built_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
template_branch=$TEMPLATE_BRANCH
template_common_branch=${TEMPLATE_COMMON_BRANCH:-unknown}
template_common_commit=$(git -C "$KERNEL_ROOT/common" rev-parse HEAD)
ksu_variant=$(normalize_variant "$KSU_VARIANT")
ksu_track=$(normalize_track "$KSU_TRACK")
ksu_ref=$(resolve_ksu_ref)
ksu_commit=$ksu_commit
revision=$REVISION
enable_susfs=$ENABLE_SUSFS
use_zram=$USE_ZRAM
use_bbg=$USE_BBG
use_ddk=$USE_DDK
use_ntsync=$USE_NTSYNC
use_networking=$USE_NETWORKING
use_kpm=$USE_KPM
use_rekernel=$USE_REKERNEL
virtualization_support=$VIRTUALIZATION_SUPPORT
build_timestamp=$build_timestamp
EOF
}

package_anykernel() {
    local src_dir
    local stage_dir="$WORKSPACE_DIR/staging/AnyKernel3"
    local zip_name="${ANDROID_VERSION}-${KERNEL_VERSION}.${SUB_LEVEL}-${OS_PATCH_LEVEL}-AnyKernel3.zip"

    src_dir="$(kernel_dist_dir)"
    require_file "$src_dir/Image"
    rm -rf "$stage_dir"
    mkdir -p "$stage_dir"
    rsync -a --delete --exclude='.git' "$ANYKERNEL3_SOURCE"/ "$stage_dir"/
    cp "$src_dir/Image" "$stage_dir/Image"
    (
        cd "$stage_dir"
        zip -qr "$ARTIFACTS_DIR/$zip_name" ./*
    )
}

package_boot_images() {
    local src_dir
    local stage_dir="$WORKSPACE_DIR/staging/bootimgs"
    local prefix="${ANDROID_VERSION}-${KERNEL_VERSION}.${SUB_LEVEL}-${OS_PATCH_LEVEL}"

    src_dir="$(kernel_dist_dir)"
    require_file "$src_dir/Image"
    require_file "$src_dir/Image.lz4"
    rm -rf "$stage_dir"
    mkdir -p "$stage_dir"
    cp "$src_dir/Image" "$stage_dir/"
    cp "$src_dir/Image.lz4" "$stage_dir/"
    gzip -n -k -f -9 "$stage_dir/Image"

    (
        cd "$stage_dir"
        "$MKBOOTIMG" --header_version 4 --kernel Image --output boot.img
        "$AVBTOOL" add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot.img --algorithm SHA256_RSA2048 --key "$BOOT_SIGN_KEY_PATH"
        cp boot.img "$ARTIFACTS_DIR/${prefix}-boot.img"

        "$MKBOOTIMG" --header_version 4 --kernel Image.gz --output boot-gz.img
        "$AVBTOOL" add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot-gz.img --algorithm SHA256_RSA2048 --key "$BOOT_SIGN_KEY_PATH"
        cp boot-gz.img "$ARTIFACTS_DIR/${prefix}-boot-gz.img"

        "$MKBOOTIMG" --header_version 4 --kernel Image.lz4 --output boot-lz4.img
        "$AVBTOOL" add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot-lz4.img --algorithm SHA256_RSA2048 --key "$BOOT_SIGN_KEY_PATH"
        cp boot-lz4.img "$ARTIFACTS_DIR/${prefix}-boot-lz4.img"
    )
}

print_loaded_env() {
    cat <<EOF
ROOT_DIR=$ROOT_DIR
TEMPLATE_ROOT=$TEMPLATE_ROOT
TEMPLATE_NAME=$TEMPLATE_NAME
KERNEL_ROOT=$KERNEL_ROOT
ANDROID_VERSION=$ANDROID_VERSION
KERNEL_VERSION=$KERNEL_VERSION
SUB_LEVEL=$SUB_LEVEL
OS_PATCH_LEVEL=$OS_PATCH_LEVEL
REVISION=$REVISION
TEMPLATE_BRANCH=$TEMPLATE_BRANCH
KSU_VARIANT=$KSU_VARIANT
KSU_TRACK=$KSU_TRACK
ENABLE_SUSFS=$ENABLE_SUSFS
USE_ZRAM=$USE_ZRAM
USE_BBG=$USE_BBG
USE_DDK=$USE_DDK
USE_NTSYNC=$USE_NTSYNC
USE_NETWORKING=$USE_NETWORKING
USE_KPM=$USE_KPM
USE_REKERNEL=$USE_REKERNEL
VIRTUALIZATION_SUPPORT=$VIRTUALIZATION_SUPPORT
USE_CUSTOM_EXTERNAL_MODULES=$USE_CUSTOM_EXTERNAL_MODULES
CUSTOM_EXTERNAL_MODULES=$CUSTOM_EXTERNAL_MODULES
ABK_MANAGER_PACKAGE=$ABK_MANAGER_PACKAGE
ABK_MANAGER_CERT_SIZE=$ABK_MANAGER_CERT_SIZE
EOF
}

main() {
    parse_args "$@"
    source_env
    if (( PRINT_ENV == 1 )); then
        print_loaded_env
        exit 0
    fi

    setup_logging
    assert_supported_template
    ensure_layout

    if (( RESEED == 1 )); then
        if [[ -d "$KERNEL_ROOT/.repo" ]]; then
            reset_workspace_state
        else
            seed_workspace
        fi
        ensure_layout
    fi

    if (( CLEAN_OUT == 1 )); then
        clean_outputs
    fi

    check_profile_compatibility
    if [[ ! -f "$STATE_DATA_DIR/profile.env" || ! -f "$DEFCONFIG.orig" || ! -s "$KERNEL_ROOT/common/arch/arm64/configs/ksu.fragment" || $RESEED -eq 1 ]]; then
        log_info "realizing profile into workspace"
        prepare_profile
        finalize_profile
        run_custom_external_modules before_build
        finalize_sukisu_resukisu_susfs_compat
        store_profile_snapshot
    fi

    prepare_defconfig_fragment
    run_kernel_build
    apply_kpm_patch
    mkdir -p "$ARTIFACTS_DIR"
    write_metadata

    if (( NO_PACKAGE == 0 )); then
        package_anykernel
        package_boot_images
    fi

    log_info "build completed"
    log_info "artifacts: $ARTIFACTS_DIR"
}

main "$@"
