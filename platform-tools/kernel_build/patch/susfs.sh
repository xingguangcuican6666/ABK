#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/susfs.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 应用 SUSFS 补丁 ----
apply() {
    cd "$KERNEL_ROOT"
    echo "应用 SUSFS 补丁..."
    # 读取 SUSFS 上游版本（例如 v2.1.0），用于匹配修复补丁目录
    SUSFS_VERSION=$(grep -E '^#define[[:space:]]+SUSFS_VERSION' "$SUSFS4KSU/kernel_patches/include/linux/susfs.h" | awk '{print $3}' | tr -d '"' | sed 's/^v//')
    if [ -z "$SUSFS_VERSION" ]; then
      SUSFS_VERSION="2.0.0"
    fi

    resolve_susfs_fix_root() {
      local version="$1"
      local candidate
      for candidate in \
        "$KERNEL_PATCHES/wild/susfs_fix_patches/v${version}" \
        "$KERNEL_PATCHES/wild/archived/susfs_fix_patches/v${version}" \
        "$KERNEL_PATCHES/ksu/susfs_fix_patches/v${version}"; do
        if [ -d "$candidate" ]; then
          printf '%s\n' "$candidate"
          return 0
        fi
      done
      return 1
    }

    SUSFS_FIX_VERSION="$SUSFS_VERSION"
    SUSFS_FIX_ROOT="$(resolve_susfs_fix_root "$SUSFS_FIX_VERSION" || true)"
    if [ -z "$SUSFS_FIX_ROOT" ] && [ "$SUSFS_FIX_VERSION" != "2.0.0" ]; then
      echo "未找到 v${SUSFS_FIX_VERSION} 的 SUSFS 修复补丁，回退 v2.0.0"
      SUSFS_VERSION="2.0.0"
      SUSFS_FIX_VERSION="$SUSFS_VERSION"
      SUSFS_FIX_ROOT="$(resolve_susfs_fix_root "$SUSFS_FIX_VERSION" || true)"
    fi

    SUSFS_KSU_FIX_ROOT=""
    if [ -d "$KERNEL_PATCHES/ksu/susfs_fix_patches/v${SUSFS_FIX_VERSION}" ]; then
      SUSFS_KSU_FIX_ROOT="$KERNEL_PATCHES/ksu/susfs_fix_patches/v${SUSFS_FIX_VERSION}"
    fi

    if [ -n "$SUSFS_FIX_ROOT" ]; then
      echo "SUSFS 修复补丁版本: v${SUSFS_FIX_VERSION}"
      echo "SUSFS 修复补丁目录: ${SUSFS_FIX_ROOT#$KERNEL_PATCHES/}"
    else
      echo "::warning::未找到可用的 SUSFS 修复补丁目录，将仅依赖源码兜底修复"
    fi

    apply_susfs_patch_file() {
      local rel_path="$1"
      local patch_source=""

      if [ -n "$SUSFS_FIX_ROOT" ] && [ -f "$SUSFS_FIX_ROOT/$rel_path" ]; then
        patch_source="$SUSFS_FIX_ROOT/$rel_path"
      elif [ -n "$SUSFS_KSU_FIX_ROOT" ] && [ -f "$SUSFS_KSU_FIX_ROOT/$rel_path" ]; then
        patch_source="$SUSFS_KSU_FIX_ROOT/$rel_path"
      elif [ -n "$SUSFS_KSU_FIX_ROOT" ]; then
        case "$rel_path" in
          */fdinfo.c.patch)
            [ -f "$SUSFS_KSU_FIX_ROOT/fix_fdinfo.c.patch" ] && patch_source="$SUSFS_KSU_FIX_ROOT/fix_fdinfo.c.patch"
            ;;
          */namespace.c.patch)
            [ -f "$SUSFS_KSU_FIX_ROOT/fix_namespace.c.patch" ] && patch_source="$SUSFS_KSU_FIX_ROOT/fix_namespace.c.patch"
            ;;
          */open.c.patch)
            [ -f "$SUSFS_KSU_FIX_ROOT/fix_open.c.patch" ] && patch_source="$SUSFS_KSU_FIX_ROOT/fix_open.c.patch"
            ;;
          */task_mmu.c.patch)
            [ -f "$SUSFS_KSU_FIX_ROOT/fix_task_mmu.c.patch" ] && patch_source="$SUSFS_KSU_FIX_ROOT/fix_task_mmu.c.patch"
            ;;
        esac
      fi

      if [ -z "$patch_source" ]; then
        return 1
      fi

      patch -p1 < "$patch_source" || true
      return 0
    }

    apply_susfs_optional_patch() {
      local description="$1"
      local rel_path="$2"

      if ! apply_susfs_patch_file "$rel_path"; then
        echo "::warning::$description 对应的 SUSFS 修复补丁不存在，已跳过"
      fi
      return 0
    }

    apply_susfs_base_patch() {
      local description="$1"
      local rel_path="$2"

      if apply_susfs_patch_file "$rel_path"; then
        return 0
      fi

      local applied=0 fallback_patch
      if [ -n "$SUSFS_KSU_FIX_ROOT" ]; then
        for fallback_patch in 1_fix_base.c.patch 2_fix_base.c.patch; do
          if [ -f "$SUSFS_KSU_FIX_ROOT/$fallback_patch" ]; then
            patch -p1 < "$SUSFS_KSU_FIX_ROOT/$fallback_patch" || true
            applied=1
          fi
        done
      fi

      if [ "$applied" -eq 0 ]; then
        echo "::warning::$description 对应的 SUSFS base 修复补丁不存在，已跳过"
      fi
      return 0
    }

    cp "$SUSFS4KSU/kernel_patches/50_add_susfs_in_gki-${ABK_ANDROID_VERSION}-${ABK_KERNEL_VERSION}.patch" ./common/
    cp $SUSFS4KSU/kernel_patches/fs/* ./common/fs/
    cp $SUSFS4KSU/kernel_patches/include/linux/* ./common/include/linux/

    case "$ABK_KSU_VARIANT" in
      "Official")
        cd ./KernelSU
        cp $SUSFS4KSU/kernel_patches/KernelSU/10_enable_susfs_for_ksu.patch ./
        # 兼容旧版补丁：仅当补丁仍只修改 kernel/Makefile 时，才替换为 kernel/Kbuild
        if grep -q '^diff --git a/kernel/Makefile b/kernel/Makefile' ./10_enable_susfs_for_ksu.patch \
          && ! grep -q '^diff --git a/kernel/Kbuild b/kernel/Kbuild' ./10_enable_susfs_for_ksu.patch; then
          sed -i 's|kernel/Makefile|kernel/Kbuild|g' ./10_enable_susfs_for_ksu.patch
        fi
        patch -p1 --forward < 10_enable_susfs_for_ksu.patch || true

        cd ..
        ;;
      "BakaSU")
        echo "BakaSU 使用内置 SUSFS 支持"
        ;;
      "SukiSU")
        echo "SukiSU 使用单独步骤注入 SUSFS Kconfig 符号"
        ;;
    esac

    cd $KERNEL_ROOT/common
    patch -p1 < "50_add_susfs_in_gki-${ABK_ANDROID_VERSION}-${ABK_KERNEL_VERSION}.patch" || true

    if [ "$ABK_KSU_VARIANT" == "SukiSU" ] || [ "$ABK_KSU_VARIANT" == "BakaSU" ]; then
      SUSFS_C="./fs/susfs.c"
      if [ -f "$SUSFS_C" ] && grep -q '^static void susfs_run_sus_path_loop(' "$SUSFS_C"; then
        sed -i 's/^static void susfs_run_sus_path_loop(/void susfs_run_sus_path_loop(/' "$SUSFS_C"
        echo "已导出 susfs_run_sus_path_loop 供 SukiSU/BakaSU LSM hook 调用"
      fi
    fi

    if [ "$ABK_KSU_VARIANT" == "Official" ] || [ "$ABK_KSU_VARIANT" == "SukiSU" ] || [ "$ABK_KSU_VARIANT" == "BakaSU" ]; then
      CURRENT_SUB="$ABK_SUB_LEVEL"
      if [[ ! "$CURRENT_SUB" =~ ^[0-9]+$ ]]; then
        CURRENT_SUB=99999
      fi

      sub_in_range() {
        local value="$1"
        local min="$2"
        local max="$3"
        [[ "$value" =~ ^[0-9]+$ ]] || return 1
        (( value >= min && value <= max ))
      }

      fix_task_mmu_show_pad() {
        local file="./fs/proc/task_mmu.c"
        [ -f "$file" ] || return 0

        if grep -qF 'goto show_pad;' "$file"; then
          sed -i -e 's/goto show_pad;/return 0;/' "$file"
          echo "已替换 task_mmu.c 中的 goto show_pad"
        fi

        if grep -q '^[[:space:]]*show_pad:[[:space:]]*$' "$file" && ! grep -qF 'goto show_pad;' "$file"; then
          sed -i '/^[[:space:]]*show_pad:[[:space:]]*$/d' "$file"
          echo "已移除 task_mmu.c 中无引用的 show_pad 标签，避免 -Wunused-label"
        fi
      }

      fix_task_mmu_vma_pad_start() {
        local file="./fs/proc/task_mmu.c"
        [ -f "$file" ] || return 0

        python3 - <<'PY'
from pathlib import Path
import re

path = Path("./fs/proc/task_mmu.c")
text = path.read_text()

if not re.search(r"\bVMA_PAD_START\s*\(", text):
    raise SystemExit(0)
if re.search(r"(?m)^\s*#\s*define\s+VMA_PAD_START(?:\s|\()", text):
    print("task_mmu.c 已定义 VMA_PAD_START")
    raise SystemExit(0)

anchor_matches = list(re.finditer(r'(?m)^#include\s+"internal\.h"\s*$', text))
if anchor_matches:
    insert_at = anchor_matches[-1].end()
else:
    include_matches = list(re.finditer(r"(?m)^#include\s+[<\"].*[>\"]\s*$", text))
    if not include_matches:
        raise SystemExit(f"{path} 使用 VMA_PAD_START，但无法定位头文件区域")
    insert_at = include_matches[-1].end()

fallback = """

#ifndef VMA_PAD_START
#define VMA_PAD_START(vma) ((vma)->vm_end)
#endif"""
path.write_text(text[:insert_at] + fallback + text[insert_at:])
print("已为 task_mmu.c 注入 VMA_PAD_START 兼容定义")
PY
      }

      fix_proc_base_missing_vma_decl() {
        local file="./fs/proc/base.c"
        [ -f "$file" ] || return 0
        grep -qF 'vma = find_vma(mm, addr);' "$file" || return 0

        python3 - <<'PY'
from pathlib import Path
import re

path = Path("./fs/proc/base.c")
lines = path.read_text().splitlines(keepends=True)

assign_re = re.compile(r"^\s*vma\s*=\s*find_vma\s*\(\s*mm\s*,\s*addr\s*\)\s*;")
vma_decl_re = re.compile(r"\bstruct\s+vm_area_struct\s*\*\s*vma\s*;")
mm_decl_re = re.compile(r"\bstruct\s+mm_struct\s*\*\s*mm(?:\b|[\s=;])")

def sanitize_line(line, in_block_comment):
    result = []
    index = 0
    in_string = None

    while index < len(line):
        if in_block_comment:
            end = line.find("*/", index)
            if end == -1:
                return "".join(result), True
            index = end + 2
            in_block_comment = False
            continue

        char = line[index]
        next_char = line[index + 1] if index + 1 < len(line) else ""

        if in_string is not None:
            if char == "\\" and index + 1 < len(line):
                index += 2
                continue
            if char == in_string:
                in_string = None
            index += 1
            continue

        if char == "/" and next_char == "*":
            in_block_comment = True
            index += 2
            continue
        if char == "/" and next_char == "/":
            break
        if char in ("'", '"'):
            in_string = char
            index += 1
            continue

        result.append(char)
        index += 1

    return "".join(result), in_block_comment

