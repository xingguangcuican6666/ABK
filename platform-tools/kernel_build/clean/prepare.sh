#!/usr/bin/env bash
# 干净 GKI 内核：环境、校验、工具链、源码同步、编译与打包。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/clean/prepare.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 应用 Stock Config 伪装 ----
stock-config() {
    STOCK_SRC="$GITHUB_WORKSPACE/config/stock_defconfig"
    STOCK_DST="$KERNEL_ROOT/common/arch/arm64/configs/stock_defconfig"

    if [ ! -f "$STOCK_SRC" ]; then
      echo "未检测到 $STOCK_SRC，跳过 Stock Config 伪装。"
      exit 0
    fi

    mkdir -p "$(dirname "$STOCK_DST")"
    if ! cp "$STOCK_SRC" "$STOCK_DST"; then
      echo "::error::复制 stock_defconfig 失败: $STOCK_SRC -> $STOCK_DST"
      exit 1
    fi
    echo "已复制 stock_defconfig -> $STOCK_DST"

    NEW_RULE='$(obj)/config_data: arch/arm64/configs/stock_defconfig FORCE'
    OLD_RULE='$(obj)/config_data: $(KCONFIG_CONFIG) FORCE'
    TARGET_MAKEFILE="$KERNEL_ROOT/common/kernel/Makefile"

    if [ ! -f "$TARGET_MAKEFILE" ]; then
      echo "::error::未找到 $TARGET_MAKEFILE"
      exit 1
    fi

    if grep -qF "$NEW_RULE" "$TARGET_MAKEFILE"; then
      echo "config_data 规则已是 stock_defconfig，跳过。"
    elif grep -qF "$OLD_RULE" "$TARGET_MAKEFILE"; then
      sed -i 's|$(obj)/config_data: $(KCONFIG_CONFIG) FORCE|$(obj)/config_data: arch/arm64/configs/stock_defconfig FORCE|' "$TARGET_MAKEFILE"
      echo "已替换 config_data 规则: $TARGET_MAKEFILE"
    else
      echo "::error::未在 $TARGET_MAKEFILE 找到规则: $OLD_RULE"
      exit 1
    fi

}

# ---- 提取实际子版本号 ----
actual-sublevel() {
    ACTUAL_SUBLEVEL="${ABK_INPUT_SUB_LEVEL}"
    if [[ -f "$KERNEL_ROOT/common/Makefile" ]]; then
      EXTRACTED=$(grep '^SUBLEVEL = ' "$KERNEL_ROOT/common/Makefile" | awk '{print $3}')
      [[ -n "$EXTRACTED" ]] && ACTUAL_SUBLEVEL="$EXTRACTED"
    fi
    echo "ACTUAL_SUBLEVEL=$ACTUAL_SUBLEVEL" >> $GITHUB_ENV
    echo "实际子版本号: $ACTUAL_SUBLEVEL"

}

# ---- 备份基准 defconfig ----
backup-defconfig() {
    cp "$DEFCONFIG" "$DEFCONFIG.orig"

}

case "${1:-}" in
    stock-config) stock-config ;;
    actual-sublevel) actual-sublevel ;;
    backup-defconfig) backup-defconfig ;;
    *)
        echo "用法: $0 {stock-config|actual-sublevel|backup-defconfig}" >&2
        exit 2
        ;;
esac
