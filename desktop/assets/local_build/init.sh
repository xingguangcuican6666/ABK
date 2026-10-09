#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_INSTANCE_ID="${ABK_LOCAL_BUILD_SOURCE_INSTANCE_ID:-}"
STATE_DIR="${ABK_LOCAL_BUILD_STATE_DIR:-$ROOT_DIR/.local-build}"
SOURCES_DIR="${ABK_LOCAL_BUILD_SOURCES_DIR:-$STATE_DIR/sources}"
WORKSPACE_DIR="${ABK_LOCAL_BUILD_WORKSPACE_DIR:-$STATE_DIR/workspace}"
ARTIFACTS_DIR="$WORKSPACE_DIR/artifacts"
LOGS_DIR="$WORKSPACE_DIR/logs"
CACHE_DIR="$WORKSPACE_DIR/cache"
KEYS_DIR="$WORKSPACE_DIR/keys"
STATE_DATA_DIR="$WORKSPACE_DIR/state"
ENV_FILE="$STATE_DIR/env.sh"
KERNEL_ROOT="$WORKSPACE_DIR/kernel"

ABK_SOURCE_LOCAL="$(realpath "${ABK_LOCAL_BUILD_ABK_SOURCE_DIR:-$ROOT_DIR/../abk}")"
ABK_SOURCE="$ABK_SOURCE_LOCAL"
ANYKERNEL3_SOURCE="$SOURCES_DIR/AnyKernel3"
KERNEL_PATCHES_SOURCE="$SOURCES_DIR/kernel_patches"
SUKISU_PATCHES_SOURCE="$SOURCES_DIR/SukiSU_patch"
ACTION_BUILD_SOURCE="$SOURCES_DIR/Action-Build"
SUSFS_SOURCE="$SOURCES_DIR/susfs4ksu"
GCC_SOURCE="$SOURCES_DIR/gcc"
VIRTUALIZATION_SOURCE="$SOURCES_DIR/Droidspaces-OSS"

ANYKERNEL3_REPO_URL="https://github.com/WildKernels/AnyKernel3.git"
KERNEL_PATCHES_REPO_URL="https://github.com/WildKernels/kernel_patches.git"
SUKISU_PATCHES_REPO_URL="https://github.com/ShirkNeko/SukiSU_patch.git"
ACTION_BUILD_REPO_URL="https://github.com/Numbersf/Action-Build.git"
SUSFS_REPO_URL="https://gitlab.com/simonpunk/susfs4ksu.git"
GCC_REPO_URL="https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1.git"
VIRTUALIZATION_REPO_URL="https://github.com/ravindu644/Droidspaces-OSS.git"

FORCE=0
SKIP_DEPS=0
SELECTED_ANDROID_VERSION=""
SELECTED_KERNEL_VERSION=""
SELECTED_BRANCH_MONTH=""
TEMPLATE_ROOT="${ABK_LOCAL_BUILD_TEMPLATE_ROOT:-}"

usage() {
    cat <<'EOF'
Usage: ./init.sh --android <android14|android15|...> --kernel <6.1|6.6|6.12> --branch-month <YYYY-MM> [--force] [--skip-deps]

  --android       Android ACK line, for example: android14, android15.
  --kernel        Kernel version line, for example: 6.1, 6.6, 6.12.
  --branch-month  Patch month appended to the ACK branch, for example: 2025-05.
  --force         Recreate .local-build workspace from scratch.
  --skip-deps     Do not clone or update dependency repositories.
EOF
}

log_info() {
    printf '[init] %s\n' "$*"
}

log_warn() {
    printf '[init][warn] %s\n' "$*" >&2
}

