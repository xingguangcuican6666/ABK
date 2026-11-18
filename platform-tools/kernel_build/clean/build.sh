#!/usr/bin/env bash
# 干净 GKI 内核：环境、校验、工具链、源码同步、编译与打包。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/clean/build.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 配置内核名称 ----
kernel-name() {
    cd "$KERNEL_ROOT"
    if [ -f "build/build.sh" ]; then
      sed -i 's/-dirty//' ./common/scripts/setlocalversion
    else
    python3 - <<'PY'
from pathlib import Path
import re

path = Path("common/BUILD.bazel")
if not path.exists():
    print("未找到 common/BUILD.bazel，跳过属性清理")
else:
    text = path.read_text(encoding="utf-8")
    removed = []

    kmi_multi = re.compile(
        r'^[ \t]*(?:"kmi_symbol_list_strict_mode"\s*:|kmi_symbol_list_strict_mode\s*=)\s*select\(\{.*?\}\),?[ \t]*\n?',
        re.M | re.S,
    )
    kmi_single = re.compile(
        r'^[ \t]*(?:"kmi_symbol_list_strict_mode"\s*:|kmi_symbol_list_strict_mode\s*=)[^\n]*$',
        re.M,
    )
    text, count = kmi_multi.subn("", text)
    if count:
        removed.append("kmi_symbol_list_strict_mode(select)")
    if kmi_single.search(text):
        if re.search(r'^[ \t]*(?:"kmi_symbol_list_strict_mode"\s*:|kmi_symbol_list_strict_mode\s*=)[^\n]*select\(', text, re.M):
            raise SystemExit("BUILD.bazel: kmi_symbol_list_strict_mode 多行形式无法安全移除")
        text, count = kmi_single.subn("", text)
        if count:
            removed.append("kmi_symbol_list_strict_mode")
    if re.search(r'^[ \t]*(?:"kmi_symbol_list_strict_mode"\s*:|kmi_symbol_list_strict_mode\s*=)', text, re.M):
        raise SystemExit("BUILD.bazel: kmi_symbol_list_strict_mode 形式无法安全移除")

    pe_multi = re.compile(
        r'^[ \t]*"protected_exports_list"\s*:\s*select\(\{.*?\}\),?[ \t]*\n?',
        re.M | re.S,
    )
    pe_single = re.compile(
        r'^[ \t]*"protected_exports_list"\s*:[^\n]*$',
        re.M,
    )
    text, count = pe_multi.subn("", text)
    if count:
        removed.append("protected_exports_list(select)")
    if pe_single.search(text):
        if re.search(r'^[ \t]*"protected_exports_list"\s*:[^\n]*select\(', text, re.M):
            raise SystemExit("BUILD.bazel: protected_exports_list 多行形式无法安全移除")
        text, count = pe_single.subn("", text)
        if count:
            removed.append("protected_exports_list")
    if re.search(r'^[ \t]*"protected_exports_list"\s*:', text, re.M):
        raise SystemExit("BUILD.bazel: protected_exports_list 形式无法安全移除")

    if removed:
        path.write_text(text, encoding="utf-8")
    print("BUILD.bazel 属性清理:", ", ".join(removed) if removed else "无需修改")
PY
      rm -rf ./common/android/abi_gki_protected_exports_*
      sed -i "/stable_scmversion_cmd/s/-maybe-dirty//g" ./build/kernel/kleaf/impl/stamp.bzl
    fi

    VERSION_INPUT=$(echo "${ABK_INPUT_VERSION}" | tr -d '[:space:]')
    if [ -n "$VERSION_INPUT" ]; then
      CLEAN_VERSION=$(echo "$VERSION_INPUT" | sed -E 's/^[0-9]+\.[0-9]+\.[0-9]+//')
      perl -i -0777 -pe 's/(.*)echo "\$\{KERNELVERSION\}\$\{file_localversion\}\$\{config_localversion\}\$\{LOCALVERSION\}\$\{scm_version\}"/$1echo "\$\{KERNELVERSION\}'"${CLEAN_VERSION}"'"/s' ./common/scripts/setlocalversion 2>/dev/null || true
      sed -i "\$s|echo \"\$res\"|echo \"${CLEAN_VERSION}\"|" ./common/scripts/setlocalversion 2>/dev/null || true
      sed -i '/^CONFIG_LOCALVERSION=/ s/="\([^"]*\)"/="'"$CLEAN_VERSION"'"/' ./common/arch/arm64/configs/gki_defconfig
    elif [ ! -f "build/build.sh" ]; then
      cd ./common
      BID="ab$((RANDOM % 90000000 + 10000000))"
      GHASH=$(git rev-parse --verify HEAD | cut -c1-13)
      case "${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}" in
        "android14-6.1")  KMI_TAG="android14-11" ;;
        "android15-6.6")  KMI_TAG="android15-8" ;;
        "android16-6.12") KMI_TAG="android16-5" ;;
        "android17-6.18") KMI_TAG="android17-5" ;;
        *) KMI_TAG="${ABK_INPUT_ANDROID_VERSION}" ;;
      esac

      if [ "${ABK_INPUT_KERNEL_VERSION}" = "6.1" ]; then
        KMI_LOCAL="-${KMI_TAG}-g${GHASH}-${BID}-4k"
        sed -i "\$s|echo \"\$res\"|echo \"${KMI_LOCAL}\"|" ./scripts/setlocalversion 2>/dev/null || true
        sed -i "/^CONFIG_LOCALVERSION=/ s/=\"[^\"]*\"/=\"${KMI_LOCAL}\"/" ./arch/arm64/configs/gki_defconfig 2>/dev/null || true
      else
        SUFFIX="-${KMI_TAG}-g${GHASH}-${BID}"
        perl -i -0777 -pe 's/(.*)echo "\$\{KERNELVERSION\}\$\{file_localversion\}\$\{config_localversion\}\$\{LOCALVERSION\}\$\{scm_version\}"/$1echo "\$\{KERNELVERSION\}'"${SUFFIX}"'\$\{config_localversion\}"/s' ./scripts/setlocalversion 2>/dev/null || true
      fi
    fi

}

