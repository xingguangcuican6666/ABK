#!/usr/bin/env bash
# 干净 GKI 内核：环境、校验、工具链、源码同步、编译与打包。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/clean/validate.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 校验自定义内核版本组合 ----
validate() {
    set -euo pipefail

    android_version="${ABK_INPUT_ANDROID_VERSION}"
    kernel_version="${ABK_INPUT_KERNEL_VERSION}"
    sub_level="${ABK_INPUT_SUB_LEVEL}"
    os_patch_level="${ABK_INPUT_OS_PATCH_LEVEL}"
    combo="${android_version}-${kernel_version}"

    case "$combo" in
      android12-5.10|android13-5.15|android14-6.1|android15-6.6|android16-6.12|android17-6.18) ;;
      *)
        echo "::error::不支持的 Android/内核组合: ${android_version}/${kernel_version}"
        echo "::error::当前工作流支持: android12/5.10, android13/5.15, android14/6.1, android15/6.6, android16/6.12, android17/6.18"
        exit 1
        ;;
    esac

    if [ "${ABK_INPUT_USE_KPM}" = "true" ] \
      && [ "${ABK_INPUT_KSU_VARIANT}" = "Official" ]; then
      echo "::error::Official / KernelSU 变体不支持 KPM，请关闭 KPM 后重试。"
      exit 1
    fi

    if [ "${ABK_INPUT_USE_KPM}" = "true" ] \
      && [ "${ABK_INPUT_KSU_VARIANT}" = "NyaSU" ]; then
      echo "::error::NyaSU 当前未验证 KPM 集成，请关闭 KPM 后重试。"
      exit 1
    fi

    if [ "${ABK_INPUT_USE_KPM}" = "true" ] \
      && [ "${ABK_INPUT_KSU_VARIANT}" = "BakaSU" ] \
      && { [ "${ABK_INPUT_KSU_BRANCH}" = "Dev(开发)" ] || [ "${ABK_INPUT_KSU_BRANCH}" = "Latest(最新)" ]; }; then
      echo "::error::BakaSU 仅 Stable(标准) / Custom(自定义) 支持 KPM，请切换分支或关闭 KPM。"
      exit 1
    fi

    if [ "$sub_level" = "X" ] || [ "$os_patch_level" = "lts" ]; then
      if [ "$sub_level" = "X" ] && [ "$os_patch_level" = "lts" ]; then
        echo "LTS 构建组合校验通过: ${combo}.${sub_level}-${os_patch_level}"
        exit 0
      fi
      echo "::error::LTS 构建必须同时使用 sub_level=X 和 os_patch_level=lts"
      exit 1
    fi

    KNOWN_KERNEL_PAIRS=$(cat <<'EOF'
android12 5.10 43 2021-08
android12 5.10 43 2021-09
android12 5.10 43 2021-10
android12 5.10 66 2021-11
android12 5.10 66 2021-12
android12 5.10 66 2022-01
android12 5.10 81 2022-02
android12 5.10 81 2022-03
android12 5.10 101 2022-04
android12 5.10 101 2022-05
android12 5.10 110 2022-06
android12 5.10 110 2022-07
android12 5.10 117 2022-08
android12 5.10 117 2022-09
android12 5.10 136 2022-10
android12 5.10 136 2022-11
android12 5.10 149 2022-12
android12 5.10 149 2023-01
android12 5.10 160 2023-02
android12 5.10 160 2023-03
android12 5.10 168 2023-04
android12 5.10 168 2023-05
android12 5.10 177 2023-06
android12 5.10 177 2023-07
android12 5.10 185 2023-09
android12 5.10 198 2023-11
android12 5.10 198 2024-01
android12 5.10 205 2024-03
android12 5.10 209 2024-05
android12 5.10 218 2024-08
android12 5.10 226 2024-11
android12 5.10 233 2025-02
android12 5.10 236 2025-05
android12 5.10 237 2025-06
android12 5.10 240 2025-09
android12 5.10 246 2025-12
android13 5.15 41 2022-06
android13 5.15 41 2022-07
android13 5.15 41 2022-08
android13 5.15 41 2022-09
android13 5.15 41 2022-10
android13 5.15 41 2022-11
android13 5.15 74 2022-12
android13 5.15 74 2023-01
android13 5.15 78 2023-02
android13 5.15 78 2023-03
android13 5.15 94 2023-04
android13 5.15 94 2023-05
android13 5.15 104 2023-06
android13 5.15 104 2023-07
android13 5.15 119 2023-08
android13 5.15 119 2023-09
android13 5.15 123 2023-10
android13 5.15 123 2023-11
android13 5.15 137 2023-12
android13 5.15 137 2024-01
android13 5.15 144 2024-02
android13 5.15 144 2024-03
android13 5.15 148 2024-04
android13 5.15 148 2024-05
android13 5.15 149 2024-06
android13 5.15 149 2024-07
android13 5.15 151 2024-08
android13 5.15 153 2024-09
android13 5.15 167 2024-11
android13 5.15 170 2025-01
android13 5.15 178 2025-03
android13 5.15 180 2025-05
android13 5.15 185 2025-07
android13 5.15 189 2025-09
android13 5.15 194 2025-12
android14 6.1 25 2023-06
android14 6.1 25 2023-07
android14 6.1 25 2023-08
android14 6.1 25 2023-09
android14 6.1 25 2023-10
android14 6.1 43 2023-11
android14 6.1 57 2023-12
android14 6.1 57 2024-01
android14 6.1 68 2024-02
android14 6.1 68 2024-03
android14 6.1 75 2024-04
android14 6.1 75 2024-05
android14 6.1 78 2024-06
android14 6.1 84 2024-07
android14 6.1 90 2024-08
android14 6.1 93 2024-09
android14 6.1 99 2024-10
android14 6.1 112 2024-11
android14 6.1 115 2024-12
android14 6.1 118 2025-01
android14 6.1 124 2025-02
android14 6.1 128 2025-03
android14 6.1 129 2025-04
android14 6.1 134 2025-05
android14 6.1 138 2025-06
android14 6.1 145 2025-09
android14 6.1 157 2025-12
android14 6.1 162 2026-03
android15 6.6 50 2024-10
android15 6.6 56 2024-11
android15 6.6 57 2024-12
android15 6.6 58 2025-01
android15 6.6 66 2025-02
android15 6.6 77 2025-03
android15 6.6 82 2025-04
android15 6.6 87 2025-05
android15 6.6 89 2025-06
android15 6.6 98 2025-09
android15 6.6 102 2025-10
android15 6.6 118 2026-01
android15 6.6 127 2026-04
android16 6.12 23 2025-06
android16 6.12 38 2025-09
android16 6.12 58 2025-12
android16 6.12 69 2026-03
android17 6.18 20 2026-04
android17 6.18 21 2026-06
android17 6.18 32 2026-09
EOF
    )

    if awk -v a="$android_version" -v k="$kernel_version" -v s="$sub_level" -v p="$os_patch_level" \
      '$1 == a && $2 == k && $3 == s && $4 == p { found = 1 } END { exit found ? 0 : 1 }' <<< "$KNOWN_KERNEL_PAIRS"; then
      echo "内核版本组合校验通过: ${kernel_version}.${sub_level}-${android_version}-${os_patch_level}"
      exit 0
    fi

    patches_for_sub=$(awk -v a="$android_version" -v k="$kernel_version" -v s="$sub_level" \
      '$1 == a && $2 == k && $3 == s { print $4 }' <<< "$KNOWN_KERNEL_PAIRS" | sort -u | tr '\n' ' ')
    sublevels_for_patch=$(awk -v a="$android_version" -v k="$kernel_version" -v p="$os_patch_level" \
      '$1 == a && $2 == k && $4 == p { print $3 }' <<< "$KNOWN_KERNEL_PAIRS" | sort -n -u | tr '\n' ' ')

    echo "::error::内核版本和安全补丁级别不匹配: ${kernel_version}.${sub_level}-${android_version}-${os_patch_level}"
    if [ -n "$patches_for_sub" ]; then
      echo "::error::${android_version}/${kernel_version}.${sub_level} 可用补丁级别: ${patches_for_sub}"
    fi
    if [ -n "$sublevels_for_patch" ]; then
      echo "::error::${android_version}/${kernel_version}/${os_patch_level} 可用子版本号: ${sublevels_for_patch}"
    fi
    exit 1

}

case "${1:-}" in
    validate) validate ;;
    *)
        echo "用法: $0 {validate}" >&2
        exit 2
        ;;
esac