def find_function_ranges(source_lines):
    depth = 0
    in_block_comment = False
    function_start = None
    ranges = []

    for index, line in enumerate(source_lines):
        sanitized, in_block_comment = sanitize_line(line, in_block_comment)
        opens = sanitized.count("{")
        closes = sanitized.count("}")
        previous_depth = depth
        depth += opens - closes

        if previous_depth == 0 and depth > 0 and opens:
            function_start = index
        if function_start is not None and previous_depth > 0 and depth == 0:
            ranges.append((function_start, index))
            function_start = None

    return ranges

function_ranges = find_function_ranges(lines)
changed = False

for start, end in reversed(function_ranges):
    body = lines[start:end + 1]
    if not any(assign_re.search(line) for line in body):
        continue
    if any(vma_decl_re.search(line) for line in body):
        continue

    mm_offsets = [offset for offset, line in enumerate(body) if mm_decl_re.search(line)]
    if not mm_offsets:
        raise SystemExit(f"{path} 命中 vma 赋值但未找到同函数 mm 声明")

    mm_index = start + mm_offsets[-1]
    indent = re.match(r"^\s*", lines[mm_index]).group(0)
    lines.insert(mm_index + 1, f"{indent}struct vm_area_struct *vma;\n")
    changed = True

if changed:
    path.write_text("".join(lines))
    print("已修复 proc/base.c 缺少 vma 声明")

