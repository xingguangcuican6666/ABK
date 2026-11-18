#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/virtualization.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 克隆虚拟化支持补丁仓库 ----
clone() {
    set -euo pipefail

    patches_dir="$RUNNER_TEMP/virtualization-support-patches"
    rm -rf "$patches_dir"

    # 上游 Droidspaces-OSS 的 GKI kABI 补丁与配置清单按 tag 演进，跟随 HEAD 会让
    # 上游一次文档改动在同一天改变构建产物，因此默认钉到 v7.1.0。
    # 优先级: 环境变量 ABK_DROIDSPACES_REF > config/config 的 droidspaces= > 默认值。
    droidspaces_ref="${ABK_DROIDSPACES_REF:-}"
    config_file="$GITHUB_WORKSPACE/config/config"
    if [ -z "$droidspaces_ref" ] && [ -f "$config_file" ]; then
      droidspaces_ref="$(awk -F= '
        /^[[:space:]]*#/ { next }
        $1 == "droidspaces" {
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2)
          print $2
          exit
        }' "$config_file")"
    fi
    droidspaces_ref="${droidspaces_ref:-$ABK_DROIDSPACES_DEFAULT_REF}"
    echo "虚拟化补丁 ref: $droidspaces_ref"

    python3 "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py" clone \
      https://github.com/ravindu644/Droidspaces-OSS.git "$patches_dir" \
      --ref "$droidspaces_ref" --depth 1

    resolved_sha="$(git -C "$patches_dir" rev-parse HEAD)"
    resolved_tag="$(git -C "$patches_dir" describe --tags --exact-match "$resolved_sha" 2>/dev/null || echo "$droidspaces_ref")"

    gki_patches="$patches_dir/Documentation/resources/kernel-patches/GKI"
    [ -d "$gki_patches" ] || {
      echo "::error::虚拟化补丁目录缺失: Documentation/resources/kernel-patches/GKI (ref=$droidspaces_ref)"
      exit 1
    }

    echo "VIRTUALIZATION_PATCHES_REF=$resolved_tag" >> $GITHUB_ENV
    echo "VIRTUALIZATION_PATCHES_SHA=$resolved_sha" >> $GITHUB_ENV
    echo "VIRTUALIZATION_SUPPORT_PATCHES=$gki_patches" >> $GITHUB_ENV
    echo "已检出 Droidspaces-OSS@$resolved_tag ($resolved_sha)"

}