# ---- 设置自定义构建时间 ----
build-time() {
    INPUT_TIME="${ABK_INPUT_BUILD_TIME}"
    if [[ -n "$INPUT_TIME" && "$INPUT_TIME" != "N" && "$INPUT_TIME" != "n" ]]; then
      DATESTR="$INPUT_TIME"
    else
      DATESTR="$(TZ='UTC' date +'%a %b %d %T %Z %Y')"
    fi

    echo "使用构建时间: $DATESTR"
    echo "KBUILD_BUILD_TIMESTAMP=$DATESTR" >> "$GITHUB_ENV"
    echo "KBUILD_BUILD_VERSION=1" >> "$GITHUB_ENV"

    # 统一处理 mkcompile_h 补丁
    f="$KERNEL_ROOT/common/scripts/mkcompile_h"
    if [ -f "$f" ]; then
      if [[ "${ABK_INPUT_KERNEL_VERSION}" == "5.10" || "${ABK_INPUT_KERNEL_VERSION}" == "5.15" ]]; then
        echo "应用 5.x 经典时间戳补丁: $f"
        perl -pi -e "s{UTS_VERSION=\"\\\$\(echo \\\$UTS_VERSION \\\$CONFIG_FLAGS \\\$TIMESTAMP \\| cut -b -\\\$UTS_LEN\)\"}{UTS_VERSION=\"#1 SMP PREEMPT $DATESTR\"}" "$f"
      else
        echo "应用 6.x mkcompile_h 补丁: $f"
        if grep -q 'UTS_VERSION=' "$f"; then
          perl -pi -e "s{UTS_VERSION=\"\\\$\\\(.*?\\\)\"}{UTS_VERSION=\"#1 SMP PREEMPT $DATESTR\"}" "$f"
        else
          perl -0777 -pi -e "s{cat <<EOF}{cat <<EOF\n#undef UTS_VERSION\n#define UTS_VERSION \"#1 SMP PREEMPT $DATESTR\" } unless /UTS_VERSION/" "$f"
    fi
  fi
fi

}

