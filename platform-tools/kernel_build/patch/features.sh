#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/features.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 应用 Networking 增强 (IPSet + BBR) ----
networking() {
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

    echo " 添加IP SET & IPv6_NAT 支持"
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
    if [ "${ABK_INPUT_KERNEL_VERSION}" = "5.10" ]; then
      echo "应用 5.10 特殊配置 "
      sed -i 's/CONFIG_IP_SET_MAX/65534/g' net/netfilter/ipset/ip_set_core.c
    fi

    echo " 添加 TCP Congestion Control (BBR) 支持"
    # 应用 BBRv3 补丁仅有6.1版本
    if [ "${ABK_INPUT_KERNEL_VERSION}" = "6.1" && false ]; then
      echo "应用 BBRv3 补丁"
      PATCH_FILE="$KERNEL_PATCHES/bbr/bbrv3-${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.patch"
      echo "→ 应用补丁: bbrv3-${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.patch"
      patch -p1 --forward < "$PATCH_FILE" || true
    fi

    ensure_defconfig_value CONFIG_TCP_CONG_ADVANCED y
    if [ -f net/ipv4/Kconfig ] && grep -q '^config TCP_CONG_BIC$' net/ipv4/Kconfig; then
      ensure_defconfig_value CONFIG_TCP_CONG_BIC y
    fi
    ensure_defconfig_value CONFIG_TCP_CONG_BBR y
    ensure_defconfig_value CONFIG_TCP_CONG_CUBIC y
    ensure_defconfig_value CONFIG_TCP_CONG_WESTWOOD y
    ensure_defconfig_value CONFIG_TCP_CONG_HTCP y
    ensure_defconfig_value CONFIG_DEFAULT_BBR y
    ensure_defconfig_value CONFIG_DEFAULT_TCP_CONG '"bbr"'
    ensure_defconfig_value CONFIG_NET_SCH_FQ y
    ensure_defconfig_value CONFIG_NET_SCH_FQ_CODEL y

    echo "✓ Networking配置完成"

}

