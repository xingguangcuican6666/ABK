#!/usr/bin/env bash
# 干净 GKI 内核：环境、校验、工具链、源码同步、编译与打包。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/clean/source.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 校验类 LineageOS 源码构建参数 ----
validate-custom() {
    set -euo pipefail
    access=public
    [ "$SOURCE_PRIVATE" = "true" ] && access=github_private
    python3 "$GITHUB_WORKSPACE/.github/scripts/custom-source.py" normalize-url \
      --source-url "$SOURCE_REPO" --access "$access" >/dev/null
    [ -n "$SOURCE_REF" ] || { echo "::error::源码 ref 不能为空"; exit 1; }
    [[ "$SOURCE_COMMIT" =~ ^[0-9a-fA-F]{40}$ ]] || { echo "::error::源码 commit 必须是完整 SHA"; exit 1; }
    python3 - <<'PY'
import os, re
if not re.fullmatch(r"(?:\d{4}-(0[1-9]|1[0-2])|lts)", os.environ["SOURCE_PATCH_LEVEL"]):
    raise SystemExit("os_patch_level must use YYYY-MM or lts")
if os.environ["SOURCE_KERNEL_VERSION"] not in {"5.10", "5.15", "6.1", "6.6", "6.12", "6.18"}:
    raise SystemExit("unsupported custom source kernel line")
expected = {
    "5.10": "android12", "5.15": "android13", "6.1": "android14",
    "6.6": "android15", "6.12": "android16", "6.18": "android17",
}[os.environ["SOURCE_KERNEL_VERSION"]]
if os.environ["SOURCE_ANDROID_VERSION"] != expected:
    raise SystemExit("custom source Android/kernel mapping mismatch")
PY
    printf '%s\n' "$SOURCE_DEFCONFIGS" | grep -qx 'gki_defconfig' || {
      echo "::error::defconfig 列表必须包含 gki_defconfig"; exit 1;
    }

}