# ---- 编译内核 ----
compile() {
    echo "当前 KSU 最新提交日期: ${KSU_LATEST_COMMIT_DATE}"
    echo "当前 SUSFS 最新提交日期: ${SUSFS_LATEST_COMMIT_DATE}"
    echo "当前虚拟化补丁版本: ${VIRTUALIZATION_PATCHES_REF:-未启用}"
    set -ex
    cd "$KERNEL_ROOT"

    CURRENT_SUB="${ABK_INPUT_SUB_LEVEL}"
    if [[ ! "$CURRENT_SUB" =~ ^[0-9]+$ ]]; then
      CURRENT_SUB=99999
    fi

    if [[ "${ABK_INPUT_ANDROID_VERSION}" == "android13" \
      && "${ABK_INPUT_KERNEL_VERSION}" == "5.15" \
      && "$CURRENT_SUB" -ge 41 && "$CURRENT_SUB" -le 137 ]]; then
      case "${ABK_INPUT_KSU_VARIANT}" in
        "Official"|"BakaSU")
          echo "编译前兜底修复 ${ABK_INPUT_KSU_VARIANT} 5.15 kallsyms symbol_resolver"
          python3 - <<'PY'
from pathlib import Path
from textwrap import dedent

VARIANT = "${ABK_INPUT_KSU_VARIANT}"
SUBLEVEL = "${ABK_INPUT_SUB_LEVEL}"
TARGETED_BAKASU_SUBLEVELS = {"41", "74", "78", "94", "104", "119", "123", "137"}
TARGETED_BAKASU = VARIANT == "BakaSU" and SUBLEVEL in TARGETED_BAKASU_SUBLEVELS
if TARGETED_BAKASU:
    MARKER = "ABK: Android 13 5.15.{41,74,78,94,104,119,123,137} BakaSU kallsyms lookup rewrite"
    LEGACY_MARKER = "ABK: Android 13 5.15.{41,74,78,94,104,119,123,137} BakaSU kallsyms weak reference"
else:
    MARKER = "ABK: Android 13 5.15.41-5.15.137 Official/BakaSU kallsyms lookup rewrite"
    LEGACY_MARKER = "ABK: Android 13 5.15.41-5.15.137 Official/BakaSU kallsyms weak reference"

BASE_TARGET_SOURCE = dedent(f"""\
// SPDX-License-Identifier: GPL-2.0-only
/*
 * {MARKER}.
 *
 * The affected GKI 5.15 builds can fail while linking .tmp_vmlinux.btf
 * when KernelSU keeps a static kallsyms_on_each_symbol callback path
 * under clang ThinLTO. Rewrite symbol resolution to the simpler
 * kallsyms_lookup_name path used by modern KernelSU/BakaSU trees so
 * the built-in code no longer emits forbidden PLT/GOT entries.
 */
#include <linux/kernel.h>
#include <linux/string.h>
#include <linux/kallsyms.h>

#include "infra/symbol_resolver.h"

static const char cfi_suffix[] = ".cfi_jt";

void *ksu_lookup_symbol(const char *symbol_name)
{{
#ifdef CONFIG_KALLSYMS
    unsigned long addr;
    char cfi_name[KSYM_NAME_LEN];

    if (!symbol_name || !symbol_name[0])
        return NULL;

    addr = kallsyms_lookup_name(symbol_name);
    if (addr)
        return (void *)addr;

    if (scnprintf(cfi_name, sizeof(cfi_name), "%s%s", symbol_name, cfi_suffix) >= sizeof(cfi_name))
        return NULL;

    addr = kallsyms_lookup_name(cfi_name);
    return addr ? (void *)addr : NULL;
#else
    return NULL;
#endif
}}
""")