final_lines = path.read_text().splitlines()
final_ranges = find_function_ranges([line + "\n" for line in final_lines])
for start, end in final_ranges:
    body = final_lines[start:end + 1]
    if any(assign_re.search(line) for line in body) and not any(vma_decl_re.search(line) for line in body):
        raise SystemExit(f"{path} 仍存在未声明 vma 的 find_vma 调用")
PY
      }

      fix_namei_set_nameidata_arity() {
        local file="./fs/namei.c"
        [ -f "$file" ] || return 0

        python3 - <<'PY'
from pathlib import Path
import re

path = Path("./fs/namei.c")
text = path.read_text()

call_re = re.compile(r"set_nameidata\s*\(\s*nd\s*,\s*old_dfd\s*,\s*fake_filename\s*,\s*NULL\s*\)")
if not call_re.search(text):
    raise SystemExit(0)

definition = re.search(r"static\s+void\s+set_nameidata\s*\((.*?)\)\s*\{", text, re.S)
if definition is None:
    raise SystemExit(f"{path} 存在四参 set_nameidata 调用，但未找到定义")

params = [part.strip() for part in definition.group(1).split(",") if part.strip()]
if len(params) == 3:
    updated, count = call_re.subn("set_nameidata(nd, old_dfd, fake_filename)", text)
    if count:
        path.write_text(updated)
        print("已修复 namei.c 中 set_nameidata 参数个数")
    if call_re.search(path.read_text()):
        raise SystemExit(f"{path} 仍残留四参 set_nameidata 调用")
elif len(params) != 4:
    raise SystemExit(f"{path} 的 set_nameidata 定义参数个数异常: {len(params)}")
PY
      }

      fix_task_mmu_unused_vma_decl() {
        local file="./fs/proc/task_mmu.c"
        [ -f "$file" ] || return 0
        grep -qF 'struct vm_area_struct *vma;' "$file" || return 0

        python3 - <<'PY'
from pathlib import Path
import re

path = Path("./fs/proc/task_mmu.c")
lines = path.read_text().splitlines(keepends=True)
changed = False

for index, line in enumerate(lines):
    if "struct vm_area_struct *vma;" not in line:
        continue

    start = index
    while start > 0:
        if "{" in lines[start]:
            break
        start -= 1

    depth = 0
    end = len(lines)
    for cursor in range(start, len(lines)):
        depth += lines[cursor].count("{")
        depth -= lines[cursor].count("}")
        if cursor > start and depth <= 0:
            end = cursor + 1
            break

    body = "".join(lines[start:end])
    body_without_decl = body.replace(line, "", 1)
    if not re.search(r"\bvma\b", body_without_decl):
        lines[index] = ""
        changed = True

if changed:
    path.write_text("".join(lines))
PY

        if ! grep -qF 'struct vm_area_struct *vma;' "$file"; then
          echo "已移除 task_mmu.c 中无使用的 vma 声明"
        fi
      }

      fix_memory_missing_susfs_def() {
        local file="./mm/memory.c"
        [ -f "$file" ] || return 0
        grep -qF 'SUSFS_IS_INODE_SUS_MAP' "$file" || return 0
        grep -qF '#include <linux/susfs_def.h>' "$file" && return 0

        if grep -qF '#include <linux/zswap.h>' "$file"; then
          sed -i '/#include <linux\/zswap.h>/a #ifdef CONFIG_KSU_SUSFS\n#include <linux\/susfs_def.h>\n#endif' "$file"
        elif grep -qF '#include <linux/sched/sysctl.h>' "$file"; then
          sed -i '/#include <linux\/sched\/sysctl.h>/a #ifdef CONFIG_KSU_SUSFS\n#include <linux\/susfs_def.h>\n#endif' "$file"
        elif grep -qF '#include <linux/mm_inline.h>' "$file"; then
          sed -i '/#include <linux\/mm_inline.h>/a #ifdef CONFIG_KSU_SUSFS\n#include <linux\/susfs_def.h>\n#endif' "$file"
        elif grep -qF '#include <linux/mm.h>' "$file"; then
          sed -i '/#include <linux\/mm.h>/a #ifdef CONFIG_KSU_SUSFS\n#include <linux\/susfs_def.h>\n#endif' "$file"
        else
          sed -i '1i #ifdef CONFIG_KSU_SUSFS\n#include <linux/susfs_def.h>\n#endif' "$file"
        fi
        echo "已修复 memory.c 缺少 susfs_def.h 问题"
      }

      fix_task_mmu_missing_susfs_def() {
        local file="./fs/proc/task_mmu.c"
        [ -f "$file" ] || return 0
        # 仅当 SUSFS 主体 hunk 已应用（使用了宏）但头部 hunk 被 reject 时注入
        grep -q 'SUSFS_IS_INODE_SUS_MAP\|SUSFS_IS_INODE_OPEN_REDIRECT' "$file" || return 0
        grep -qF 'susfs_def.h' "$file" && return 0
        if grep -qF '#include <linux/pkeys.h>' "$file"; then
          sed -i '/#include <linux\/pkeys.h>/a #if defined(CONFIG_KSU_SUSFS_SUS_KSTAT) || defined(CONFIG_KSU_SUSFS_SUS_MAP) || defined(CONFIG_KSU_SUSFS_OPEN_REDIRECT)\n#include <linux\/susfs_def.h>\n#endif' "$file"
        elif grep -qF '#include <linux/shmem_fs.h>' "$file"; then
          sed -i '/#include <linux\/shmem_fs.h>/a #if defined(CONFIG_KSU_SUSFS_SUS_KSTAT) || defined(CONFIG_KSU_SUSFS_SUS_MAP) || defined(CONFIG_KSU_SUSFS_OPEN_REDIRECT)\n#include <linux\/susfs_def.h>\n#endif' "$file"
        else
          sed -i '1i #ifdef CONFIG_KSU_SUSFS\n#include <linux/susfs_def.h>\n#endif' "$file"
        fi
        echo "已修复 task_mmu.c 缺少 susfs_def.h 问题"
      }

      fix_a13_515_namespace_missing_susfs_def() {
        local file="./fs/namespace.c"
        [ -f "$file" ] || return 0
        grep -q 'DEFAULT_KSU_MNT_ID\|susfs_is_current_ksu_domain\|CL_COPY_MNT_NS' "$file" || return 0
        grep -qF 'susfs_def.h' "$file" && return 0
        sed -i '/#include <linux\/mnt_idmapping.h>/a #ifdef CONFIG_KSU_SUSFS\n#include <linux\/susfs_def.h>\n#endif' "$file"
        if ! grep -q 'extern bool susfs_is_current_ksu_domain' "$file"; then
          sed -i '/#include "internal.h"/a \\n#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT\nextern bool susfs_is_current_ksu_domain(void);\nextern struct static_key_true susfs_is_sdcard_android_data_not_decrypted;\n#ifndef CL_COPY_MNT_NS\n#define CL_COPY_MNT_NS BIT(25)\n#endif\n#endif' "$file"
        fi
        echo "已修复 android13-5.15 namespace.c 缺少 susfs_def.h 问题"
      }

      fix_selinux_extern_static() {
        local file="./security/selinux/hooks.c"
        if [ -f "$file" ] && grep -qF 'extern static int security_context_to_sid_with_policy' "$file"; then
          sed -i 's/extern static int security_context_to_sid_with_policy/int security_context_to_sid_with_policy/' "$file"
          echo "已修复 hooks.c 中 security_context_to_sid_with_policy 的 extern static 冲突"
        fi

        file="./security/selinux/selinuxfs.c"
        if [ -f "$file" ] && grep -qF 'extern static int security_context_to_sid_with_policy' "$file"; then
          sed -i 's/extern static int security_context_to_sid_with_policy/int security_context_to_sid_with_policy/' "$file"
          echo "已修复 selinuxfs.c 中 security_context_to_sid_with_policy 的 extern static 冲突"
        fi
        if [ -f "$file" ] && grep -qF 'extern static int security_sid_to_context_with_policy' "$file"; then
          sed -i 's/extern static int security_sid_to_context_with_policy/int security_sid_to_context_with_policy/' "$file"
          echo "已修复 selinuxfs.c 中 security_sid_to_context_with_policy 的 extern static 冲突"
        fi
      }

      fix_fdinfo_declarations() {
        local file="./fs/notify/fdinfo.c"
        [ -f "$file" ] || return 0

        python3 - <<'PY'
from pathlib import Path
import re

path = Path("./fs/notify/fdinfo.c")
lines = path.read_text().splitlines(keepends=True)

start = None
for index, line in enumerate(lines):
    if re.match(r"\s*static\s+void\s+inotify_fdinfo\s*\(", line):
        start = index
        break

if start is None:
    raise SystemExit(0)

brace_line = None
for index in range(start, len(lines)):
    if "{" in lines[index]:
        brace_line = index
        break

if brace_line is None:
    raise SystemExit(0)

depth = 0
end = None
for index in range(brace_line, len(lines)):
    depth += lines[index].count("{")
    depth -= lines[index].count("}")
    if index > brace_line and depth <= 0:
        end = index
        break

if end is None:
    raise SystemExit(0)

body = "".join(lines[brace_line + 1:end])
if "mask, mark->ignored_mask" not in body:
    raise SystemExit(0)

decl_re = re.compile(r"^\s*u32\s+mask\s*(?:=\s*mark->mask\s*&\s*IN_ALL_EVENTS)?\s*;\s*(?:/\*.*\*/\s*)?$")
new_body = [
    line for line in lines[brace_line + 1:end]
    if not decl_re.match(line)
]
declarations = ["\tu32 mask = mark->mask & IN_ALL_EVENTS;\n"]
if re.search(r"(?m)^\s*mnt\s*=\s*real_mount\(", "".join(new_body)) \
        and not re.search(r"\bstruct\s+mount\s*\*\s*mnt\b", "".join(new_body)):
    declarations.append("\tstruct mount *mnt;\n")
new_lines = lines[:brace_line + 1] + declarations + new_body + lines[end:]

if new_lines != lines:
    path.write_text("".join(new_lines))
    print("已修复 fdinfo.c 中局部变量声明")
else:
    print("fdinfo.c 局部变量声明无需修改")
PY
      }

      ensure_susfs_stat_compat() {
        local file="./fs/stat.c"
        [ -f "$file" ] || return 0

        python3 - <<'PY'
from pathlib import Path
import re

path = Path("./fs/stat.c")
text = path.read_text()
# 仅当 stat.c 原生代码已是新式 idmap API（6.5+）时，才把 SUSFS 补丁带入的
# mnt_userns 旧式调用改写为 4 参 generic_fillattr；5.10/5.15/6.1 必须保持 3 参旧式，
# 否则会引用不存在的 AT_GETATTR_NOSEC 并触发 "expected 3, have 4"。
native_new_api = "struct mnt_idmap" in text or "mnt_idmap(path->mnt)" in text
if "mnt_userns" in text and native_new_api:
    text = text.replace(
        "inode->i_op->getattr(mnt_userns, path, stat,\n\t\t\t\trequest_mask, query_flags);",
        "inode->i_op->getattr(idmap, path, stat,\n\t\t\t\trequest_mask, query_flags | AT_GETATTR_NOSEC);",
    )
    text = text.replace(
        "generic_fillattr(mnt_userns, inode, stat);",
        "generic_fillattr(idmap, request_mask, inode, stat);",
    )
    if "mnt_userns" in text:
        text = text.replace("mnt_userns", "idmap")
    path.write_text(text)
    print("已修复 fs/stat.c 的 mnt_userns -> idmap 兼容")
if "susfs_get_non_sus_mnt_id_unique_from_mnt(" in text:
    declaration = "extern u64 susfs_get_non_sus_mnt_id_unique_from_mnt(struct mount *orig_mnt);"
    call = text.find("susfs_get_non_sus_mnt_id_unique_from_mnt(")
    declared = text.find(declaration)
    if declared < 0 or declared > call:
        anchor = "extern void susfs_sus_kstat_spoof_generic_fillattr(struct inode *inode, struct kstat *stat, u32 result_mask);"
        if anchor in text:
            text = text.replace(anchor, anchor + "\n" + declaration, 1)
        else:
            include_anchor = "#include \"mount.h\"\n"
            if include_anchor not in text:
                raise SystemExit(f"无法定位 {path} 的 SUSFS stat 声明插入点")
            block = "#ifdef CONFIG_KSU_SUSFS_SUS_KSTAT\n" + declaration + "\n#endif\n"
            text = text.replace(include_anchor, include_anchor + "\n" + block, 1)
        path.write_text(text)
        print("已补齐 fs/stat.c 的 SUSFS mount id helper 原型")
PY
      }

      ensure_susfs_stat_compat

      ensure_susfs_namespace_tail() {
        local file="./fs/namespace.c"
        [ -f "$file" ] || return 0

        python3 - <<'PY'
from pathlib import Path
import re

path = Path("./fs/namespace.c")
text = path.read_text()
opens = len(re.findall(r"(?m)^[ \t]*#[ \t]*(if|ifdef|ifndef)\b", text))
closes = len(re.findall(r"(?m)^[ \t]*#[ \t]*endif\b", text))
# SUSFS 上游偶发坏 hunk 头（声明的新行数少于实际行数，如 +4454,34 实为 43）时，
# patch 只截取尾部一部分，留下未闭合的 #ifdef CONFIG_KSU_SUSFS，
# 编译报 "unterminated conditional directive"。按已知截断签名补齐尾部。
if closes >= opens or not text.rstrip().endswith("if (!mnt->mnt.mnt_root || IS_ERR(mnt->mnt.mnt_root)) {"):
    raise SystemExit(0)
completion = (
    "\t\tmntput(&mnt->mnt);\n"
    "\t\tunlock_mount_hash();\n"
    "\t\treturn vfsmnt;\n"
    "\t}\n"
    "\tdget(mnt->mnt.mnt_root);\n"
    "\tunlock_mount_hash();\n"
    "\treturn &mnt->mnt;\n"
    "}\n"
    "#endif // #ifdef CONFIG_KSU_SUSFS\n"
)
path.write_text(text.rstrip("\n") + "\n" + completion)
print("已补齐 fs/namespace.c 被截断的 SUSFS 尾部")
PY
      }

      ensure_susfs_namespace_tail

      ensure_susfs_super_compat() {
        local file="./fs/super.c"
        [ -f "$file" ] || return 0

        python3 - <<'PY'
from pathlib import Path

path = Path("./fs/super.c")
text = path.read_text()
needed = (
    "susfs_is_current_ksu_domain",
    "susfs_is_sdcard_android_data_not_decrypted",
    "DEFAULT_KSU_MNT_MINOR_DEV",
)
if not any(name in text for name in needed):
    raise SystemExit(0)

if "#include <linux/susfs_def.h>" not in text:
    anchor = "#include <linux/fs_context.h>\n"
    if anchor not in text:
        raise SystemExit(f"无法定位 {path} 的 susfs_def.h 插入点")
    text = text.replace(anchor, anchor + "#ifdef CONFIG_KSU_SUSFS\n#include <linux/susfs_def.h>\n#endif\n", 1)

if "extern bool susfs_is_current_ksu_domain(void);" not in text:
    anchor = '#include "internal.h"\n'
    if anchor not in text:
        raise SystemExit(f"无法定位 {path} 的 SUSFS extern 插入点")
    block = (
        "\n#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT\n"
        "extern bool susfs_is_current_ksu_domain(void);\n"
        "extern struct static_key_true susfs_is_sdcard_android_data_not_decrypted;\n"
        "#ifndef DEFAULT_KSU_MNT_MINOR_DEV\n"
        "#define DEFAULT_KSU_MNT_MINOR_DEV (1 << 12)\n"
        "#endif\n"
        "#endif\n"
    )
    text = text.replace(anchor, anchor + block, 1)

path.write_text(text)
print("已补齐 fs/super.c 的 SUSFS include/extern 声明")
PY
      }

      ensure_susfs_super_compat

      # Android 12 - 5.10 修复
      if [[ "$ABK_ANDROID_VERSION" == "android12" && "$ABK_KERNEL_VERSION" == "5.10" ]]; then
        if sub_in_range "$CURRENT_SUB" 43 117; then
          fix_task_mmu_show_pad
          apply_susfs_optional_patch "Android 12 - 5.10 fdinfo.c" "a12-5.10/fdinfo.c.patch"
          fix_fdinfo_declarations
          apply_susfs_base_patch "Android 12 - 5.10 base.c" "a12-5.10/base.c.patch"
        fi
        fix_proc_base_missing_vma_decl
        fix_namei_set_nameidata_arity
        # Android 12/5.10 的上游补丁在 statfs.c 本地声明该 helper；
        # 某些分支头部 hunk 漏打，且 susfs_def.h 并不提供该原型。
        python3 - <<'PY'