# ---- 添加 BBG 防格机补丁 ----
bbg() {
    cd "$KERNEL_ROOT"
    wget -O- https://github.com/vc-teahouse/Baseband-guard/raw/main/setup.sh | bash
    BBG_MAKEFILE="Baseband-guard/Makefile"
    if { { [ "${ABK_INPUT_ANDROID_VERSION}" = "android12" ] && \
           [ "${ABK_INPUT_KERNEL_VERSION}" = "5.10" ]; } || \
         { [ "${ABK_INPUT_ANDROID_VERSION}" = "android13" ] && \
           [ "${ABK_INPUT_KERNEL_VERSION}" = "5.15" ]; }; } && \
       [ -f "$BBG_MAKEFILE" ]; then
      echo "应用 BBG legacy host header 生成兼容修复: ${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}"
      python3 - "$BBG_MAKEFILE" "common/security/selinux" <<'PY'
import sys
import re
from pathlib import Path

makefile = Path(sys.argv[1])
selinux_dir = Path(sys.argv[2])
include_dir = selinux_dir / "include"
classmap_h = include_dir / "classmap.h"
initial_sid_h = include_dir / "initial_sid_to_string.h"

def join_continuations(text):
    lines = []
    buf = ""
    for raw in text.splitlines():
        line = raw.rstrip()
        if line.endswith("\\"):
            buf += line[:-1] + " "
        else:
            lines.append(buf + line)
            buf = ""
    if buf:
        lines.append(buf)
    return "\n".join(lines)

def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//.*", "", text)
    return text

classmap_text = strip_comments(join_continuations(classmap_h.read_text(encoding="utf-8")))
initial_sid_text = strip_comments(join_continuations(initial_sid_h.read_text(encoding="utf-8")))

macros = {
    name: body.strip()
    for name, body in re.findall(r"^\s*#define\s+([A-Za-z_][A-Za-z0-9_]*)\s+(.+)$", classmap_text, re.M)
}

def expand_list(expr, seen=()):
    values = []
    for string_value, ident in re.findall(r'"([^"]+)"|([A-Za-z_][A-Za-z0-9_]*)', expr):
        if string_value:
            values.append(string_value.upper())
        elif ident == "NULL":
            continue
        elif ident in macros and ident not in seen:
            values.extend(expand_list(macros[ident], seen + (ident,)))
    return values

secclasses = []
body_match = re.search(r"secclass_map\s*\[\]\s*=\s*\{(.*)\};", classmap_text, re.S)
if not body_match:
    raise SystemExit("secclass_map not found")

for class_name, perms_expr in re.findall(r'\{\s*"([^"]+)"\s*,\s*\{(.*?)\}\s*\}', body_match.group(1), re.S):
    secclasses.append((class_name.upper(), expand_list(perms_expr)))

sid_match = re.search(r"initial_sid_to_string\s*\[\]\s*=\s*\{(.*)\};", initial_sid_text, re.S)
if not sid_match:
    raise SystemExit("initial_sid_to_string not found")

initial_sids = []
for string_value, null_value in re.findall(r'"([^"]+)"|(NULL)', sid_match.group(1)):
    initial_sids.append(string_value.upper() if string_value else None)

flask_lines = [
    "/* This file is automatically generated. Do not edit. */",
    "#ifndef _SELINUX_FLASK_H_",
    "#define _SELINUX_FLASK_H_",
    "",
]
for idx, (class_name, _) in enumerate(secclasses, 1):
    flask_lines.append(f"#define SECCLASS_{class_name:<39} {idx:2d}")

flask_lines.append("")
for idx, sid_name in enumerate(initial_sids):
    if idx and sid_name:
        flask_lines.append(f"#define SECINITSID_{sid_name:<39} {idx:2d}")

flask_lines.extend([
    f"\n#define SECINITSID_NUM {len(initial_sids) - 1}",
    "",
    "static inline bool security_is_socket_class(u16 kern_tclass)",
    "{",
    "\tbool sock = false;",
    "",
    "\tswitch (kern_tclass) {",
])
for class_name, _ in secclasses:
    if class_name.endswith("SOCKET"):
        flask_lines.append(f"\tcase SECCLASS_{class_name}:")
flask_lines.extend([
    "\t\tsock = true;",
    "\t\tbreak;",
    "\tdefault:",
    "\t\tbreak;",
    "\t}",
    "",
    "\treturn sock;",
    "}",
    "",
    "#endif",
    "",
])

av_lines = [
    "/* This file is automatically generated. Do not edit. */",
    "#ifndef _SELINUX_AV_PERMISSIONS_H_",
    "#define _SELINUX_AV_PERMISSIONS_H_",
    "",
]
for class_name, perms in secclasses:
    for bit, perm in enumerate(perms):
        if bit >= 32:
            raise SystemExit(f"too many permissions for {class_name}")
        pad = " " * max(1, 40 - len(class_name) - len(perm))
        av_lines.append(f"#define {class_name}__{perm}{pad}0x{1 << bit:08x}U")
av_lines.extend(["", "#endif", ""])

(selinux_dir / "flask.h").write_text("\n".join(flask_lines), encoding="utf-8")
(selinux_dir / "av_permissions.h").write_text("\n".join(av_lines), encoding="utf-8")

text = makefile.read_text(encoding="utf-8")
text = text.replace(
    "ifneq ($(wildcard $(objtree)/security/selinux/flask.h),)",
    "ifneq ($(wildcard $(objtree)/security/selinux/flask.h $(srctree)/security/selinux/flask.h),)",
    1,
)

makefile.write_text(text, encoding="utf-8")
PY
    fi
    echo "CONFIG_BBG=y" >> common/arch/arm64/configs/gki_defconfig
    sed -i '/^config LSM$/,/^help$/{ /^[[:space:]]*default/ { /baseband_guard/! s/selinux/selinux,baseband_guard/ } }' common/security/Kconfig

}

