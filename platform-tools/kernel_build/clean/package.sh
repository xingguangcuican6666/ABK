#!/usr/bin/env bash
# 干净 GKI 内核：环境、校验、工具链、源码同步、编译与打包。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/clean/package.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 准备 Boot 镜像 ----
boot-prep() {
    set -euo pipefail
    mkdir -p bootimgs

    if [ "${ABK_INPUT_ANDROID_VERSION}" == "android12" ] || [ "${ABK_INPUT_ANDROID_VERSION}" == "android13" ]; then
      SRC_DIR="$KERNEL_ROOT/out/${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}/dist"
    else
      SRC_DIR="$KERNEL_ROOT/bazel-bin/common/kernel_aarch64"
    fi

    cp "$SRC_DIR/Image" ./bootimgs/
    cp "$SRC_DIR/Image.lz4" ./bootimgs/
    cp "$SRC_DIR/Image" ./
    cp "$SRC_DIR/Image.lz4" ./
    gzip -n -c -9 ./Image > ./Image.gz
    cp ./Image.gz ./bootimgs/Image.gz

    if [ "${ABK_INPUT_SOURCE_MODE}" = "custom_git" ]; then
      images_name="${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}-${ABK_INPUT_OS_PATCH_LEVEL}-Images.zip"
      (
        cd bootimgs
        zip -9 "../$images_name" Image Image.lz4 Image.gz
      )
    fi

}

