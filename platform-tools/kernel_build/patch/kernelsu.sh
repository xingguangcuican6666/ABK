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
      "Official")
        echo "添加 KernelSU 官方版..."
        # KernelSU ref 由 .github/scripts/resolve-ksu-ref.sh 按发布层
        # (Stable/Dev/Latest/Custom) 统一解析并写入 $BRANCH，这里直接使用。
        curl -LSs "https://raw.githubusercontent.com/tiann/KernelSU/main/kernel/setup.sh" | bash -s "$BRANCH"

        cd KernelSU
        KSU_GIT_VERSION=$(git rev-list --count HEAD)
        KSU_VERSION=$((30000 + KSU_GIT_VERSION))
        echo "KSU_VERSION=$KSU_VERSION" >> $GITHUB_ENV

        if [ -f "kernel/Kbuild" ]; then
          sed -i "s/DKSU_VERSION=16/DKSU_VERSION=${KSU_VERSION}/" kernel/Kbuild
        fi
        cd ..
        ;;
      "SukiSU")
        echo "添加 SukiSU..."
        requested_ref="$BRANCH"
        if [ -z "$requested_ref" ]; then
          echo "::error::SukiSU 请求 ref 为空"
          exit 1
        fi

        setup_script="$(mktemp)"
        curl -fLSs "https://raw.githubusercontent.com/SukiSU-Ultra/SukiSU-Ultra/main/kernel/setup.sh" -o "$setup_script"
        if grep -qF 'git checkout "$1" && echo "[-] Checked out $1." || echo "[-] Checkout default branch"' "$setup_script"; then
          sed -i '/git checkout "\$1" && echo /c\        git checkout "$1" && echo "[-] Checked out $1."' "$setup_script"
        fi
        bash "$setup_script" "$requested_ref"
        rm -f "$setup_script"

        if [ ! -d KernelSU/.git ]; then
          echo "::error::SukiSU setup 完成后未找到 KernelSU Git 仓库"
          exit 1
        fi
        requested_head="$(git -C KernelSU rev-parse "${requested_ref}^{commit}" 2>/dev/null)" || {
          echo "::error::SukiSU 无法解析请求 ref: $requested_ref"
          exit 1
        }
        resolved_head="$(git -C KernelSU rev-parse HEAD)"
        echo "SukiSU 请求 ref: $requested_ref"
        echo "SukiSU 请求 HEAD: $requested_head"
        echo "SukiSU 最终 HEAD: $resolved_head"
        if [ "$requested_head" != "$resolved_head" ]; then
          echo "::error::SukiSU setup 静默回退或检出错误: 请求 $requested_head，实际 $resolved_head"
          exit 1
        fi
        ;;
      "BakaSU")
        echo "添加 BakaSU..."
        curl -LSs "https://raw.githubusercontent.com/Baka-SU/BakaSU/main/kernel/setup.sh" | bash -s "$BRANCH"
        ;;
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
    esac

    if [ -d "KernelSU/.git" ]; then
      KSU_GIT_VERSION=$(git -C KernelSU rev-list --count HEAD)
      KSU_VERSION_BASE=30000
      if [ "${ABK_INPUT_KSU_VARIANT}" = "BakaSU" ]; then
        KSU_VERSION_BASE=30700
      elif [ "${ABK_INPUT_KSU_VARIANT}" = "SukiSU" ]; then
        KSU_VERSION_BASE=40000
      fi
      KSU_VERSION=$((KSU_VERSION_BASE + KSU_GIT_VERSION))
      printf 'KSU_VERSION=%s\n' "$KSU_VERSION" >> "${GITHUB_ENV:-/dev/null}"
      KSU_LATEST_COMMIT_DATE=$(git -C KernelSU log -1 --date=format:'%Y-%m-%d %H:%M:%S %z' --format='%cd')
      echo "KSU_LATEST_COMMIT_DATE=$KSU_LATEST_COMMIT_DATE" >> $GITHUB_ENV
    else
      echo "KSU_LATEST_COMMIT_DATE=未知" >> $GITHUB_ENV
    fi

}

# ---- 确保 KernelSU SUSFS ABI 兼容 ----
abi() {
    cd "$KERNEL_ROOT"
    python3 "$GITHUB_WORKSPACE/.github/scripts/ensure-ksu-compat.py" "$KERNEL_ROOT"

}

# ---- 配置 SukiSU 管理器信息 ----
manager-info() {
    cd "$KERNEL_ROOT/KernelSU"
    KBUILD_FILE="./kernel/Kbuild"
    CUSTOM_TAG="${MANAGER_STR}"

    if [ ! -f "$KBUILD_FILE" ]; then
      echo "未找到 $KBUILD_FILE，跳过 SukiSU 版本标识定制"
      exit 0
    fi

    GIT_HASH=$(git rev-parse --short=8 HEAD)
    BRANCH_NAME="${BRANCH#-s }"
    if [ -z "$BRANCH_NAME" ] || [ "$BRANCH_NAME" = "$BRANCH" ]; then
      BRANCH_NAME=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main")
    fi

    if [ -n "$CUSTOM_TAG" ]; then
      VERSION_TEMPLATE="v\$1-$CUSTOM_TAG@$BRANCH_NAME[$GIT_HASH]"
    else
      VERSION_TEMPLATE="v\$1-$GIT_HASH@$BRANCH_NAME"
    fi

    awk -v body="$VERSION_TEMPLATE" '
      BEGIN {
        in_block = 0
        replaced = 0
      }
      /^[[:space:]]*define get_ksu_version_full$/ {
        print
        print body
        in_block = 1
        replaced = 1
        next
      }
      in_block && /^[[:space:]]*endef$/ {
        print
        in_block = 0
        next
      }
      !in_block {
        print
      }
      END {
        if (!replaced) {
          exit 1
        }
      }
    ' "$KBUILD_FILE" > "${KBUILD_FILE}.tmp" && mv "${KBUILD_FILE}.tmp" "$KBUILD_FILE"

    echo "已更新 get_ksu_version_full 模板: $VERSION_TEMPLATE"

}

case "${1:-}" in
    resolve-ref) resolve-ref ;;
    install) install ;;
    abi) abi ;;
    manager-info) manager-info ;;
    *)
        echo "用法: $0 {resolve-ref|install|abi|manager-info}" >&2
        exit 2
        ;;
esac
