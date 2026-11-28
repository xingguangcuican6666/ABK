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
    echo "当前虚拟化补丁版本: ${VIRTUALIZATION_PATCHES_REF:-未启用}"
    set -ex
    cd "$KERNEL_ROOT"

    # 旧 Official/BakaSU 在 android13-5.15 上的 kallsyms 重写已随变体一并移除；
    # NyaSU 的 symbol_resolver.c 自带 kallsyms_lookup_name 查表实现。

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