TARGETED_BAKASU_SOURCE = dedent(f"""\
// SPDX-License-Identifier: GPL-2.0-only
/*
 * {MARKER}.
 *
 * The affected BakaSU GKI 5.15 builds reference newer symbol
 * resolver entry points from LSM hook code. Keep the existing direct
 * kallsyms_lookup_name path and provide the missing entry points only
 * for the known failing Android 13 / 5.15 sublevels.
 */
#include <linux/kernel.h>
#include <linux/string.h>
#include <linux/kallsyms.h>

#include "infra/symbol_resolver.h"

static const char cfi_suffix[] = ".cfi_jt";

static void *ksu_lookup_symbol_impl(const char *symbol_name)
{{
#ifdef CONFIG_KALLSYMS
    unsigned long addr;
    char cfi_name[KSYM_NAME_LEN];

    if (!symbol_name || !symbol_name[0])
        return NULL;

    addr = kallsyms_lookup_name(symbol_name);
    if (addr)
        return (void *)addr;

    if (scnprintf(cfi_name, sizeof(cfi_name), "%s%s", symbol_name, cfi_suffix) >= sizeof(cfi_name))
        return NULL;

    addr = kallsyms_lookup_name(cfi_name);
    return addr ? (void *)addr : NULL;
#else
    return NULL;
#endif
}}

void *ksu_resolve_symbol_for_functable_hook(const char *symbol_name)
{{
    return ksu_lookup_symbol_impl(symbol_name);
}}

unsigned long find_kernel_symbol_exact(const char *symbol_name)
{{
    return (unsigned long)ksu_lookup_symbol_impl(symbol_name);
}}

void ksu_init_symbol_resolver(void)
{{
}}

void *ksu_lookup_symbol(const char *symbol_name)
{{
    return ksu_lookup_symbol_impl(symbol_name);
}}
""")

TARGET_SOURCE = TARGETED_BAKASU_SOURCE if TARGETED_BAKASU else BASE_TARGET_SOURCE

candidates = []
seen = set()
for root in (Path("common/drivers/kernelsu"), Path("drivers/kernelsu")):
    path = root / "infra" / "symbol_resolver.c"
    if path.exists():
        resolved = path.resolve()
        if resolved not in seen:
            candidates.append(path)
            seen.add(resolved)

if not candidates:
    raise SystemExit(f"{VARIANT} 未找到最终编译目录 infra/symbol_resolver.c，无法应用 5.15 kallsyms 兼容补丁")

changed = False
matched = False
for path in candidates:
    print(f"检查 5.15 kallsyms 编译目录: {path}")
    text = path.read_text()
    if not any(token in text for token in (
        "ksu_lookup_symbol(",
        "kallsyms_on_each_symbol(",
        "kallsyms_lookup_name(",
        MARKER,
        LEGACY_MARKER,
    )):
        print(f"{path} 看起来不是预期的 KernelSU symbol_resolver，跳过")
        continue

    matched = True
    needs_rewrite = (
        MARKER not in text
        or LEGACY_MARKER in text
        or "kallsyms_on_each_symbol(" in text
        or "__attribute__((weak))" in text
        or "void *ksu_lookup_symbol(const char *symbol_name)" not in text
        or "kallsyms_lookup_name(symbol_name)" not in text
    )

    if needs_rewrite:
        path.write_text(TARGET_SOURCE)
        changed = True
        print(f"已将 {path} 重写为 {VARIANT} 5.15 kallsyms 直接查表实现")
    else:
        print(f"{path} 已使用无 PLT/GOT 的 kallsyms_lookup_name 实现")

    final_text = path.read_text()
    if MARKER not in final_text:
        raise SystemExit(f"{path} 缺少 5.15 kallsyms rewrite marker")
    if "kallsyms_on_each_symbol(" in final_text:
        raise SystemExit(f"{path} 仍包含 kallsyms_on_each_symbol 直接调用")
    if "__attribute__((weak))" in final_text:
        raise SystemExit(f"{path} 仍包含 weak kallsyms extern")
    if TARGETED_BAKASU:
        if "ksu_resolve_symbol_for_functable_hook(" not in final_text:
            raise SystemExit(f"{path} 缺少 ksu_resolve_symbol_for_functable_hook() 目标实现")
        if "find_kernel_symbol_exact(" not in final_text:
            raise SystemExit(f"{path} 缺少 find_kernel_symbol_exact() 目标实现")
        if "ksu_init_symbol_resolver(" not in final_text:
            raise SystemExit(f"{path} 缺少 ksu_init_symbol_resolver() 目标实现")
    if "void *ksu_lookup_symbol(const char *symbol_name)" not in final_text:
        raise SystemExit(f"{path} 缺少 ksu_lookup_symbol() 目标实现")
    if "kallsyms_lookup_name(symbol_name)" not in final_text:
        raise SystemExit(f"{path} 未切换到 kallsyms_lookup_name 直接查表实现")

    print(f"已验证 {path}: 无 kallsyms_on_each_symbol 直接调用，且无 weak extern")

