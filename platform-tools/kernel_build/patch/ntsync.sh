#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/ntsync.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 应用 NTsync 补丁 ----
ntsync() {
    cd "$KERNEL_ROOT/common"
    set -euo pipefail

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
        echo "$line" >> "$DEFCONFIG"
      fi
    }

    echo "启用 NTsync 补丁"
    BASE_PATCH="$KERNEL_PATCHES/common/ntsync/ntsync_base.patch"
    PATCH_FILE="$KERNEL_PATCHES/common/ntsync/ntsync_compat_${ABK_ANDROID_VERSION}-${ABK_KERNEL_VERSION}.patch"
    NTSYNC_SOURCE="drivers/misc/ntsync.c"

    apply_ntsync_patch() {
      local patch_file="$1"
      [ -f "$patch_file" ] || {
        echo "::error::NTsync 补丁不存在: $patch_file"
        exit 1
      }
      echo "检查并应用 NTsync 补丁: $(basename "$patch_file")"
      if patch -p1 --forward --dry-run < "$patch_file" >/dev/null 2>&1; then
        patch -p1 --forward < "$patch_file"
      elif patch -p1 --reverse --dry-run < "$patch_file" >/dev/null 2>&1; then
        echo "NTsync 补丁已应用: $(basename "$patch_file")"
      else
        echo "::error::NTsync 补丁既无法应用，也不是已应用状态: $patch_file"
        patch -p1 --forward --dry-run < "$patch_file" || true
        exit 1
      fi
    }

    apply_android12_ntsync_compat() {
      local patch_file="$1"
      local filtered_patch
      filtered_patch="$(mktemp)"
      # Android 12 GKI lacks lockdep_assert_none_held_once(), so the
      # upstream backport hunk never matches. Replace that hunk with the
      # equivalent macros needed by ntsync and apply the remaining patch.
      python3 - "$patch_file" "$filtered_patch" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
start = source.find("@@ -305,25 +310,29")
end = source.find("@@ -394,8 +403,12", start)
if start < 0 or end < 0:
    raise SystemExit("unexpected android12 NTsync compatibility patch layout")
source = source[:start] + source[end:]

lockdep = Path("kernel/locking/lockdep.c").read_text()
function_pos = lockdep.find("int __lock_is_held")
function_line = lockdep[:function_pos].count("\n") + 1 if function_pos >= 0 else 99999
if function_line < 5250:
    lockdep_start = source.find("@@ -5308,13 +5309,13")
    lockdep_end = source.find("@@ -5593,10 +5594,14", lockdep_start)
    if lockdep_start < 0 or lockdep_end < 0:
        raise SystemExit("unexpected android12 NTsync lockdep.c patch layout")
    source = source[:lockdep_start] + source[lockdep_end:]
Path(sys.argv[2]).write_text(source)
PY
      apply_ntsync_patch "$filtered_patch"
      rm -f "$filtered_patch"

      python3 - <<'PY'
from pathlib import Path

path = Path("include/linux/lockdep.h")
text = path.read_text()
if "#define lockdep_assert(cond)" not in text:
    marker = "#define lockdep_depth(tsk)\t(debug_locks ? (tsk)->lockdep_depth : 0)\n"
    if marker not in text:
        raise SystemExit(f"missing lockdep assertion anchor: {path}")
    block = """

#define lockdep_assert(cond) \
    do { WARN_ON(debug_locks && !(cond)); } while (0)

#define lockdep_assert_once(cond) \
    do { WARN_ON_ONCE(debug_locks && !(cond)); } while (0)
"""
    path.write_text(text.replace(marker, marker + block, 1))

lockdep_path = Path("kernel/locking/lockdep.c")
lockdep = lockdep_path.read_text()
function_start = lockdep.find("int __lock_is_held(const struct lockdep_map *lock, int read)")
function_end = lockdep.find("static struct pin_cookie __lock_pin_lock", function_start)
if function_start >= 0 and function_end >= 0:
    body = lockdep[function_start:function_end]
    if "return LOCK_STATE_HELD;" not in body:
        body = body.replace("return 1;", "return LOCK_STATE_HELD;", 1)
        body = body.replace("return 0;", "return LOCK_STATE_NOT_HELD;")
        lockdep_path.write_text(lockdep[:function_start] + body + lockdep[function_end:])
PY
      grep -qF '#define lockdep_assert(cond)' include/linux/lockdep.h || {
        echo "::error::Android 12 NTsync lockdep_assert 兼容修复失败"
        exit 1
      }
    }

    # Android 16/6.12 与 Android 17/6.18 已带原生 ntsync.c 与 UAPI；基础补丁是新增文件式补丁，
    # 对原生源码既不能正向应用，也不能用反向 dry-run 判定为已应用。
    NATIVE_NTSYNC="false"
    if [[ "$ABK_ANDROID_VERSION" == "android16" && "$ABK_KERNEL_VERSION" == "6.12" ]] \
      || [[ "$ABK_ANDROID_VERSION" == "android17" && "$ABK_KERNEL_VERSION" == "6.18" ]]; then
      NATIVE_NTSYNC="true"
    fi

    if [ "$NATIVE_NTSYNC" != "true" ]; then
      apply_ntsync_patch "$BASE_PATCH"
    elif [ -f "$NTSYNC_SOURCE" ] && [ -f include/uapi/linux/ntsync.h ]; then
      echo "NTsync 基础源码已由 ${ABK_ANDROID_VERSION}-${ABK_KERNEL_VERSION} 内核提供"
    else
      echo "::error::${ABK_ANDROID_VERSION}-${ABK_KERNEL_VERSION} 内核缺少原生 NTsync 源码或 UAPI"
      exit 1
    fi
    if [[ "$ABK_ANDROID_VERSION" == "android12" && "$ABK_KERNEL_VERSION" == "5.10" ]]; then
      apply_android12_ntsync_compat "$PATCH_FILE"
    elif [ "$ABK_ANDROID_VERSION" = "android17" ] && [ "$ABK_KERNEL_VERSION" = "6.18" ] \
      && [ ! -f "$PATCH_FILE" ]; then
      echo "::warning::未找到 ${ABK_ANDROID_VERSION}-${ABK_KERNEL_VERSION} 的 NTsync 兼容补丁 ($(basename "$PATCH_FILE"))，按内核原生实现继续。"
    else
      apply_ntsync_patch "$PATCH_FILE"
    fi

    [ -f "$NTSYNC_SOURCE" ] || {
      echo "::error::NTsync 补丁完成后未生成 $NTSYNC_SOURCE"
      exit 1
    }

    # 强制内建可避免 Android 14/6.1 将 ntsync.ko modpost 为独立模块；
    # __vfs_setxattr_noperm 在该内核树中存在但未导出，内建对象可合法引用。
    ensure_defconfig_value CONFIG_NTSYNC y
    echo "应用 NTsync 补丁完成"

}

case "${1:-}" in
    ntsync) ntsync ;;
    *)
        echo "用法: $0 {ntsync}" >&2
        exit 2
        ;;
esac
