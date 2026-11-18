#!/usr/bin/env bash
# 内核启动与编译必需的底层修复（设备修复、CVE、编译兼容）。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/core/kernel-specific.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 应用 Kernel 特定补丁 ----
kernel-specific() {
    cd "$KERNEL_ROOT/common"
    # 修复指定 2024-11 分支: mmap.c 调用了 vm_flags_clear()，但同分支 mm.h 未提供 helper
    if [[ "${ABK_INPUT_OS_PATCH_LEVEL}" == "2024-11" ]] \
      && { [[ "${ABK_INPUT_ANDROID_VERSION}" == "android12" && "${ABK_INPUT_KERNEL_VERSION}" == "5.10" && "${ABK_INPUT_SUB_LEVEL}" == "226" ]] \
        || [[ "${ABK_INPUT_ANDROID_VERSION}" == "android13" && "${ABK_INPUT_KERNEL_VERSION}" == "5.15" && "${ABK_INPUT_SUB_LEVEL}" == "167" ]] \
        || [[ "${ABK_INPUT_ANDROID_VERSION}" == "android15" && "${ABK_INPUT_KERNEL_VERSION}" == "6.6" ]]; } \
      && grep -qF 'vm_flags_clear(new_vma, VM_PAD_MASK);' ./mm/mmap.c \
      && ! grep -qF 'static inline void vm_flags_clear(' ./include/linux/mm.h; then
      python3 - "${ABK_INPUT_KERNEL_VERSION}" <<'PY'
import sys
from pathlib import Path

kernel_version = sys.argv[1]
path = Path("include/linux/mm.h")
text = path.read_text()
if kernel_version in {"5.10", "5.15"}:
    helper = """
static inline void vm_flags_clear(struct vm_area_struct *vma,
                                  vm_flags_t flags)
{
    vma->vm_flags &= ~flags;
}
"""
else:
    helper = """
static inline void vm_flags_clear(struct vm_area_struct *vma,
                                  vm_flags_t flags)
{
    vma_start_write(vma);
    ACCESS_PRIVATE(vma, __vm_flags) &= ~flags;
}
"""

if "static inline void vm_flags_clear(" not in text:
    marker = "/*\n * Use only if VMA is not part of the VMA tree"
    if marker in text:
        text = text.replace(marker, helper + "\n" + marker, 1)
    else:
        needle = "static inline void vm_flags_set(struct vm_area_struct *vma,"
        start = text.find(needle)
        if start != -1:
            body_start = text.find("{", start)
            body_end = text.find("\n}\n", body_start)
            if body_start == -1 or body_end == -1:
                raise SystemExit("include/linux/mm.h vm_flags_set() has unexpected format")
            insert_at = body_end + len("\n}\n")
        elif "#endif /* _LINUX_MM_H */" in text:
            insert_at = text.rfind("#endif /* _LINUX_MM_H */")
        elif "#endif" in text:
            insert_at = text.rfind("#endif")
        else:
            raise SystemExit("include/linux/mm.h missing insertion anchor for vm_flags_clear()")
        text = text[:insert_at] + helper + text[insert_at:]
    path.write_text(text)
PY
      echo "已为 ${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION} 2024-11 补充 vm_flags_clear helper"
    fi

}

case "${1:-}" in
    kernel-specific) kernel-specific ;;
    *)
        echo "用法: $0 {kernel-specific}" >&2
        exit 2
        ;;
esac