log_error() {
    printf '[init][error] %s\n' "$*" >&2
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

clone_or_update() {
    local url="$1"
    local dest="$2"
    local branch="${3:-}"

    mkdir -p "$(dirname "$dest")"

    if [[ ! -d "$dest/.git" ]]; then
        rm -rf "$dest"
        if [[ -n "$branch" ]]; then
            git clone --depth 1 -b "$branch" "$url" "$dest"
        else
            git clone --depth 1 "$url" "$dest"
        fi
        return 0
    fi

    git -C "$dest" remote set-url origin "$url"
    if [[ -n "$branch" ]]; then
        git -C "$dest" fetch --depth 1 origin "$branch"
        git -C "$dest" checkout -B "$branch" FETCH_HEAD
    else
        git -C "$dest" fetch --depth 1 origin
        git -C "$dest" reset --hard FETCH_HEAD
    fi
}

parse_args() {
    while (($# > 0)); do
        case "$1" in
            --android)
                shift
                (($# > 0)) || log_error "--android requires a value"
                SELECTED_ANDROID_VERSION="$1"
                ;;
            --kernel)
                shift
                (($# > 0)) || log_error "--kernel requires a value"
                SELECTED_KERNEL_VERSION="$1"
                ;;
            --branch-month)
                shift
                (($# > 0)) || log_error "--branch-month requires a value"
                SELECTED_BRANCH_MONTH="$1"
                ;;
            --force)
                FORCE=1
                ;;
            --skip-deps)
                SKIP_DEPS=1
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

validate_selection() {
    [[ -n "$SELECTED_ANDROID_VERSION" ]] || log_error "--android is required"
    [[ -n "$SELECTED_KERNEL_VERSION" ]] || log_error "--kernel is required"
    [[ -n "$SELECTED_BRANCH_MONTH" ]] || log_error "--branch-month is required"

    [[ "$SELECTED_ANDROID_VERSION" =~ ^android[0-9]+$ ]] || \
        log_error "unsupported --android value: $SELECTED_ANDROID_VERSION"
    [[ "$SELECTED_KERNEL_VERSION" =~ ^[0-9]+\.[0-9]+$ ]] || \
        log_error "unsupported --kernel value: $SELECTED_KERNEL_VERSION"
    [[ "$SELECTED_BRANCH_MONTH" =~ ^[0-9]{4}-[0-9]{2}$ ]] || \
        log_error "unsupported --branch-month value: $SELECTED_BRANCH_MONTH (expected YYYY-MM)"
}

resolve_template_root() {
    local android_suffix candidate

    case "${1}:${2}" in
        android14:6.1) printf '%s\n' "$ROOT_DIR/AOSP_Kernel_A14_6.1" ;;
        android15:6.6) printf '%s\n' "$ROOT_DIR/AOSP_Kernel_A15_6.6" ;;
        *)
            android_suffix="${1#android}"
            candidate="$ROOT_DIR/AOSP_Kernel_A${android_suffix}_${2}"
            printf '%s\n' "$candidate"
            ;;
    esac
}

manifest_branch_for() {
    printf 'common-%s-%s-%s\n' "$1" "$2" "$3"
}

susfs_branch_for() {
    printf 'gki-%s-%s\n' "$1" "$2"
}

read_manifest_common_revision() {
    local manifest="$1"
    sed -n 's#.*path="common".*revision="\([^"]*\)".*#\1#p' "$manifest" | head -n 1
}

read_template_patch_level() {
    local revision
    revision="$(read_manifest_common_revision "$TEMPLATE_ROOT/.repo/manifests/default.xml")"
    [[ "$revision" =~ ([0-9]{4}-[0-9]{2})$ ]] || \
        log_error "failed to derive template patch level from $TEMPLATE_ROOT/.repo/manifests/default.xml"
    printf '%s\n' "${BASH_REMATCH[1]}"
}

read_template_sublevel() {
    local value
    value="$(awk '/^SUBLEVEL = / {print $3}' "$TEMPLATE_ROOT/common/Makefile")"
    [[ -n "$value" ]] || log_error "failed to derive template sublevel from $TEMPLATE_ROOT/common/Makefile"
    printf '%s\n' "$value"
}

clean_bazel_artifacts() {
    local root="$1"

    rm -rf "$root/out" \
           "$root/bazel-bin" \
           "$root/bazel-out" \
           "$root/bazel-testlogs"

    find "$root" -mindepth 1 -maxdepth 1 -type d -name 'bazel-*' -exec rm -rf {} + 2>/dev/null || true
}

git_remote_branch_exists() {
    local repo_dir="$1"
    local remote_name="$2"
    local branch_name="$3"
    local remote_url

    if git -C "$repo_dir" show-ref --verify --quiet "refs/remotes/$remote_name/$branch_name"; then
        return 0
    fi

    remote_url="$(git -C "$repo_dir" remote get-url "$remote_name" 2>/dev/null || true)"
    [[ -n "$remote_url" ]] || return 1
    git ls-remote "$remote_url" "refs/heads/$branch_name" 2>/dev/null | grep -Fq "refs/heads/$branch_name"
}

resolve_common_branch() {
    local branch_base="$1-$2-$3"
    local common_remote_url="https://android.googlesource.com/kernel/common"

    if [[ -d "$TEMPLATE_ROOT/common/.git" ]] && git_remote_branch_exists "$TEMPLATE_ROOT/common" "aosp" "$branch_base"; then
        printf '%s\n' "$branch_base"
        return 0
    fi

    if [[ -d "$TEMPLATE_ROOT/common/.git" ]] && git_remote_branch_exists "$TEMPLATE_ROOT/common" "aosp" "deprecated/$branch_base"; then
        printf 'deprecated/%s\n' "$branch_base"
        return 0
    fi

    if git ls-remote "$common_remote_url" "refs/heads/$branch_base" 2>/dev/null | grep -Fq "refs/heads/$branch_base"; then
        printf '%s\n' "$branch_base"
        return 0
    fi

    if git ls-remote "$common_remote_url" "refs/heads/deprecated/$branch_base" 2>/dev/null | grep -Fq "refs/heads/deprecated/$branch_base"; then
        printf 'deprecated/%s\n' "$branch_base"
        return 0
    fi

    log_error "no kernel/common branch matched $branch_base on $common_remote_url"
}

ensure_repo_launcher() {
    local repo_tool="$1"

    if [[ -x "$repo_tool" ]]; then
        return 0
    fi

    mkdir -p "$(dirname "$repo_tool")"
    log_info "downloading repo launcher to $repo_tool"
    curl -fsSL "https://storage.googleapis.com/git-repo-downloads/repo" -o "$repo_tool"
    chmod 0755 "$repo_tool"
}

bootstrap_template_checkout() {
    local manifest_branch="$1"
    local repo_tool="$TEMPLATE_ROOT/.repo/repo/repo"
    local bootstrap_repo_tool="$STATE_DIR/bin/repo"

    if [[ -d "$TEMPLATE_ROOT/.repo/manifests" && -f "$repo_tool" ]]; then
        return 0
    fi

    log_info "bootstrapping template checkout in $(basename "$TEMPLATE_ROOT") from Google source"
    mkdir -p "$TEMPLATE_ROOT"
    ensure_repo_launcher "$bootstrap_repo_tool"
    if [[ ! -d "$TEMPLATE_ROOT/.repo/manifests" ]]; then
        rm -rf "$TEMPLATE_ROOT/.repo/repo" "$TEMPLATE_ROOT/.repo/repo.tmp"
    fi
    (
        cd "$TEMPLATE_ROOT"
        "$bootstrap_repo_tool" init --depth=1 \
            -u "https://android.googlesource.com/kernel/manifest" \
            -b "$manifest_branch" \
            --repo-rev=v2.16 \
            --quiet
    )
}

repair_manifest_checkout() {
    local manifest_repo="$1"
    local manifest_branch="$2"
    local needs_repair=0

    if ! git -C "$manifest_repo" rev-parse --verify HEAD >/dev/null 2>&1; then
        needs_repair=1
    fi

    if [[ -n "$(git -C "$manifest_repo" status --porcelain 2>/dev/null || true)" ]]; then
        needs_repair=1
    fi

    if (( needs_repair == 0 )); then
        return 0
    fi

    log_warn "repairing template manifest checkout in $manifest_repo"
    git -C "$manifest_repo" fetch --depth 1 origin "$manifest_branch"
    git -C "$manifest_repo" checkout -B default -f "refs/remotes/origin/$manifest_branch"
    git -C "$manifest_repo" reset --hard "refs/remotes/origin/$manifest_branch"
    git -C "$manifest_repo" clean -fd
}

rewrite_template_manifest() {
    local manifest_file="$1"
    local manifest_branch="$2"
    local common_branch="$3"

    python3 - "$manifest_file" "$manifest_branch" "$common_branch" <<'PY'
import sys
import xml.etree.ElementTree as ET

manifest_file, manifest_branch, common_branch = sys.argv[1:]
tree = ET.parse(manifest_file)
root = tree.getroot()

found_superproject = False
found_common = False

for superproject in root.findall("superproject"):
    if superproject.get("name") == "kernel/superproject":
        superproject.set("revision", manifest_branch)
        found_superproject = True

for project in root.findall("project"):
    if project.get("name") == "kernel/superproject":
        project.set("revision", manifest_branch)
        found_superproject = True
    if project.get("name") == "kernel/common" and project.get("path") == "common":
        project.set("revision", common_branch)
        project.set("upstream", common_branch)
        project.set("dest-branch", common_branch)
        found_common = True

if not found_superproject:
    raise SystemExit(f"missing kernel/superproject entry in {manifest_file}")
if not found_common:
    raise SystemExit(f"missing kernel/common entry in {manifest_file}")

tree.write(manifest_file, encoding="UTF-8", xml_declaration=True)
PY
}

sync_template_branch() {
    local repo_tool manifest_repo manifest_url manifest_branch common_branch actual_common_branch

    manifest_branch="$(manifest_branch_for "$SELECTED_ANDROID_VERSION" "$SELECTED_KERNEL_VERSION" "$SELECTED_BRANCH_MONTH")"
    bootstrap_template_checkout "$manifest_branch"
    repo_tool="$TEMPLATE_ROOT/.repo/repo/repo"
    manifest_repo="$TEMPLATE_ROOT/.repo/manifests"

    require_file "$repo_tool"
    require_dir "$manifest_repo"

    manifest_url="$(git -C "$manifest_repo" remote get-url origin 2>/dev/null || true)"
    [[ -n "$manifest_url" ]] || manifest_url="https://android.googlesource.com/kernel/manifest"

    common_branch="$(resolve_common_branch "$SELECTED_ANDROID_VERSION" "$SELECTED_KERNEL_VERSION" "$SELECTED_BRANCH_MONTH")"
    repair_manifest_checkout "$manifest_repo" "$manifest_branch"

    log_info "syncing template $(basename "$TEMPLATE_ROOT") to $manifest_branch"
    (
        cd "$TEMPLATE_ROOT"
        "$repo_tool" init -u "$manifest_url" -b "$manifest_branch" -m default.xml --quiet
        rewrite_template_manifest "$TEMPLATE_ROOT/.repo/manifests/default.xml" "$manifest_branch" "$common_branch"
        "$repo_tool" sync -c -j4 --force-sync --no-clone-bundle --no-tags
    )

    actual_common_branch="$(read_manifest_common_revision "$TEMPLATE_ROOT/.repo/manifests/default.xml")"
    [[ "$actual_common_branch" == "$common_branch" ]] || \
        log_error "template manifest mismatch after sync: expected common revision $common_branch, got $actual_common_branch"
}

seed_workspace() {
    log_info "seeding workspace from local template $(basename "$TEMPLATE_ROOT")"

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

    if [[ -d "$GCC_SOURCE" ]]; then
        rm -rf "$KERNEL_ROOT/gcc"
        rsync -a --delete "$GCC_SOURCE"/ "$KERNEL_ROOT/gcc"/
    fi

    mkdir -p "$ARTIFACTS_DIR" "$LOGS_DIR" "$CACHE_DIR" "$KEYS_DIR" "$STATE_DATA_DIR"
}

write_env_file() {
    local template_sublevel="$1"
    local template_patch_level="$2"
    local template_branch template_common_branch boot_sign_key_path

    template_branch="$(manifest_branch_for "$SELECTED_ANDROID_VERSION" "$SELECTED_KERNEL_VERSION" "$template_patch_level")"
    template_common_branch="$(read_manifest_common_revision "$TEMPLATE_ROOT/.repo/manifests/default.xml")"
    boot_sign_key_path="$KEYS_DIR/boot_sign_key.pem"

    cat >"$ENV_FILE" <<EOF
#!/usr/bin/env bash
# Generated by init.sh. Edit only the user-facing variables in the "Build knobs" section.

export ROOT_DIR="$ROOT_DIR"
export SOURCE_INSTANCE_ID="$SOURCE_INSTANCE_ID"
export STATE_DIR="$STATE_DIR"
export SOURCES_DIR="$SOURCES_DIR"
export WORKSPACE_DIR="$WORKSPACE_DIR"
export ARTIFACTS_DIR="$ARTIFACTS_DIR"
export LOGS_DIR="$LOGS_DIR"
export CACHE_DIR="$CACHE_DIR"
export KEYS_DIR="$KEYS_DIR"
export STATE_DATA_DIR="$STATE_DATA_DIR"

export TEMPLATE_ROOT="$TEMPLATE_ROOT"
export TEMPLATE_NAME="$(basename "$TEMPLATE_ROOT")"
export KERNEL_ROOT="$KERNEL_ROOT"
export DEFCONFIG="\$KERNEL_ROOT/common/arch/arm64/configs/gki_defconfig"

export ABK_SOURCE="$ABK_SOURCE"
export ANYKERNEL3_SOURCE="$ANYKERNEL3_SOURCE"
export KERNEL_PATCHES_SOURCE="$KERNEL_PATCHES_SOURCE"
export SUKISU_PATCHES_SOURCE="$SUKISU_PATCHES_SOURCE"
export ACTION_BUILD_SOURCE="$ACTION_BUILD_SOURCE"
export SUSFS_SOURCE="$SUSFS_SOURCE"
export GCC_SOURCE="$GCC_SOURCE"
export VIRTUALIZATION_SOURCE="$VIRTUALIZATION_SOURCE"
export VIRTUALIZATION_SUPPORT_PATCHES="\$VIRTUALIZATION_SOURCE/Documentation/resources/kernel-patches/GKI"

export AVBTOOL="\$KERNEL_ROOT/prebuilts/kernel-build-tools/linux-x86/bin/avbtool"
export MKBOOTIMG="\$KERNEL_ROOT/tools/mkbootimg/mkbootimg.py"
export UNPACK_BOOTIMG="\$KERNEL_ROOT/tools/mkbootimg/unpack_bootimg.py"
export BOOT_SIGN_KEY_PATH="$boot_sign_key_path"

export CCACHE_DIR="\$CACHE_DIR/ccache"
export BAZEL_DISK_CACHE="\$CACHE_DIR/bazel-disk"
export CUSTOM_EXTERNAL_MODULES_MANIFEST="\$STATE_DATA_DIR/custom_external_modules.tsv"
export CUSTOM_EXTERNAL_MODULES_ROOT="\$WORKSPACE_DIR/custom_modules"

export TEMPLATE_ANDROID_VERSION="$SELECTED_ANDROID_VERSION"
export TEMPLATE_KERNEL_VERSION="$SELECTED_KERNEL_VERSION"
export TEMPLATE_SUB_LEVEL="$template_sublevel"
export TEMPLATE_OS_PATCH_LEVEL="$template_patch_level"
export TEMPLATE_BRANCH="$template_branch"
export TEMPLATE_COMMON_BRANCH="$template_common_branch"

# Target selection (set by init.sh; re-run init.sh to change)
export ANDROID_VERSION="$SELECTED_ANDROID_VERSION"
export KERNEL_VERSION="$SELECTED_KERNEL_VERSION"
export SUB_LEVEL="$template_sublevel"
export OS_PATCH_LEVEL="$template_patch_level"
export REVISION="r1"

# Build knobs
export KSU_VARIANT="ReSukiSU"
export KSU_TRACK="Dev"
export KSU_CUSTOM_REF=""

export ENABLE_SUSFS="false"
export USE_ZRAM="false"
export ZRAM_FULL_ALGO="false"
export ZRAM_EXTRA_ALGOS=""
export USE_BBG="false"
export USE_DDK="false"
export USE_NTSYNC="false"
export USE_NETWORKING="false"
export USE_KPM="false"
export KPM_PASSWORD=""
export USE_REKERNEL="false"
export SUPP_OP="false"
export USE_CUSTOM_EXTERNAL_MODULES="false"
# Supports legacy repo;stage, plus module:repo;stage and set:repo#child;stage
export CUSTOM_EXTERNAL_MODULES=""
export VIRTUALIZATION_SUPPORT="off"

export VERSION_INPUT=""
export BUILD_TIME=""

# ABK manager certificate metadata used by ABK_control_module and similar hooks.
export ABK_MANAGER_CERT_ENV_FILE="\$ABK_SOURCE/app/signing/abk-manager-cert.env"
# Optional overrides; leave empty to read defaults from ABK_MANAGER_CERT_ENV_FILE.
export ABK_MANAGER_PACKAGE=""
export ABK_MANAGER_CERT_SIZE=""
export ABK_MANAGER_CERT_SHA256=""
EOF

    chmod 0644 "$ENV_FILE"
}

prepare_key() {
    local key_path="$KEYS_DIR/boot_sign_key.pem"
    if [[ ! -f "$key_path" ]]; then
        log_info "generating boot signing key"
        openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 >"$key_path"
    fi
}

prepare_dependencies() {
    local susfs_branch

    log_info "syncing clean dependency sources"
    require_dir "$ABK_SOURCE"
    require_dir "$ABK_SOURCE"
    require_file "$ABK_SOURCE/cli/abk.py"
    require_file "$ABK_SOURCE/hmbird_patch.c"
    require_file "$ABK_SOURCE/zram/apply_lz4_neon.sh"
    require_file "$ABK_SOURCE/ddk/setup.sh"

    susfs_branch="$(susfs_branch_for "$SELECTED_ANDROID_VERSION" "$SELECTED_KERNEL_VERSION")"
    clone_or_update "$ANYKERNEL3_REPO_URL" "$ANYKERNEL3_SOURCE" "gki-2.0"
    clone_or_update "$KERNEL_PATCHES_REPO_URL" "$KERNEL_PATCHES_SOURCE"
    clone_or_update "$SUKISU_PATCHES_REPO_URL" "$SUKISU_PATCHES_SOURCE"
    clone_or_update "$ACTION_BUILD_REPO_URL" "$ACTION_BUILD_SOURCE"
    clone_or_update "$SUSFS_REPO_URL" "$SUSFS_SOURCE" "$susfs_branch"
    clone_or_update "$GCC_REPO_URL" "$GCC_SOURCE"
    clone_or_update "$VIRTUALIZATION_REPO_URL" "$VIRTUALIZATION_SOURCE"
}

resolve_template_selection() {
    validate_selection
    if [[ -z "$TEMPLATE_ROOT" ]]; then
        TEMPLATE_ROOT="$(resolve_template_root "$SELECTED_ANDROID_VERSION" "$SELECTED_KERNEL_VERSION")"
    fi
}

main() {
    local template_sublevel template_patch_level manifest_branch

    parse_args "$@"
    resolve_template_selection
    manifest_branch="$(manifest_branch_for "$SELECTED_ANDROID_VERSION" "$SELECTED_KERNEL_VERSION" "$SELECTED_BRANCH_MONTH")"
    bootstrap_template_checkout "$manifest_branch"

    require_dir "$TEMPLATE_ROOT"
    require_dir "$TEMPLATE_ROOT/.repo"

    if [[ -e "$ENV_FILE" || -d "$KERNEL_ROOT" ]]; then
        if (( FORCE == 0 )); then
            log_error "environment already exists. Re-run with --force to recreate it."
        fi
        log_warn "recreating existing local build environment"
        rm -rf "$STATE_DIR"
    fi

    mkdir -p "$SOURCES_DIR" "$WORKSPACE_DIR" "$ARTIFACTS_DIR" "$LOGS_DIR" "$CACHE_DIR" "$KEYS_DIR" "$STATE_DATA_DIR"

    sync_template_branch

    require_dir "$TEMPLATE_ROOT/common"
    require_file "$TEMPLATE_ROOT/common/arch/arm64/configs/gki_defconfig"
    require_file "$TEMPLATE_ROOT/build/kernel/kleaf/impl/stamp.bzl"
    require_file "$TEMPLATE_ROOT/prebuilts/kernel-build-tools/linux-x86/bin/avbtool"
    require_file "$TEMPLATE_ROOT/tools/mkbootimg/mkbootimg.py"

    if (( SKIP_DEPS == 0 )); then
        prepare_dependencies
    else
        require_dir "$ABK_SOURCE"
        require_dir "$ANYKERNEL3_SOURCE"
        require_dir "$KERNEL_PATCHES_SOURCE"
        require_dir "$SUKISU_PATCHES_SOURCE"
        require_dir "$ACTION_BUILD_SOURCE"
        require_dir "$SUSFS_SOURCE"
        require_dir "$GCC_SOURCE"
        require_dir "$VIRTUALIZATION_SOURCE"
    fi

    seed_workspace

    template_sublevel="$(read_template_sublevel)"
    template_patch_level="$(read_template_patch_level)"

    prepare_key
    write_env_file "$template_sublevel" "$template_patch_level"

    log_info "environment initialized"
    if [[ -n "$SOURCE_INSTANCE_ID" ]]; then
        log_info "source instance: $SOURCE_INSTANCE_ID"
    fi
    log_info "template: $(basename "$TEMPLATE_ROOT")"
    log_info "branch: $(manifest_branch_for "$SELECTED_ANDROID_VERSION" "$SELECTED_KERNEL_VERSION" "$SELECTED_BRANCH_MONTH")"
    log_info "edit $ENV_FILE if you need to change build knobs"
    log_info "then run ./rebuild.sh"
}

main "$@"