from pathlib import Path

path = Path("./fs/statfs.c")
text = path.read_text()
declaration = "extern int susfs_sus_kstat_spoof_vfs_statfs(struct inode *inode, struct kstatfs *buf, bool *is_fuse);"
call = text.find("susfs_sus_kstat_spoof_vfs_statfs(")
declared = text.find(declaration)
if call >= 0 and (declared < 0 or declared > call):
    block = f"#ifdef CONFIG_KSU_SUSFS_SUS_KSTAT\n{declaration}\n#endif\n"
    anchor = "#include <linux/security.h>\n"
    if anchor not in text:
        raise SystemExit(f"无法定位 {path} 的 SUSFS statfs helper 声明插入点")
    path.write_text(text.replace(anchor, anchor + block, 1))
    print("已修复 android12-5.10 fs/statfs.c 缺少前置 SUSFS statfs helper 原型")

PY
      fi

      python3 - <<'PY'
from pathlib import Path

susfs_path = Path("./fs/susfs.c")
if susfs_path.is_file():
    susfs_text = susfs_path.read_text()
    if "security_sb_statfs(" in susfs_text and "#include <linux/security.h>" not in susfs_text:
        anchor = "#include <linux/statfs.h>\n"
        if anchor not in susfs_text:
            raise SystemExit(f"无法定位 {susfs_path} 的 security.h 插入点")
        susfs_path.write_text(susfs_text.replace(anchor, anchor + "#include <linux/security.h>\n", 1))
        print("已修复 fs/susfs.c 缺少 security_sb_statfs 原型")