# ---- 集成虚拟化支持 ----
apply() {
    cd "$KERNEL_ROOT/common"
    set -euo pipefail
    KERNEL_VER="${ABK_INPUT_KERNEL_VERSION}"
    SLOT="${ABK_INPUT_VIRTUALIZATION_SUPPORT}"

    fail() {
      echo "::error::虚拟化支持: $*"
      exit 1
    }

    try_apply_patch() {
      local patch_file="$1"
      local name
      name="$(basename "$patch_file")"
      if patch -p1 --forward --dry-run < "$patch_file" >/dev/null 2>&1; then
        patch -p1 --forward < "$patch_file"
        echo "已应用虚拟化支持 patch: $name"
        return 0
      fi
      if patch -p1 --reverse --dry-run < "$patch_file" >/dev/null 2>&1; then
        echo "虚拟化支持 patch 已应用: $name"
        return 0
      fi
      return 1
    }

    apply_sysvipc_below_612_compat() {
      local sched="include/linux/sched.h"
      [ -f "$sched" ] || fail "缺少 $sched"
      if grep -qF "ANDROID_KABI_USE(${SLOT:0:1}, struct sysv_sem sysvsem);" "$sched"; then
        echo "SYSVIPC below-6.12 kABI fallback 已应用"
        return 0
      fi
      # 上游补丁按固定上下文书写，ACK 各分支的 KABI 预留区排布不同 (例如 5.10 已占用
      # 槽位 1/2，6.1 已占用 1/2/3)，因此 123 与 345 补丁在多数分支上会 reject。
      # 这里按所选槽位就地改写，使三种槽位在任何分支上都可用，并在槽位已被占用时
      # 明确报错，而不是静默产出破坏 kABI 的内核。
      if ! ABK_SYSVIPC_SLOT="$SLOT" ABK_SYSVIPC_SCHED="$sched" python3 - \
        > "${RUNNER_TEMP:-/tmp}/abk-sysvipc-slot.log" 2>&1 <<'PY'
import os
import re
import sys
from pathlib import Path

slot = os.environ["ABK_SYSVIPC_SLOT"]
sched = Path(os.environ["ABK_SYSVIPC_SCHED"])
if not re.fullmatch(r"[1-8]{3}", slot) or len(set(slot)) != 3:
    sys.exit(f"invalid slot: {slot}")
first, second, third = slot

text = sched.read_text()
occupied = sorted(set(re.findall(r"ANDROID_KABI_USE\((\d),", text)))
conflicts = sorted(set(occupied) & set(slot))
if conflicts:
    sys.exit(
        "slot {} is already taken by ANDROID_KABI_USE({}); pick another slot "
        "(678 fits every current ACK line)".format(slot, ",".join(conflicts))
    )

fields = (
    "#ifdef CONFIG_SYSVIPC\n"
    "\tstruct sysv_sem\t\t\tsysvsem;\n"
    "\tstruct sysv_shm\t\t\tsysvshm;\n"
    "#endif"
)
commented = (
    "#ifdef CONFIG_SYSVIPC\n"
    "\t// struct sysv_sem\t\t\tsysvsem;\n"
    "\t// struct sysv_shm\t\t\tsysvshm;\n"
    "#endif"
)
if fields not in text:
    sys.exit("CONFIG_SYSVIPC fields are not in their stock shape")

reserve = (
    "\tANDROID_KABI_RESERVE({});\n"
    "\tANDROID_KABI_RESERVE({});\n"
    "\tANDROID_KABI_RESERVE({});"
).format(first, second, third)
if reserve not in text:
    sys.exit(f"ANDROID_KABI_RESERVE({first},{second},{third}) block not found")
replacement = (
    "\n#ifdef CONFIG_SYSVIPC\n"
    "\tANDROID_KABI_USE({}, struct sysv_sem sysvsem);\n"
    "\t_ANDROID_KABI_REPLACE(ANDROID_KABI_RESERVE({}); ANDROID_KABI_RESERVE({}), "
    "struct sysv_shm sysvshm);\n"
    "#else\n"
    "{}\n"
    "#endif"
).format(first, second, third, reserve)

text = text.replace(fields, commented, 1).replace(reserve, replacement, 1)
sched.write_text(text)
PY
      then
        cat "${RUNNER_TEMP:-/tmp}/abk-sysvipc-slot.log"
        fail "SYSVIPC kABI fallback 注入失败 (slot=$SLOT)；该内核线的预留槽位可能已被占用，请改用 678"
      fi
      grep -qF '// struct sysv_sem' "$sched" || fail "SYSVIPC 原字段注释失败"
      grep -qF "ANDROID_KABI_USE(${SLOT:0:1}, struct sysv_sem sysvsem);" "$sched" || fail "SYSVIPC kABI fallback 注入失败"
      echo "已应用 SYSVIPC below-6.12 kABI fallback (slot=$SLOT)"
    }

    apply_mqueue_510_compat() {
      local user_h="include/linux/sched/user.h"
      [ -f "$user_h" ] || fail "缺少 $user_h"
      if grep -qF 'ANDROID_KABI_USE(1, unsigned long mq_bytes);' "$user_h"; then
        echo "POSIX_MQUEUE kABI fallback 已应用"
        return 0
      fi
      perl -0pi -e 's/#ifdef CONFIG_POSIX_MQUEUE\n\t\/\* protected by mq_lock\t\*\/\n\tunsigned long mq_bytes;\t\/\* How many bytes can be allocated to mqueue\? \*\/\n#endif/#ifdef CONFIG_POSIX_MQUEUE\n\t\/\* protected by mq_lock\t\*\/\n\t\/\/unsigned long mq_bytes;\t\/\* How many bytes can be allocated to mqueue? \*\/\n#endif/s' "$user_h"
      perl -0pi -e 's/\tANDROID_KABI_RESERVE\(1\);\n\tANDROID_KABI_RESERVE\(2\);\n\tANDROID_OEM_DATA_ARRAY\(1, 2\);/\n#if defined(CONFIG_POSIX_MQUEUE)\n\tANDROID_KABI_USE(1, unsigned long mq_bytes);\n\tANDROID_KABI_RESERVE(2);\n\tANDROID_OEM_DATA_ARRAY(1, 2);\n#else\n\tANDROID_KABI_RESERVE(1);\n\tANDROID_KABI_RESERVE(2);\n\tANDROID_OEM_DATA_ARRAY(1, 2);\n#endif/s' "$user_h"
      grep -qF 'ANDROID_KABI_USE(1, unsigned long mq_bytes);' "$user_h" || fail "POSIX_MQUEUE kABI fallback 注入失败"
      echo "已应用 POSIX_MQUEUE 5.10 kABI fallback"
    }

    apply_sysvipc_612_plus_compat() {
      local sched="include/linux/sched.h"
      [ -f "$sched" ] || fail "缺少 $sched"
      if grep -qF 'char __kabi_ignored_0;' "$sched"; then
        echo "SYSVIPC 6.12+ kABI fallback 已应用"
        return 0
      fi
      perl -0pi -e 's/(\n\tunsigned int\s+rt_priority;\n)/$1\n#ifdef CONFIG_SYSVIPC\n\tunion {\n\t\tchar __kabi_ignored_0;\n\t\tstruct sysv_sem\t\t\tsysvsem;\n\t}__attribute__((packed));\n\tunion {\n\t\tchar __kabi_ignored_1;\n\t\tstruct sysv_shm\t\t\tsysvshm;\n\t}__attribute__((packed));\n#endif\n/s or die "rt_priority anchor not found\n";' "$sched" || fail "SYSVIPC 6.12+ packed 字段注入失败"
      perl -0pi -e 's/#ifdef CONFIG_SYSVIPC\n\tstruct sysv_sem\s+sysvsem;\n\tstruct sysv_shm\s+sysvshm;\n#endif/#ifdef CONFIG_SYSVIPC\n\t\/\/ struct sysv_sem\t\t\tsysvsem;\n\t\/\/ struct sysv_shm\t\t\tsysvshm;\n#endif/s' "$sched"
      grep -qF 'char __kabi_ignored_0;' "$sched" || fail "SYSVIPC 6.12+ ignored 字段注入失败"
      grep -qF '// struct sysv_sem' "$sched" || fail "SYSVIPC 6.12+ 原字段注释失败"
      echo "已应用 SYSVIPC 6.12+ kABI fallback"
    }

    case "$KERNEL_VER" in
      6.12|6.18)
        PATCH_FILE="$VIRTUALIZATION_SUPPORT_PATCHES/kernel-6.12/001.GKI-6.12-or-above-fix_sysvipc_kabi.patch"
        if ! try_apply_patch "$PATCH_FILE"; then
          apply_sysvipc_612_plus_compat
        fi
        ;;
      5.10|5.15|6.1|6.6)
        case "$SLOT" in
          678|123|345) ;;
          on) SLOT=678 ;;
          *) fail "$KERNEL_VER 需要选择 678、123 或 345 槽位，当前为 $SLOT" ;;
        esac
        SLOT_NAME=$(echo "$SLOT" | sed 's/\(.\)/\1_/g; s/_$//')
        PATCH_FILE="$VIRTUALIZATION_SUPPORT_PATCHES/below-kernel-6.12/001.GKI-below-6.12-fix_sysvipc_kabi_${SLOT_NAME}.patch"
        # 上游补丁按固定上下文书写，只有 6_7_8 在全部 ACK 分支上都能干净应用；
        # reject 时按所选槽位就地改写，因此三种槽位都可用。
        if ! try_apply_patch "$PATCH_FILE"; then
          apply_sysvipc_below_612_compat
        fi
        ;;
      *)
        fail "未适配的内核版本 $KERNEL_VER"
        ;;
    esac

    # 5.10 及以下还需要 POSIX_MQUEUE 的 kABI 修复
    if [ "$KERNEL_VER" = "5.10" ]; then
      echo "应用虚拟化支持 POSIX_MQUEUE kABI 修复补丁 (5.10)..."
      POSIX_PATCH="$VIRTUALIZATION_SUPPORT_PATCHES/below-kernel-6.12/002.5.10_or_lower_use_android_abi_padding_for_posix_mqueue.patch"
      if ! try_apply_patch "$POSIX_PATCH"; then
        apply_mqueue_510_compat
      fi
    fi

    # Android 16 / 6.12 与 Android 17 / 6.18 的 rust_binder.ko 在启用 IPC_NS 后会引用
    # init_ipc_ns 与 put_ipc_ns，但当前 AOSP common 分支尚未导出这两个符号。
    # 这里补齐导出，避免 modpost 因 undefined symbol 失败。
    if [ "$KERNEL_VER" = "6.12" ] || [ "$KERNEL_VER" = "6.18" ]; then
      if [ -f "ipc/msgutil.c" ] && ! grep -qF 'EXPORT_SYMBOL(init_ipc_ns);' "ipc/msgutil.c"; then
        sed -i '/^struct msg_msgseg {/i EXPORT_SYMBOL(init_ipc_ns);' "ipc/msgutil.c"
        echo "已为 init_ipc_ns 补充符号导出"
      fi

      if [ -f "ipc/namespace.c" ] && ! grep -qF 'EXPORT_SYMBOL(put_ipc_ns);' "ipc/namespace.c"; then
        sed -i '/^static struct ns_common \*ipcns_get(/i EXPORT_SYMBOL(put_ipc_ns);' "ipc/namespace.c"
        echo "已为 put_ipc_ns 补充符号导出"
      fi
    fi

    echo "添加虚拟化支持内核配置..."
    # 按文档规则逐个处理: 已启用则跳过, "# not set" 则替换, 不存在则追加
    enable_config() {
      local cfg="$1"
      if grep -q "^${cfg}=y" "$DEFCONFIG"; then
        echo "  已启用: $cfg"
      elif grep -q "^# ${cfg} is not set" "$DEFCONFIG"; then
        sed -i "s/^# ${cfg} is not set$/${cfg}=y/" "$DEFCONFIG"
        echo "  已切换: $cfg"
      else
        echo "${cfg}=y" >> "$DEFCONFIG"
        echo "  已添加: $cfg"
      fi
    }

    config_defined() {
      local name="${1#CONFIG_}"
      # 一次性建立 Kconfig 符号索引后查表，避免为每个配置项重复 find 整棵内核树。
      # 索引写在 RUNNER_TEMP 而非内核树内，custom_git 模式下不会混入特性提交。
      if [ "${ABK_KCONFIG_INDEX_READY:-false}" != "true" ]; then
        ABK_KCONFIG_INDEX="${RUNNER_TEMP:-/tmp}/abk-kconfig-index-${KERNEL_VER}.txt"
        # 仅做 source 转发、不含 config 行的 Kconfig 会让 grep 返回 1，
        # 进而被 pipefail 判为失败，故对 grep 阶段单独放行。
        find . -type f -name 'Kconfig*' -print0 |
          { xargs -0 grep -hE '^[[:space:]]*(menuconfig|config)[[:space:]]+[A-Za-z0-9_]+' || true; } |
          sed -E 's/^[[:space:]]*(menuconfig|config)[[:space:]]+([A-Za-z0-9_]+).*$/\2/' |
          sort -u > "$ABK_KCONFIG_INDEX"
        [ -s "$ABK_KCONFIG_INDEX" ] || fail "无法建立 Kconfig 符号索引，虚拟化配置校验不可用"
        ABK_KCONFIG_INDEX_READY=true
      fi
      grep -qxF "$name" "$ABK_KCONFIG_INDEX"
    }

    enable_required_config() {
      local cfg="$1"
      if config_defined "$cfg"; then
        enable_config "$cfg"
      else
        fail "当前内核未定义必要配置: $cfg"
      fi
    }

    enable_config_if_defined() {
      local cfg="$1"
      if config_defined "$cfg"; then
        enable_config "$cfg"
      else
        echo "  当前内核未定义: $cfg，跳过"
      fi
    }

    # 必要配置
    enable_required_config CONFIG_SYSVIPC
    enable_required_config CONFIG_POSIX_MQUEUE
    enable_required_config CONFIG_IPC_NS
    enable_required_config CONFIG_PID_NS
    enable_required_config CONFIG_DEVTMPFS

    # 上游 v7.1.0 GKI 清单新增: 容器内 Docker 的 unsafe procfs 修复
    enable_config_if_defined CONFIG_USER_NS

    # 上游 v7.1.0 GKI 清单新增: NAT66 (缺失时 NAT 容器只有 IPv4)
    enable_config_if_defined CONFIG_IP6_NF_NAT
    enable_config_if_defined CONFIG_IP6_NF_TARGET_MASQUERADE

    # 上游 v7.1.0 GKI 清单新增: macvlan 模式。上游已在 5.15 GKI 树上验证
    # 8423 个导出符号无一变化，驱动自包含，属 kABI 安全项。
    enable_config_if_defined CONFIG_MACVLAN

    # 可选: 网络相关配置 (Docker/NAT 模式)
    enable_config_if_defined CONFIG_NETFILTER_XT_MATCH_ADDRTYPE
    enable_config_if_defined CONFIG_NETFILTER_XT_TARGET_LOG
    enable_config_if_defined CONFIG_NETFILTER_XT_MATCH_RECENT
    enable_config_if_defined CONFIG_IP_SET
    enable_config_if_defined CONFIG_IP_SET_HASH_IP
    enable_config_if_defined CONFIG_IP_SET_HASH_NET
    enable_config_if_defined CONFIG_NETFILTER_XT_SET

    # REJECT 目标在不同内核版本上的符号名可能不同，按实际存在的配置启用
    enable_config_if_defined CONFIG_NETFILTER_XT_TARGET_REJECT
    enable_config_if_defined CONFIG_IP_NF_TARGET_REJECT

    # NixOS 容器依赖 tmpfs 的 xattr 与 POSIX ACL。主流程的「配置内核选项」步骤
    # 也会写入这两项，这里重复写入是为了让虚拟化支持自身可独立生效 (幂等)。
    enable_config_if_defined CONFIG_TMPFS_XATTR
    enable_config_if_defined CONFIG_TMPFS_POSIX_ACL

    # 上游 v7.1.0 明确要求 GKI 上不得开启资源限制相关项:
    # CONFIG_CFS_BANDWIDTH 与 CONFIG_CGROUP_PIDS 会改动调度器与 cgroup 结构体大小，
    # 在 5.15 GKI 树上改变了约四千个导出符号的校验和，且没有任何 kABI 补丁可覆盖，
    # 原厂厂商模块会拒绝加载导致 bootloop。ABK 只产出 boot.img，无法同时重刷
    # vendor_boot / vendor_dlkm / system_dlkm，因此这两项一律不由本步骤开启。
    # (android16-6.12 与 android17-6.18 的 stock gki_defconfig 自带这两项，属上游基线，保持原样。)
    [ -f "$DEFCONFIG.orig" ] || fail "缺少 defconfig 基线 $DEFCONFIG.orig，无法校验虚拟化配置是否破坏 kABI"
    for forbidden in CONFIG_CFS_BANDWIDTH CONFIG_CGROUP_PIDS; do
      if ! grep -qxF "${forbidden}=y" "$DEFCONFIG.orig" && grep -qxF "${forbidden}=y" "$DEFCONFIG"; then
        fail "虚拟化支持拒绝开启 ${forbidden}: GKI 上无 kABI 补丁覆盖，会导致厂商模块拒绝加载并 bootloop"
      fi
    done

    echo "虚拟化支持集成完成"

}

case "${1:-}" in
    clone) clone ;;
    apply) apply ;;
    *)
        echo "用法: $0 {clone|apply}" >&2
        exit 2
        ;;
esac
