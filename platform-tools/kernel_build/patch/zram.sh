#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/zram.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 配置 ZRAM 补丁栈 ----
stack() {
    cd "$KERNEL_ROOT/common"
    if [ "${EFFECTIVE_ZRAM_LEGACY_LZ4K:-true}" = "true" ]; then
      echo "升级 LZ4..."
      rm -f lib/lz4/lz4_compress.c lib/lz4/lz4_decompress.c lib/lz4/lz4defs.h lib/lz4/lz4hc_compress.c

      cp -r $ZZH_PATCHES/zram/lz4/* ./lib/lz4/
      cp -r $ZZH_PATCHES/zram/include/linux/* ./include/linux/
      bash $ZZH_PATCHES/zram/apply_lz4_neon.sh

      if [ -f "fs/f2fs/Makefile" ] && ! grep -qF "f2fs-\$(CONFIG_F2FS_IOSTAT) += iostat.o" "fs/f2fs/Makefile"; then
        echo "f2fs-\$(CONFIG_F2FS_IOSTAT) += iostat.o" >> "fs/f2fs/Makefile"
      fi

      cp -r $SUKISU_PATCHES/other/zram/lz4k/include/linux/* ./include/linux/
      cp -r $SUKISU_PATCHES/other/zram/lz4k/lib/* ./lib/
      cp -r $SUKISU_PATCHES/other/zram/lz4k/crypto/* ./crypto/
      cp -r $SUKISU_PATCHES/other/zram/lz4k_oplus ./lib/

      for zram_patch_name in lz4kd.patch lz4k_oplus.patch; do
        zram_patch_file="$SUKISU_PATCHES/other/zram/zram_patch/${ABK_INPUT_KERNEL_VERSION}/$zram_patch_name"
        if [ ! -f "$zram_patch_file" ]; then
          echo "::warning::$zram_patch_name 不存在，跳过 ${ABK_INPUT_KERNEL_VERSION} 的 SukiSU ZRAM 集成补丁"
          continue
        fi

        cp "$zram_patch_file" ./
        if ! patch -p1 -F 3 < "$zram_patch_name"; then
          echo "::warning::$zram_patch_name 应用失败，可能已应用或上下文不匹配"
        fi
      done
    else
      echo "${ABK_INPUT_KERNEL_VERSION} 检测到新式 zram backend，跳过 legacy LZ4K/LZ4KD/LZ4K_OPLUS 补丁栈。"
    fi

}

# ---- 配置 ZRAM 选项 ----
options() {
    CONFIG_FILE="$DEFCONFIG"

    set_config_value() {
      local symbol="$1"
      local value="$2"
      local clean_symbol tmp

      clean_symbol="${symbol#CONFIG_}"
      tmp="$(mktemp)"
      grep -v -E "^(CONFIG_${clean_symbol}=|# CONFIG_${clean_symbol} is not set$)" "$CONFIG_FILE" > "$tmp" || true
      case "$value" in
        n)
          printf '# CONFIG_%s is not set\n' "$clean_symbol" >> "$tmp"
          ;;
        *)
          printf 'CONFIG_%s=%s\n' "$clean_symbol" "$value" >> "$tmp"
          ;;
      esac
      mv "$tmp" "$CONFIG_FILE"
    }

    disable_config() {
      set_config_value "$1" n
    }

    enable_legacy_full_algos() {
      set_config_value CONFIG_CRYPTO_LZO y
      set_config_value CONFIG_CRYPTO_LZ4 y
      set_config_value CONFIG_CRYPTO_DEFLATE y
      set_config_value CONFIG_CRYPTO_ZSTD y
    }

    enable_modern_full_algos() {
      set_config_value CONFIG_ZRAM_BACKEND_LZO y
      set_config_value CONFIG_ZRAM_BACKEND_LZ4 y
      set_config_value CONFIG_ZRAM_BACKEND_LZ4HC y
      set_config_value CONFIG_ZRAM_BACKEND_ZSTD y
      set_config_value CONFIG_ZRAM_BACKEND_DEFLATE y
      set_config_value CONFIG_ZRAM_BACKEND_842 y
    }

    enable_legacy_algo() {
      local algo="$1"
      case "$algo" in
        "lzo")        set_config_value CONFIG_CRYPTO_LZO y ;;
        "lz4")        set_config_value CONFIG_CRYPTO_LZ4 y ;;
        "lz4hc")      set_config_value CONFIG_CRYPTO_LZ4HC y ;;
        "lz4k")       set_config_value CONFIG_CRYPTO_LZ4K y ;;
        "lz4k_oplus") set_config_value CONFIG_CRYPTO_LZ4K_OPLUS y ;;
        "lz4kd")      set_config_value CONFIG_CRYPTO_LZ4KD y ;;
        "deflate")    set_config_value CONFIG_CRYPTO_DEFLATE y ;;
        "842")        set_config_value CONFIG_CRYPTO_842 y ;;
        "zstd")       set_config_value CONFIG_CRYPTO_ZSTD y ;;
        *) echo "::warning::未知的 ZRAM 算法: $algo，跳过" ;;
      esac
    }

    enable_modern_algo() {
      local algo="$1"
      case "$algo" in
        "lzo")        set_config_value CONFIG_ZRAM_BACKEND_LZO y ;;
        "lz4")        set_config_value CONFIG_ZRAM_BACKEND_LZ4 y ;;
        "lz4hc")      set_config_value CONFIG_ZRAM_BACKEND_LZ4HC y ;;
        "deflate")    set_config_value CONFIG_ZRAM_BACKEND_DEFLATE y ;;
        "842")        set_config_value CONFIG_ZRAM_BACKEND_842 y ;;
        "zstd")       set_config_value CONFIG_ZRAM_BACKEND_ZSTD y ;;
        "lz4k"|"lz4k_oplus"|"lz4kd")
          echo "::warning::${algo} 暂不支持新式 zram backend（6.12/6.18），跳过"
          ;;
        *)
          echo "::warning::未知的 ZRAM 算法: $algo，跳过"
          ;;
      esac
    }

    if [ "${ABK_INPUT_KERNEL_VERSION}" = "5.10" ]; then
      set_config_value CONFIG_MODULE_SIG n
    fi

    set_config_value CONFIG_ZSMALLOC y
    set_config_value CONFIG_ZRAM y

    python3 - "$KERNEL_ROOT" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
ENTRIES = ("drivers/block/zram/zram.ko", "mm/zsmalloc.ko")
QUOTES = "\"'"
patched, skipped = [], []
for rel in ("common/modules.bzl", "common/bazel/modules_private.bzl"):
    path = root / rel
    if not path.exists():
        skipped.append(rel)
        continue
    text = path.read_text(encoding="utf-8")
    new = text
    for entry in ENTRIES:
        escaped = re.escape(entry)
        line_pattern = re.compile(
            rf"^[ \t]*[{QUOTES}]{escaped}[{QUOTES}][ \t]*,?[ \t]*(?:#.*)?\n?",
            re.M,
        )
        new, removed_count = line_pattern.subn("", new)
        if removed_count == 0:
            new = re.sub(rf"[{QUOTES}]{escaped}[{QUOTES}][ \t]*,", "", new)
        if entry in new:
            raise SystemExit(f"{rel}: 无法安全移除模块条目 {entry}")
    if new != text:
        path.write_text(new, encoding="utf-8")
        patched.append(rel)
print("ZRAM/ZSMALLOC 模块清单清理:", ", ".join(patched) if patched else "无条目可删")
print("未找到的清单文件:", ", ".join(skipped) if skipped else "无")
PY

    if grep -q "CONFIG_ZSMALLOC=y" "$CONFIG_FILE" && grep -q "CONFIG_ZRAM=y" "$CONFIG_FILE"; then
      if [ "${EFFECTIVE_MODERN_GKI:-false}" != "true" ]; then
        while IFS= read -r line; do
          case "$line" in
            ''|'#'*) continue ;;
            CONFIG_*=*)
              set_config_value "${line%%=*}" "${line#*=}"
              ;;
          esac
        done < "$ZZH_PATCHES/config/zram.config"
      fi
    fi

    # ---- ZRAM 完整算法支持 ----
    if [ "${EFFECTIVE_ZRAM_FULL_ALGO:-false}" = "true" ]; then
      echo "启用 ZRAM 完整算法支持..."
      if [ "${EFFECTIVE_MODERN_GKI:-false}" = "true" ]; then
        enable_modern_full_algos
      else
        enable_legacy_full_algos
      fi
    elif [ -n "${EFFECTIVE_ZRAM_EXTRA_ALGOS:-}" ]; then
      # ---- 自定义单独算法 ----
      echo "添加自定义 ZRAM 算法: $EFFECTIVE_ZRAM_EXTRA_ALGOS"
      IFS=',' read -ra ALGOS <<< "$EFFECTIVE_ZRAM_EXTRA_ALGOS"
      for raw_algo in "${ALGOS[@]}"; do
        algo=$(echo "$raw_algo" | tr '[:upper:]' '[:lower:]' | tr -d ' ')
        if [ "${EFFECTIVE_MODERN_GKI:-false}" = "true" ]; then
          enable_modern_algo "$algo"
        else
          enable_legacy_algo "$algo"
        fi
      done
    fi

}

case "${1:-}" in
    stack) stack ;;
    options) options ;;
    *)
        echo "用法: $0 {stack|options}" >&2
        exit 2
        ;;
esac