# ---- 初始化并同步内核源码 ----
sync() {
    cd "$KERNEL_ROOT"
    set -euo pipefail
    python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone \
      https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1.git gcc

    FORMATTED_BRANCH="${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}-${ABK_INPUT_OS_PATCH_LEVEL}"
    echo "初始化 repo，分支: common-${FORMATTED_BRANCH}"

    if [ "$ABK_LOCAL_CACHE_ENABLED" = "true" ]; then
      python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" sync-repo \
        --repo "$REPO" --manifest-url https://android.googlesource.com/kernel/manifest \
        --branch "common-${FORMATTED_BRANCH}" --destination "$KERNEL_ROOT" \
        --kernel-branch "$FORMATTED_BRANCH"
    else
      $REPO init --depth=1 -u https://android.googlesource.com/kernel/manifest -b common-${FORMATTED_BRANCH} --repo-rev=v2.16

      retry_git_ls_remote() {
        local repo_url="$1"
        local ref="$2"
        local attempt
        local output
        for attempt in 1 2 3; do
          if output=$(git ls-remote "$repo_url" "$ref"); then
            printf '%s\n' "$output"
            return 0
          fi
          echo "::warning::git ls-remote 失败: $repo_url $ref (attempt ${attempt}/3)" >&2
          sleep "$((attempt * 5))"
        done
        echo "::error::git ls-remote 连续失败: $repo_url $ref" >&2
        return 1
      }

      REMOTE_BRANCH=$(retry_git_ls_remote https://android.googlesource.com/kernel/common "$FORMATTED_BRANCH")
      DEFAULT_MANIFEST_PATH=.repo/manifests/default.xml

      if grep -q deprecated <<< $REMOTE_BRANCH; then
        echo "检测到已弃用的分支: $FORMATTED_BRANCH"
        sed -i "s/\"${FORMATTED_BRANCH}\"/\"deprecated\/${FORMATTED_BRANCH}\"/g" $DEFAULT_MANIFEST_PATH
      fi

      repo_sync_retry() {
        local attempt
        for attempt in 1 2 3; do
          if $REPO --trace sync -c -j$(nproc --all) --no-tags --fail-fast; then
            return 0
          fi
          echo "::warning::GKI repo sync failed (attempt ${attempt}/3); retrying"
          sleep "$((attempt * 5))"
        done
        echo "::error::GKI repo sync failed after 3 attempts"
        return 1
      }
      repo_sync_retry
      echo "REMOTE_BRANCH=$REMOTE_BRANCH" >> $GITHUB_ENV
    fi

    if [ "$SOURCE_MODE" = "custom_git" ]; then
      bash "$GITHUB_WORKSPACE/.github/scripts/checkout-custom-source.sh"
      git -C common config user.name "ABK Custom Source"
      git -C common config user.email "abk-custom-source@localhost"

      clang_constants=common/build.config.constants
      clang_common=common/build.config.common
      clang_root=prebuilts/clang/host/linux-x86
      pinned_name=""
      pinned_ref=""
      literal_ref=""
      templated_root=""
      if [ -f "$clang_constants" ]; then
        pinned_name=$(
          sed -n 's/^CLANG_VERSION=//p' "$clang_constants" | head -n1
        )
        pinned_name=${pinned_name#clang-}
      fi
      if [ -f "$clang_common" ]; then
        pinned_ref=$(
          sed -n 's/^CLANG_PREBUILT_BIN=//p' "$clang_common" | head -n1
        )
        pinned_ref=${pinned_ref%/bin}
        if [ -n "$pinned_ref" ]; then
          case "$pinned_ref" in
            *'${'*) templated_root=${pinned_ref%/clang-*} ;;
            *) literal_ref=$pinned_ref ;;
          esac
        fi
      fi
      align_clang() {
        file=$1
        target=$2
        if [ -z "$target" ] || [ -d "$target" ]; then
          return 0
        fi
        root=${target%/clang-*}
        newest=$(
          ls -d "$root"/clang-r* 2>/dev/null | sort -V | tail -n1 || true
        )
        if [ -z "$newest" ]; then
          echo "::error::源码 pin 的 $target 缺失，$root 下无可用 clang"
          exit 1
        fi
        echo "::warning::pin $target 缺失，改用 $newest"
        if [ "$file" = "$clang_constants" ]; then
          name=${newest##*/clang-}
          sed_expr="s/^CLANG_VERSION=.*/CLANG_VERSION=$name/"
          sed -i "$sed_expr" "$file"
        else
          sed_expr="s|^CLANG_PREBUILT_BIN=.*|"
          sed_expr+="CLANG_PREBUILT_BIN=$newest/bin|"
          sed -i "$sed_expr" "$file"
        fi
      }
      if [ -n "$pinned_name" ]; then
        align_clang "$clang_constants" \
          "${templated_root:-$clang_root}/clang-$pinned_name"
      fi
      if [ -n "$literal_ref" ]; then
        align_clang "$clang_common" "$literal_ref"
      fi

      python3 "$GITHUB_WORKSPACE/.github/scripts/custom-source.py" inspect \
        --source-dir common \
        --source-url "$SOURCE_REPO" \
        --source-ref "$SOURCE_REF" \
        --resolved-commit "$SOURCE_COMMIT" \
        --access "$([ "$SOURCE_PRIVATE" = "true" ] && echo github_private || echo public)" \
        --os-patch-level "$SOURCE_PATCH_LEVEL" \
        --defconfigs "$SOURCE_DEFCONFIGS" \
        --device-label "$SOURCE_DEVICE_LABEL" \
        --kernel-version-override "$KERNEL_VERSION_OVERRIDE" \
        --github-output "$GITHUB_OUTPUT"

      python3 "$GITHUB_WORKSPACE/.github/scripts/custom-source.py" merge-defconfigs \
        --source-dir common \
        --defconfigs "$SOURCE_DEFCONFIGS" \
        --output "$DEFCONFIG"

      echo "ABK_SOURCE_MODE=custom_git" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_ROOT=$KERNEL_ROOT/common" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_URL=$SOURCE_REPO" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_REF=$SOURCE_REF" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_ACCESS=$([ "$SOURCE_PRIVATE" = "true" ] && echo github_private || echo public)" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_COMMIT=$SOURCE_COMMIT" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_KERNEL_VERSION=$SOURCE_FULL_KERNEL_VERSION" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_ANDROID_VERSION=$SOURCE_ANDROID_VERSION" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_PATCH_LEVEL=$SOURCE_PATCH_LEVEL" >> "$GITHUB_ENV"
      echo "ABK_CUSTOM_SOURCE_DEVICE_LABEL=$SOURCE_DEVICE_LABEL" >> "$GITHUB_ENV"
      ABK_INPUT_DEFCONFIGS="$SOURCE_DEFCONFIGS" python3 -c \
        'import json, os; values=[x.strip() for x in os.environ["ABK_INPUT_DEFCONFIGS"].splitlines() if x.strip()]; print("ABK_CUSTOM_SOURCE_DEFCONFIGS_JSON=" + json.dumps(values, ensure_ascii=False, separators=(",", ":")))' \
        >> "$GITHUB_ENV"
    fi

}

# ---- 初始化 Custom source manifest 状态 ----
custom-baseline() {
    set -euo pipefail
    status_file="$RUNNER_TEMP/abk-custom-source-feature-status.json"
    transaction_dir="$RUNNER_TEMP/abk-custom-source-transactions"
    python3 "$GITHUB_WORKSPACE/.github/scripts/custom-source-feature-status.py" init \
      --file "$status_file" \
      --feature "kernelsu=$([ "$KSU_VARIANT" != None ] && echo true || echo false)" \
      --feature "virtualization=$([ "$VIRTUALIZATION_SUPPORT" != off ] && echo true || echo false)" \
      --feature "zram=$USE_ZRAM" \
      --feature "bbg=$USE_BBG" \
      --feature "ddk=$USE_DDK" \
      --feature "ntsync=$USE_NTSYNC" \
      --feature "networking=$USE_NETWORKING" \
      --feature "rekernel=$USE_REKERNEL"
    mkdir -p "$transaction_dir"
    echo "ABK_CUSTOM_SOURCE_FEATURE_STATUS=$status_file" >> "$GITHUB_ENV"
    echo "ABK_CUSTOM_SOURCE_TRANSACTION_DIR=$transaction_dir" >> "$GITHUB_ENV"
    echo "ABK_CUSTOM_SOURCE_FEATURE_STATUS_FILE=$status_file" >> "$GITHUB_STEP_SUMMARY"
    git -C "$KERNEL_ROOT/common" add -A
    if ! git -C "$KERNEL_ROOT/common" diff --cached --quiet; then
      git -C "$KERNEL_ROOT/common" commit -m "ABK custom source baseline" >/dev/null
    fi
    echo "ABK_CUSTOM_SOURCE_BASELINE=$(git -C "$KERNEL_ROOT/common" rev-parse HEAD)" >> "$GITHUB_ENV"

}

case "${1:-}" in
    validate-custom) validate-custom ;;
    sync) sync ;;
    custom-baseline) custom-baseline ;;
    *)
        echo "用法: $0 {validate-custom|sync|custom-baseline}" >&2
        exit 2
        ;;
esac