PY

      # Android 13 - 5.15 修复
      if [[ "$ABK_ANDROID_VERSION" == "android13" && "$ABK_KERNEL_VERSION" == "5.15" ]]; then
        if sub_in_range "$CURRENT_SUB" 41 137; then
          apply_susfs_optional_patch "Android 13 - 5.15 namespace.c" "a13-5.15/namespace.c.patch"
          if [[ "$CURRENT_SUB" -eq 41 ]] && grep -qF 'static_branch_unlikely(&susfs_is_sdcard_android_data_not_decrypted)' ./fs/namespace.c; then
            if grep -qF 'extern bool susfs_is_sdcard_android_data_decrypted __read_mostly;' ./fs/namespace.c \
              && ! grep -qF 'extern struct static_key_true susfs_is_sdcard_android_data_not_decrypted;' ./fs/namespace.c; then
              sed -i 's/extern bool susfs_is_sdcard_android_data_decrypted __read_mostly;/extern struct static_key_true susfs_is_sdcard_android_data_not_decrypted;/' ./fs/namespace.c
            elif ! grep -qF 'extern struct static_key_true susfs_is_sdcard_android_data_not_decrypted;' ./fs/namespace.c; then
              sed -i '/extern bool susfs_is_current_ksu_domain(void);/a extern struct static_key_true susfs_is_sdcard_android_data_not_decrypted;' ./fs/namespace.c
            fi
            echo "已修复 android13-5.15.41 namespace.c 的 SUSFS sdcard static key 声明"
          fi
          if [[ "$CURRENT_SUB" -eq 41 ]]; then
            if [ -f ./fs/susfs.c ] && grep -qF 'i_user_ns(' ./fs/susfs.c; then
              sed -i 's/i_user_ns(&fi->inode)/fi->inode.i_sb->s_user_ns/g' ./fs/susfs.c
              sed -i 's/i_user_ns(inode)/inode->i_sb->s_user_ns/g' ./fs/susfs.c
              echo "已修复 android13-5.15.41 fs/susfs.c 缺少 i_user_ns helper"
            fi
            if grep -qF 'static DEFINE_IDA(susfs_mnt_id_ida);' ./fs/namespace.c \
              && [ "$(grep -F 'susfs_mnt_id_ida' ./fs/namespace.c | wc -l)" -eq 1 ]; then
              sed -i '/static DEFINE_IDA(susfs_mnt_id_ida);/d' ./fs/namespace.c
              echo "已移除 android13-5.15.41 namespace.c 未使用的 susfs_mnt_id_ida"
            fi
            if grep -qF 'static DEFINE_IDA(susfs_mnt_group_ida);' ./fs/namespace.c \
              && [ "$(grep -F 'susfs_mnt_group_ida' ./fs/namespace.c | wc -l)" -eq 1 ]; then
              sed -i '/static DEFINE_IDA(susfs_mnt_group_ida);/d' ./fs/namespace.c
              echo "已移除 android13-5.15.41 namespace.c 未使用的 susfs_mnt_group_ida"
            fi
          fi
          apply_susfs_optional_patch "Android 13 - 5.15 fdinfo.c" "a13-5.15/fdinfo.c.patch"
          fix_fdinfo_declarations
          apply_susfs_optional_patch "Android 13 - 5.15 open.c" "a13-5.15/open.c.patch"
          if [[ "$ABK_OS_PATCH_LEVEL" != "2024-05" ]]; then
            fix_task_mmu_show_pad
          fi
        fi
        # 2026-10: susfs4ksu gki-android13-5.15 分支 rebase 到 5.15.211 后，
        # task_mmu.c / namespace.c 的头部 hunk（注入 susfs_def.h include 与
        # extern 声明）在 <5.15.211 的所有子版本上被 reject，而主体 hunk 仍会
        # 应用，导致 SUSFS_IS_INODE_* / DEFAULT_KSU_MNT_ID 等符号未声明。
        # 这里对所有 5.15 子版本无条件做源码兜底注入（自带幂等守卫）。
        fix_task_mmu_missing_susfs_def
        fix_a13_515_namespace_missing_susfs_def
      fi

      # Android 14 - 6.1 修复
      if [[ "$ABK_ANDROID_VERSION" == "android14" && "$ABK_KERNEL_VERSION" == "6.1" ]]; then
        if sub_in_range "$CURRENT_SUB" 25 162; then
          apply_susfs_base_patch "Android 14 - 6.1 base.c" "a14-6.1/base.c.patch"
          if grep -q 'susfs_is_current_proc_umounted\|SUSFS_IS_INODE_SUS_MAP\|SUSFS_IS_INODE_OPEN_REDIRECT' ./fs/proc/base.c && ! grep -qF '#include <linux/susfs_def.h>' ./fs/proc/base.c; then
            if grep -qF '#include <linux/dma-buf.h>' ./fs/proc/base.c; then
              sed -i '/#include <linux\/dma-buf.h>/a #if defined(CONFIG_KSU_SUSFS_SUS_MAP) || defined(CONFIG_KSU_SUSFS_OPEN_REDIRECT)\n#include <linux\/susfs_def.h>\n#endif' ./fs/proc/base.c
            else
              sed -i '/#include <linux\/cpufreq_times.h>/a #if defined(CONFIG_KSU_SUSFS_SUS_MAP) || defined(CONFIG_KSU_SUSFS_OPEN_REDIRECT)\n#include <linux\/susfs_def.h>\n#endif' ./fs/proc/base.c
            fi
          fi
          if [[ "$ABK_OS_PATCH_LEVEL" != "2024-05" ]]; then
            fix_task_mmu_show_pad
          fi
          # 修复 6.1 LTS: SUSFS 补丁在 namespace.c 头部 hunk 因上下文不匹配而失败，
          # 导致函数体中的 SUSFS 符号（DEFAULT_KSU_MNT_ID 等）未声明
          if grep -q 'DEFAULT_KSU_MNT_ID\|susfs_mnt_id_ida' ./fs/namespace.c && ! grep -qF '#include <linux/susfs_def.h>' ./fs/namespace.c; then
            echo "检测到 namespace.c 缺少 susfs_def.h，注入声明..."
            sed -i '/#include <linux\/mnt_idmapping.h>/a #ifdef CONFIG_KSU_SUSFS_SUS_MOUNT\n#include <linux\/susfs_def.h>\n#endif' ./fs/namespace.c
            if ! grep -q 'extern bool susfs_is_current_ksu_domain' ./fs/namespace.c; then
              sed -i '/#include "internal.h"/a \\n#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT\nextern bool susfs_is_current_ksu_domain(void);\nextern struct static_key_true susfs_is_sdcard_android_data_not_decrypted;\n\n#define CL_COPY_MNT_NS BIT(25)\n\n#endif' ./fs/namespace.c
            fi
          fi
        fi
      fi

      # Android 15 - 6.6 修复
      if [[ "$ABK_ANDROID_VERSION" == "android15" && "$ABK_KERNEL_VERSION" == "6.6" ]]; then
        if [[ "$ABK_KSU_VARIANT" == "Official" ]] && { sub_in_range "$CURRENT_SUB" 50 58 || sub_in_range "$CURRENT_SUB" 98 127; }; then
          fix_selinux_extern_static
        fi
        if sub_in_range "$CURRENT_SUB" 98 127; then
          apply_susfs_base_patch "Android 15 - 6.6 base.c" "a15-6.6/base.c.patch"
        fi
        # 修复 fs/proc/base.c 缺少 susfs_def.h (不限 sublevel，检测到宏调用但缺头文件时注入)
        if grep -q 'AS_FLAGS_SUS_MAP\|susfs_is_current_proc_umounted\|SUSFS_IS_INODE_SUS_MAP\|SUSFS_IS_INODE_OPEN_REDIRECT' ./fs/proc/base.c && ! grep -qF 'susfs_def.h' ./fs/proc/base.c; then
          if ! grep -qF '#include <linux/dma-buf.h>' ./fs/proc/base.c; then
            sed -i '/#include <linux\/cpufreq_times.h>/a #include <linux\/dma-buf.h>' ./fs/proc/base.c
          fi
          sed -i '/#include <linux\/dma-buf.h>/a #endif' ./fs/proc/base.c
          sed -i '/#include <linux\/dma-buf.h>/a #include <linux\/susfs_def.h>' ./fs/proc/base.c
          sed -i '/#include <linux\/dma-buf.h>/a #if defined(CONFIG_KSU_SUSFS_SUS_MAP) || defined(CONFIG_KSU_SUSFS_OPEN_REDIRECT)' ./fs/proc/base.c
        fi
        if sub_in_range "$CURRENT_SUB" 50 58; then
          apply_susfs_optional_patch "Android 15 - 6.6 task_mmu.c" "a15-6.6/task_mmu.c.patch"
          fix_task_mmu_unused_vma_decl
          fix_memory_missing_susfs_def
        fi
      fi

      # Android 16 - 6.12 / Android 17 - 6.18 修复
      if { [[ "$ABK_ANDROID_VERSION" == "android16" && "$ABK_KERNEL_VERSION" == "6.12" ]] || \
           [[ "$ABK_ANDROID_VERSION" == "android17" && "$ABK_KERNEL_VERSION" == "6.18" ]]; }; then
        # 修复 setuid_hook.c 重复定义
        SETUID_HOOK="$KERNEL_ROOT/common/drivers/kernelsu/setuid_hook.c"
        if [ -f "$SETUID_HOOK" ]; then
          sed -i 's/defined(CONFIG_KSU_MANUAL_HOOK))/!defined(CONFIG_KSU_SUSFS) \&\& defined(CONFIG_KSU_MANUAL_HOOK))/' "$SETUID_HOOK"
          echo "已修复 setuid_hook.c 重复定义问题"
        fi

        # 修复 6.12.58+: exec.c 头部上下文变化导致 SUSFS 补丁漏掉 susfs_def.h
        if grep -qE 'susfs_is_current_proc_(no_su|umounted)' ./fs/exec.c && ! grep -qF '#include <linux/susfs_def.h>' ./fs/exec.c; then
          if grep -qF '#include <linux/dma-buf.h>' ./fs/exec.c; then
            sed -i '/#include <linux\/dma-buf.h>/a #ifdef CONFIG_KSU_SUSFS\n#include <linux\/susfs_def.h>\n#endif' ./fs/exec.c
          else
            sed -i '/#include <linux\/ksm.h>/a #ifdef CONFIG_KSU_SUSFS\n#include <linux\/susfs_def.h>\n#endif' ./fs/exec.c
          fi
          echo "已修复 exec.c 缺少 susfs_def.h 问题"
        fi
      fi

      fix_task_mmu_vma_pad_start
    fi

}