# ---- 添加 DDK 防格机 LSM ----
ddk() {
    cd "$KERNEL_ROOT"
    echo "应用 Xingguang DDK patch 栈并启用内建 LSM"
    bash "$ZZH_PATCHES/ddk/setup.sh"
    test -f common/include/linux/xingguang_ddk.h
    grep -q '^CONFIG_XINGGUANG_DDK=y$' "$DEFCONFIG" || echo "CONFIG_XINGGUANG_DDK=y" >> "$DEFCONFIG"

}

# ---- 应用 Re-Kernel ----
rekernel() {
    cd "$KERNEL_ROOT"
    set -euo pipefail
    echo "Integrating Re-Kernel..."
    TMP_REKERNEL=/tmp/rekernel
    rm -rf "$TMP_REKERNEL"
    git clone --depth 1 https://github.com/Sakion-Team/Re-Kernel.git "$TMP_REKERNEL"

    REKERNEL_SRC="$TMP_REKERNEL/LKM-Source"
    if [ ! -d "$REKERNEL_SRC" ]; then
      echo "::error::Re-Kernel upstream 缺少 LKM-Source 目录"
      exit 1
    fi

    test -f "$REKERNEL_SRC/Makefile"
    test -f "$REKERNEL_SRC/Kconfig"
    test -f "$REKERNEL_SRC/rekernel_main.c"
    test -f "$REKERNEL_SRC/rekernel.h"

    # 准备驱动源码（上游已改为多文件结构）
    rm -rf common/drivers/rekernel
    mkdir -p common/drivers/rekernel
    cp "$REKERNEL_SRC"/*.c common/drivers/rekernel/
    cp "$REKERNEL_SRC"/*.h common/drivers/rekernel/
    cp "$REKERNEL_SRC/Makefile" common/drivers/rekernel/
    cp "$REKERNEL_SRC/Kconfig" common/drivers/rekernel/

    # 适配上游 LKM Makefile 到 in-tree 构建
    sed -i 's/^obj-m[[:space:]]*:=/obj-$(CONFIG_REKERNEL) +=/' common/drivers/rekernel/Makefile

    # 适配上游 Kconfig 到 ABK 当前的 in-tree 默认启用方式
    sed -i '/depends on MODULES/d' common/drivers/rekernel/Kconfig
    sed -i '0,/bool "Re:Kernel module"/s//bool "Re:Kernel support (GKI Vendor Hooks)"/' common/drivers/rekernel/Kconfig
    sed -i '0,/default n/s//\tdefault y/' common/drivers/rekernel/Kconfig

    # 挂载到驱动树
    if ! grep -qF 'source "drivers/rekernel/Kconfig"' common/drivers/Kconfig; then
      sed -i '/^endmenu$/i source "drivers/rekernel/Kconfig"' common/drivers/Kconfig
    fi
    if ! grep -qF 'obj-$(CONFIG_REKERNEL) += rekernel/' common/drivers/Makefile; then
      echo 'obj-$(CONFIG_REKERNEL) += rekernel/' >> common/drivers/Makefile
    fi

    # 修正头文件包含路径（适配 in-tree 编译）
    if [ -f common/drivers/rekernel/rekernel_binder.c ]; then
      sed -i 's|#include <../android/binder_internal.h>|#include "../../drivers/android/binder_internal.h"|g' common/drivers/rekernel/rekernel_binder.c
      grep -qF '#include <linux/seq_file.h>' common/drivers/rekernel/rekernel_binder.c || \
        sed -i '/#include <trace\/hooks\/binder.h>/a #include <linux/seq_file.h>' common/drivers/rekernel/rekernel_binder.c
    fi

    # 配置 defconfig（幂等）
    grep -q '^CONFIG_REKERNEL=y$' "$DEFCONFIG" || echo "CONFIG_REKERNEL=y" >> "$DEFCONFIG"
    grep -q '^CONFIG_REKERNEL_NETWORK=y$' "$DEFCONFIG" || echo "CONFIG_REKERNEL_NETWORK=y" >> "$DEFCONFIG"

}

case "${1:-}" in
    networking) networking ;;
    bbg) bbg ;;
    ddk) ddk ;;
    rekernel) rekernel ;;
    *)
        echo "用法: $0 {networking|bbg|ddk|rekernel}" >&2
        exit 2
        ;;
esac