# ---- 创建 AnyKernel3 压缩包 ----
anykernel() {
    cd $ANYKERNEL3
    ZIP_NAME="${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}-${ABK_INPUT_OS_PATCH_LEVEL}-AnyKernel3.zip"
    mv ../Image ./Image
    zip -r "../$ZIP_NAME" ./*

}

# ---- 构建 Boot 镜像 (Android 12) ----
boot-a12() {
    cd "$GITHUB_WORKSPACE/bootimgs"
    GKI_URL=https://dl.google.com/android/gki/gki-certified-boot-android12-5.10-${ABK_INPUT_OS_PATCH_LEVEL}_${ABK_INPUT_REVISION}.zip
    FALLBACK_URL=https://dl.google.com/android/gki/gki-certified-boot-android12-5.10-2023-01_r1.zip

    status=$(curl -sL -w "%{http_code}" "$GKI_URL" -o /dev/null)
    if [ "$status" = "200" ]; then
      curl -Lo gki-kernel.zip "$GKI_URL"
    else
      curl -Lo gki-kernel.zip "$FALLBACK_URL"
    fi

    unzip gki-kernel.zip && rm gki-kernel.zip
    $UNPACK_BOOTIMG --boot_img="$(pwd)/boot-5.10.img"

    gzip -n -k -f -9 ./Image > ./Image.gz

    $MKBOOTIMG --header_version 4 --kernel Image --output boot.img --ramdisk out/ramdisk --os_version 12.0.0 --os_patch_level "${ABK_INPUT_OS_PATCH_LEVEL}"
    $AVBTOOL add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot.img --algorithm SHA256_RSA2048 --key $BOOT_SIGN_KEY_PATH
    cp ./boot.img ../${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}-${ABK_INPUT_OS_PATCH_LEVEL}-boot.img

    $MKBOOTIMG --header_version 4 --kernel Image.gz --output boot-gz.img --ramdisk out/ramdisk --os_version 12.0.0 --os_patch_level "${ABK_INPUT_OS_PATCH_LEVEL}"
    $AVBTOOL add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot-gz.img --algorithm SHA256_RSA2048 --key $BOOT_SIGN_KEY_PATH
    cp ./boot-gz.img ../${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}-${ABK_INPUT_OS_PATCH_LEVEL}-boot-gz.img

    $MKBOOTIMG --header_version 4 --kernel Image.lz4 --output boot-lz4.img --ramdisk out/ramdisk --os_version 12.0.0 --os_patch_level "${ABK_INPUT_OS_PATCH_LEVEL}"
    $AVBTOOL add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot-lz4.img --algorithm SHA256_RSA2048 --key $BOOT_SIGN_KEY_PATH
    cp ./boot-lz4.img ../${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}-${ABK_INPUT_OS_PATCH_LEVEL}-boot-lz4.img

}

# ---- 构建 Boot 镜像 (Android 13+) ----
boot-a13() {
    cd "$GITHUB_WORKSPACE/bootimgs"
    gzip -n -k -f -9 ./Image > ./Image.gz

    $MKBOOTIMG --header_version 4 --kernel Image --output boot.img
    $AVBTOOL add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot.img --algorithm SHA256_RSA2048 --key $BOOT_SIGN_KEY_PATH
    cp ./boot.img ../${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}-${ABK_INPUT_OS_PATCH_LEVEL}-boot.img

    $MKBOOTIMG --header_version 4 --kernel Image.gz --output boot-gz.img
    $AVBTOOL add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot-gz.img --algorithm SHA256_RSA2048 --key $BOOT_SIGN_KEY_PATH
    cp ./boot-gz.img ../${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}-${ABK_INPUT_OS_PATCH_LEVEL}-boot-gz.img

    $MKBOOTIMG --header_version 4 --kernel Image.lz4 --output boot-lz4.img
    $AVBTOOL add_hash_footer --partition_name boot --partition_size $((64 * 1024 * 1024)) --image boot-lz4.img --algorithm SHA256_RSA2048 --key $BOOT_SIGN_KEY_PATH
    cp ./boot-lz4.img ../${ABK_INPUT_ANDROID_VERSION}-${ABK_INPUT_KERNEL_VERSION}.${ABK_INPUT_SUB_LEVEL}-${ABK_INPUT_OS_PATCH_LEVEL}-boot-lz4.img

}

# ---- 打包签名内核 Bundle ----
bundle() {
    set -euo pipefail

    if [ -z "${ABK_ARTIFACT_SIGNING_KEY_BASE64:-}" ]; then
      if [ "${ABK_INPUT_SOURCE_MODE}" = "custom_git" ]; then
        echo "::error::Custom source 产物必须签名；请先在 ABK App 中初始化 fork 产物签名。"
        exit 1
      fi
      echo "::warning::缺少 ABK_ARTIFACT_SIGNING_KEY_BASE64 secret，将生成未签名 bundle"
      key_file=""
    else
      key_file="$RUNNER_TEMP/abk-artifact-signing.key"
      printf '%s' "$ABK_ARTIFACT_SIGNING_KEY_BASE64" | base64 -d > "$key_file"
    fi
    args=(--root .)
    [ -n "$key_file" ] && args+=(--key-file "$key_file")
    [ "${ABK_INPUT_SOURCE_MODE}" = "custom_git" ] && args+=(--require-signature)
    python3 "$GITHUB_WORKSPACE/.github/scripts/create-artifact-bundles.py" ${args[@]+"${args[@]}"}

    if [ "${ABK_INPUT_SOURCE_MODE}" = "custom_git" ]; then
      images_count=$(find . -maxdepth 1 -type f -name '*-Images.zip.bundle.zip' | wc -l)
      anykernel_count=$(find . -maxdepth 1 -type f -name '*-AnyKernel3.zip.bundle.zip' | wc -l)
      if [ "$images_count" -ne 1 ] || [ "$anykernel_count" -ne 1 ]; then
        echo "::error::Custom source 必须同时生成一个 Images bundle 和一个 AnyKernel3 bundle。"
        rm -f ./*.bundle.zip
        exit 1
      fi
    fi

}

# ---- 收集补丁冲突文件 ----
collect-rejects() {
    REJECTS_DIR="$GITHUB_WORKSPACE/patch-rejects"
    mkdir -p "$REJECTS_DIR"

    mapfile -t REJS < <(find "$KERNEL_ROOT" -type f -name '*.rej')
    REJ_COUNT=${#REJS[@]}
    echo "发现 $REJ_COUNT 个 .rej 文件"
    echo "REJ_COUNT=$REJ_COUNT" >> $GITHUB_ENV

    if [ "$REJ_COUNT" -gt 0 ]; then
      for REJ in "${REJS[@]}"; do
        REL="${REJ#"$KERNEL_ROOT"/}"
        DEST="$REJECTS_DIR/$REL"
        mkdir -p "$(dirname "$DEST")"
        cp "$REJ" "$DEST"

        ORIG="${REJ%.rej}"
        if [ -f "$ORIG" ]; then
          cp "$ORIG" "${DEST%.rej}"
        fi
        echo "$REL" >> "$REJECTS_DIR/index.txt"
      done
    fi

}

case "${1:-}" in
    boot-prep) boot-prep ;;
    anykernel) anykernel ;;
    boot-a12) boot-a12 ;;
    boot-a13) boot-a13 ;;
    bundle) bundle ;;
    collect-rejects) collect-rejects ;;
    *)
        echo "用法: $0 {boot-prep|anykernel|boot-a12|boot-a13|bundle|collect-rejects}" >&2
        exit 2
        ;;
esac