# ---- 确保 SUSFS Kconfig 与核心对象 ----
kconfig() {
    cd "$KERNEL_ROOT"
    set -euo pipefail
    KSU_KERNEL_DIR=""
    for dir in ./common/drivers/kernelsu ./drivers/kernelsu ./KernelSU/kernel ./kernel; do
      if [ -f "$dir/Kconfig" ]; then
        KSU_KERNEL_DIR="$dir"
        break
      fi
    done
    if [ -z "$KSU_KERNEL_DIR" ]; then
      echo "::error::未找到 ${ABK_INPUT_KSU_VARIANT} Kconfig，无法注入 SUSFS Kconfig 符号"
      exit 1
    fi

    python3 - "$KSU_KERNEL_DIR/Kconfig" "$KSU_KERNEL_DIR" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
ksu_dir = Path(sys.argv[2])
text = path.read_text()

options = [
    ("KSU_SUSFS", "KernelSU addon - SUSFS", ["KSU", "THREAD_INFO_IN_TASK"]),
    ("KSU_SUSFS_SUS_PATH", "Enable to hide suspicious path", ["KSU_SUSFS"]),
    ("KSU_SUSFS_SUS_MOUNT", "Enable to hide suspicious mounts", ["KSU_SUSFS"]),
    ("KSU_SUSFS_SUS_KSTAT", "Enable to spoof suspicious kstat", ["KSU_SUSFS"]),
    ("KSU_SUSFS_SPOOF_UNAME", "Enable to spoof uname", ["KSU_SUSFS"]),
    ("KSU_SUSFS_ENABLE_LOG", "Enable logging susfs log to kernel", ["KSU_SUSFS"]),
    ("KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS", "Enable to hide ksu and susfs symbols", ["KSU_SUSFS"]),
    ("KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG", "Enable to spoof cmdline or bootconfig", ["KSU_SUSFS"]),
    ("KSU_SUSFS_OPEN_REDIRECT", "Enable to redirect opened paths", ["KSU_SUSFS"]),
    ("KSU_SUSFS_SUS_MAP", "Enable to hide suspicious maps", ["KSU_SUSFS"]),
]

def defined_symbols(value):
    return set(re.findall(
        r"(?m)^[ \t]*(?:menuconfig|config)[ \t]+([A-Za-z0-9_]+)\b",
        value,
    ))

missing_options = [option for option in options if option[0] not in defined_symbols(text)]
if missing_options:
    lines = ["", 'menu "KernelSU - SUSFS (ABK compatibility)"']
    for name, prompt, deps in missing_options:
        lines.extend([f"config {name}", f'  bool "{prompt}"'])
        lines.extend(f"  depends on {dep}" for dep in deps)
        lines.extend(["  default y", ""])
    lines.append("endmenu")
    block = "\n".join(lines)

    marker = "\nendmenu\n"
    if marker in text:
        text = text.rsplit(marker, 1)[0].rstrip() + "\n" + block + marker
    else:
        text = text.rstrip() + "\n" + block + "\n"
    path.write_text(text)
    print(f"已向 {path} 注入缺失的 SUSFS Kconfig 符号: "
          f"{', '.join(option[0] for option in missing_options)}")
else:
    print(f"{path} 已包含全部 SUSFS Kconfig 符号")

required_symbols = {option[0] for option in options}
still_missing = sorted(required_symbols - defined_symbols(path.read_text()))
if still_missing:
    raise SystemExit(f"{path} 注入后仍缺少 SUSFS Kconfig 符号: {still_missing}")

susfs_source = Path("./common/fs/susfs.c")
fs_makefile = Path("./common/fs/Makefile")
if not susfs_source.exists():
    raise SystemExit(f"未找到 {susfs_source}，无法提供 SUSFS 核心符号")
if not fs_makefile.exists():
    raise SystemExit(f"未找到 {fs_makefile}，无法接入 SUSFS 核心对象")

makefile_text = fs_makefile.read_text()
susfs_object_rule = re.compile(
    r"(?m)^[ \t]*obj-\$\(CONFIG_KSU_SUSFS\)[ \t]*\+=[ \t]*susfs\.o(?:[ \t]|$)"
)
if not susfs_object_rule.search(makefile_text):
    makefile_text = makefile_text.rstrip() + (
        "\n\n# ABK: ensure the SUSFS provider is linked when its Kconfig is enabled.\n"
        "obj-$(CONFIG_KSU_SUSFS) += susfs.o\n"
    )
    fs_makefile.write_text(makefile_text)
    print(f"已补齐 {fs_makefile} 的 SUSFS 核心对象规则")
else:
    print(f"{fs_makefile} 已接入 SUSFS 核心对象")

provider_patterns = {
    "susfs_extra_works": r"(?m)^[ \t]*struct[ \t]+work_struct[ \t]+susfs_extra_works[ \t]*;",
    "susfs_get_enabled_features": r"(?m)^[ \t]*void[ \t]+susfs_get_enabled_features[ \t]*\(",
    "susfs_set_avc_log_spoofing": r"(?m)^[ \t]*void[ \t]+susfs_set_avc_log_spoofing[ \t]*\(",
    "susfs_show_version": r"(?m)^[ \t]*void[ \t]+susfs_show_version[ \t]*\(",
    "susfs_show_variant": r"(?m)^[ \t]*void[ \t]+susfs_show_variant[ \t]*\(",
    "susfs_start_sdcard_monitor_fn": r"(?m)^[ \t]*void[ \t]+susfs_start_sdcard_monitor_fn[ \t]*\(",
}
caller_text = "\n".join(
    source.read_text(errors="ignore")
    for source in ksu_dir.rglob("*.c")
)
provider_text = susfs_source.read_text()
missing_providers = [
    symbol for symbol, pattern in provider_patterns.items()
    if re.search(rf"\b{re.escape(symbol)}\b", caller_text)
    and not re.search(pattern, provider_text)
]
if missing_providers:
    raise SystemExit(
        f"{susfs_source} 缺少 KernelSU 已引用的 SUSFS 核心符号: {missing_providers}"
    )
if not susfs_object_rule.search(fs_makefile.read_text()):
    raise SystemExit(f"{fs_makefile} 注入后仍未接入 susfs.o")
print("SUSFS Kconfig 与核心链接契约校验通过")
PY

}