if not matched:
    raise SystemExit(f"{VARIANT} 未找到可修补的 KernelSU symbol_resolver 实现")

if not changed:
    print(f"{VARIANT} 5.15 kallsyms rewrite 已经是目标状态，无需修改。")
PY
          ;;
      esac
    fi

    sed -i 's/BUILD_SYSTEM_DLKM=1/BUILD_SYSTEM_DLKM=0/' ./common/build.config.gki.aarch64
    sed -i '/MODULES_ORDER=android\/gki_aarch64_modules/d' ./common/build.config.gki.aarch64
    sed -i '/KMI_SYMBOL_LIST_STRICT_MODE/d' ./common/build.config.gki.aarch64

    if [ -f "build/build.sh" ]; then
      LTO=thin BUILD_CONFIG=common/build.config.gki.aarch64 build/build.sh CC="/usr/bin/ccache clang" || exit 1
      strings "out/${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}/dist/Image" | grep 'Linux version'
    else
      # 提取 gki_defconfig 修改到 fragment，避免 bazel trim 检查失败
      FRAG="common/arch/arm64/configs/ksu.fragment"
      diff "$DEFCONFIG.orig" "$DEFCONFIG" | grep '^>' | sed 's/^> //; s/^[[:space:]]*//' > "$FRAG" || true
      cp "$DEFCONFIG.orig" "$DEFCONFIG"
      echo "=== KSU Fragment 内容 ==="
      cat "$FRAG"
      echo "========================="
      FRAG_FLAG=""
      if [ -s "$FRAG" ]; then
        FRAG_FLAG="--defconfig_fragment=//common:arch/arm64/configs/ksu.fragment"
      fi
      LTO_FLAG="--lto=thin"
      if [ "${EFFECTIVE_MODERN_GKI:-false}" = "true" ]; then
        LTO_FLAG="--lto=none"
      fi
      BAZEL_DISK_CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/bazel-disk"
      mkdir -p "$BAZEL_DISK_CACHE"
      tools/bazel build --disk_cache="$BAZEL_DISK_CACHE" --config=fast $LTO_FLAG $FRAG_FLAG //common:kernel_aarch64_dist || exit 1
      strings ./bazel-bin/common/kernel_aarch64/Image | grep 'Linux version'
    fi

    echo "当前 KSU 最新提交日期: ${KSU_LATEST_COMMIT_DATE}"
    echo "当前 SUSFS 最新提交日期: ${SUSFS_LATEST_COMMIT_DATE}"
    echo "当前虚拟化补丁版本: ${VIRTUALIZATION_PATCHES_REF:-未启用}"
    echo "如果日期不同或相差过远则补丁失效、编译失败"


}

case "${1:-}" in
    kernel-name) kernel-name ;;
    build-time) build-time ;;
    compile) compile ;;
    *)
        echo "用法: $0 {kernel-name|build-time|compile}" >&2
        exit 2
        ;;
esac