# ---- 添加 SUSFS 配置 ----
config() {
    cd "$KERNEL_ROOT"
    if [ "${ABK_INPUT_KSU_VARIANT}" == "SukiSU" ]; then
      KSU_SOURCE_DIR=""
      for dir in common/drivers/kernelsu drivers/kernelsu KernelSU/kernel kernel; do
        if [ -f "$dir/infra/symbol_resolver.c" ]; then
          KSU_SOURCE_DIR="$dir"
          break
        fi
      done

      if [ -z "$KSU_SOURCE_DIR" ] \
        || ! grep -qF 'ksu_resolve_symbol_for_functable_hook' "$KSU_SOURCE_DIR/infra/symbol_resolver.c" \
        || ! grep -qF 'find_kernel_symbol_exact' "$KSU_SOURCE_DIR/infra/symbol_resolver.c" \
        || ! grep -qF 'ksu_init_symbol_resolver' "$KSU_SOURCE_DIR/infra/symbol_resolver.c"; then
        echo "::warning::SukiSU 源码仍缺少完整 SUSFS 导出符号，跳过 CONFIG_KSU_SUSFS* 写入以避免链接失败"
        exit 0
      fi
    fi

    cat >> "$DEFCONFIG" << 'EOF'
CONFIG_KSU_SUSFS=y
CONFIG_KSU_SUSFS_SUS_PATH=y
CONFIG_KSU_SUSFS_SUS_MOUNT=y
CONFIG_KSU_SUSFS_SUS_KSTAT=y
CONFIG_KSU_SUSFS_SPOOF_UNAME=y
CONFIG_KSU_SUSFS_ENABLE_LOG=y
CONFIG_KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS=y
CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG=y
CONFIG_KSU_SUSFS_OPEN_REDIRECT=y
CONFIG_KSU_SUSFS_SUS_MAP=y
EOF

}

case "${1:-}" in
    apply) apply ;;
    kconfig) kconfig ;;
    config) config ;;
    *)
        echo "用法: $0 {apply|kconfig|config}" >&2
        exit 2
        ;;
esac
