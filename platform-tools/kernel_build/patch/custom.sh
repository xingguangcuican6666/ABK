#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/custom.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 克隆自定义外部模块 ----
modules-clone() {
    set -euo pipefail

    trim() {
      sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
    }

    normalize_stage() {
      local raw="$1"
      local stage
      stage=$(printf '%s' "$raw" | tr '[:upper:] -' '[:lower:]__')
      case "$stage" in
        after_patch)
          printf '%s\n' "after_patch"
          ;;
        before_build|befor_build)
          printf '%s\n' "before_build"
          ;;
        *)
          return 1
          ;;
      esac
    }

    modules_input="$(printf '%s' "$CUSTOM_EXTERNAL_MODULES" | tr -d '\r')"
    manifest="$CUSTOM_EXTERNAL_MODULES_MANIFEST"
    : > "$manifest"

    if [ -z "$(printf '%s' "$modules_input" | trim)" ]; then
      echo "已启用自定义外部模块，但未配置模块字符串，跳过克隆。"
      exit 0
    fi

    IFS='|' read -r -a module_blocks <<< "$modules_input"
    index=0
    for block in "${module_blocks[@]}"; do
      block="$(printf '%s' "$block" | trim)"
      [ -n "$block" ] || continue

      if [[ "$block" != *";"* ]]; then
        echo "::error::自定义外部模块格式错误，缺少分号: $block"
        exit 1
      fi

      lhs="$(printf '%s' "${block%%;*}" | trim)"
      stage_raw="$(printf '%s' "${block#*;}" | trim)"
      entry_kind="module"
      group_repo_url=""
      child_id=""

      case "$lhs" in
        module:*)
          repo_url="$(printf '%s' "${lhs#module:}" | trim)"
          ;;
        set:*)
          descriptor="$(printf '%s' "${lhs#set:}" | trim)"
          if [[ "$descriptor" != *"#"* ]]; then
            echo "::error::模块集格式错误，缺少子模块 id: $block"
            exit 1
          fi
          group_repo_url="$(printf '%s' "${descriptor%%#*}" | trim)"
          child_id="$(printf '%s' "${descriptor#*#}" | trim)"
          repo_url="$group_repo_url"
          entry_kind="module_set_child"
          ;;
        *)
          repo_url="$lhs"
          ;;
      esac

      if [ -z "$repo_url" ] || [ -z "$stage_raw" ]; then
        echo "::error::自定义外部模块格式错误，链接或阶段为空: $block"
        exit 1
      fi

      if [ "$entry_kind" = "module_set_child" ] && [ -z "$child_id" ]; then
        echo "::error::模块集子模块 id 不能为空: $block"
        exit 1
      fi

      case "$repo_url" in
        http://*|https://*|git://*|ssh://*|git@*) ;;
        *)
          echo "::error::自定义外部模块链接不支持: $repo_url"
          exit 1
          ;;
      esac

      if ! stage="$(normalize_stage "$stage_raw")"; then
        echo "::error::自定义外部模块阶段不支持: $stage_raw（仅支持 after_patch / before_build）"
        exit 1
      fi

      index=$((index + 1))
      repo_name="${repo_url##*/}"
      repo_name="${repo_name%.git}"
      repo_name="$(printf '%s' "$repo_name" | sed 's/[^A-Za-z0-9._-]/_/g')"
      [ -n "$repo_name" ] || repo_name="module"
      module_dir="$GITHUB_WORKSPACE/custom_external_module_$(printf '%02d' "$index")-$repo_name"

      echo "克隆自定义外部模块 #$index ($stage/$entry_kind): $repo_url"
      git clone --depth 1 "$repo_url" "$module_dir"
      echo "external module checkout #$index:"
      echo "  repo_url: $repo_url"
      echo "  module_dir: $module_dir"
      echo "  head_sha: $(git -C "$module_dir" rev-parse HEAD)"
      echo "  branch: $(git -C "$module_dir" branch --show-current || true)"
      echo "  origin: $(git -C "$module_dir" remote get-url origin)"
      echo "  head_log: $(git -C "$module_dir" log -1 --oneline)"
      printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$stage" \
        "$module_dir" \
        "$repo_url" \
        "$entry_kind" \
        "$group_repo_url" \
        "$child_id" >> "$manifest"
    done

    if [ ! -s "$manifest" ]; then
      echo "未解析到有效自定义外部模块。"
    else
      echo "自定义外部模块清单:"
      cut -f1,3 "$manifest"
    fi

}

# ---- 生成刷写外层 Magisk 依赖清单 ----
flash-deps() {
    set -euo pipefail

    python3 - <<'PY'
import json
import os
from pathlib import Path

manifest_path = Path(os.environ["CUSTOM_EXTERNAL_MODULES_MANIFEST"])
output_path = Path(os.environ["ABK_FLASH_DEPENDENCY_MANIFEST"])

def parse_shell_like_conf(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    lines = text.splitlines()
    index = 0
    while index < len(lines):
        clean = lines[index].split("#", 1)[0].strip()
        if not clean or "=" not in clean:
            index += 1
            continue
        key, value = clean.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            index += 1
            continue
        if value in {"'", '"'}:
            quote = value
            index += 1
            collected: list[str] = []
            while index < len(lines):
                raw = lines[index]
                if raw.strip() == quote:
                    break
                collected.append(raw)
                index += 1
            result[key] = "\n".join(collected)
        else:
            if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
                value = value[1:-1]
            result[key] = value
        index += 1
    return result

def parse_module_set_items(raw: str) -> list[dict[str, str]]:
    items: list[dict[str, str]] = []
    for line in raw.splitlines():
        clean = line.strip()
        if not clean or clean.startswith("#"):
            continue
        parts = [part.strip() for part in clean.split("|")]
        if len(parts) < 4:
            continue
        item = {
            "id": parts[0],
            "name": parts[1] if len(parts) > 1 else "",
            "description": parts[2] if len(parts) > 2 else "",
            "repo_url": parts[3] if len(parts) > 3 else "",
            "magisk_module_name": parts[10] if len(parts) > 10 else "",
            "magisk_module_download_url": parts[11] if len(parts) > 11 else "",
        }
        items.append(item)
    return items

dependencies: list[dict[str, str]] = []
companion_apps: list[dict[str, str]] = []
seen_urls: set[str] = set()
seen_companion_urls: set[str] = set()

if manifest_path.exists() and manifest_path.stat().st_size > 0:
    for line in manifest_path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        entry_kind = parts[3].strip() if len(parts) > 3 and parts[3].strip() else "module"
        group_repo_url = parts[4].strip() if len(parts) > 4 else ""
        child_id = parts[5].strip() if len(parts) > 5 else ""
        module_dir = Path(parts[1].strip())
        repo_url = parts[2].strip()
        conf_path = module_dir / "module.conf"
        if not conf_path.is_file():
            continue
        conf = parse_shell_like_conf(conf_path.read_text(encoding="utf-8"))

        dep_name = ""
        dep_url = ""
        source_name = conf.get("ABK_MODULE_NAME", "").strip()
        source_id = conf.get("ABK_MODULE_ID", "").strip()
        if entry_kind == "module_set_child":
            child = next(
                (item for item in parse_module_set_items(conf.get("ABK_MODULE_SET_ITEMS", "")) if item.get("id", "") == child_id),
                None
            )
            if child is None:
                continue
            dep_name = child.get("magisk_module_name", "").strip()
            dep_url = child.get("magisk_module_download_url", "").strip()
            source_name = child.get("name", "").strip() or source_name
            source_id = child_id or source_id
        else:
            dep_name = conf.get("ABK_MAGISK_MODULE_NAME", "").strip()
            dep_url = conf.get("ABK_MAGISK_MODULE_DOWNLOAD_URL", "").strip()

        extension_id = conf.get("ABK_EXTENSION_ID", "").strip()
        companion_package = conf.get("ABK_EXTENSION_COMPANION_PACKAGE", "").strip()
        companion_name = conf.get("ABK_EXTENSION_COMPANION_DISPLAY_NAME", "").strip() or source_name
        companion_asset = conf.get("ABK_EXTENSION_COMPANION_ASSET_NAME", "").strip()
        companion_url = conf.get("ABK_EXTENSION_COMPANION_DOWNLOAD_URL", "").strip()
        requires_companion = conf.get("ABK_EXTENSION_REQUIRES_COMPANION_APP", "").strip().lower() in {
            "1", "y", "yes", "true", "on"
        }
        if requires_companion and companion_url:
            companion_key = companion_url.lower()
            if companion_key not in seen_companion_urls:
                seen_companion_urls.add(companion_key)
                companion_apps.append(
                    {
                        "extensionId": extension_id,
                        "packageName": companion_package,
                        "displayName": companion_name,
                        "assetName": companion_asset,
                        "downloadUrl": companion_url,
                        "moduleId": source_id,
                        "moduleName": source_name,
                        "moduleRepoUrl": repo_url,
                    }
                )

        if not dep_url:
            continue
        key = dep_url.lower()
        if key in seen_urls:
            continue
        seen_urls.add(key)
        dependencies.append(
            {
                "id": source_id,
                "name": dep_name or source_name or "magisk-module",
                "downloadUrl": dep_url,
                "moduleRepoUrl": repo_url,
                "groupRepoUrl": group_repo_url,
                "childId": child_id,
                "sourceName": source_name,
            }
        )

if dependencies or companion_apps:
    output_path.write_text(
        json.dumps(
            {
                "schema": 2,
                "magiskModules": dependencies,
                "companionApps": companion_apps,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(
        f"Wrote {output_path} with "
        f"{len(dependencies)} Magisk dependencies and "
        f"{len(companion_apps)} companion apps"
    )
elif output_path.exists():
    output_path.unlink()
PY

}

# ---- 执行自定义外部模块 (after_patch) ----
after-patch() {
    set -euo pipefail
    export ABK_BUILD_KSU_BRANCH="${EFFECTIVE_KSU_BRANCH:-$ABK_BUILD_KSU_BRANCH}"

    manifest="$CUSTOM_EXTERNAL_MODULES_MANIFEST"
    if [ ! -s "$manifest" ]; then
      echo "未找到自定义外部模块清单，跳过 $CUSTOM_EXTERNAL_MODULE_STAGE。"
      exit 0
    fi

    executed=0
    while IFS=$'\t' read -r stage module_dir repo_url entry_kind group_repo_url child_id; do
      [ "$stage" = "$CUSTOM_EXTERNAL_MODULE_STAGE" ] || continue

      setup="$module_dir/setup.sh"
      if [ ! -f "$setup" ]; then
        echo "::error::自定义外部模块缺少入口文件 setup.sh: $repo_url ($module_dir)"
        exit 1
      fi

      chmod +x "$setup"
      echo "::group::执行自定义外部模块 [$stage] $repo_url"
      (
        cd "$module_dir"
        echo "external module execution context:"
        echo "  repo_url: $repo_url"
        echo "  module_dir: $module_dir"
        echo "  entry_kind: ${entry_kind:-module}"
        echo "  child_id: ${child_id:-}"
        echo "  head_sha: $(git rev-parse HEAD)"
        echo "  branch: $(git branch --show-current || true)"
        echo "  origin: $(git remote get-url origin)"
        echo "  head_log: $(git log -1 --oneline)"
        export ABK_MODULE_ENTRY_KIND="${entry_kind:-module}"
        export ABK_MODULE_GROUP_REPO_URL="${group_repo_url:-}"
        export ABK_MODULE_CHILD_ID="${child_id:-}"
        bash "$setup"
      )
      echo "::endgroup::"
      executed=$((executed + 1))
    done < "$manifest"

    if [ "$executed" -eq 0 ]; then
      echo "没有配置 $CUSTOM_EXTERNAL_MODULE_STAGE 阶段的自定义外部模块。"
    fi

}

# ---- 执行自定义外部模块 (before_build) ----
before-build() {
    set -euo pipefail
    export ABK_BUILD_KSU_BRANCH="${EFFECTIVE_KSU_BRANCH:-$ABK_BUILD_KSU_BRANCH}"

    manifest="$CUSTOM_EXTERNAL_MODULES_MANIFEST"
    if [ ! -s "$manifest" ]; then
      echo "未找到自定义外部模块清单，跳过 $CUSTOM_EXTERNAL_MODULE_STAGE。"
      exit 0
    fi

    executed=0
    while IFS=$'\t' read -r stage module_dir repo_url entry_kind group_repo_url child_id; do
      [ "$stage" = "$CUSTOM_EXTERNAL_MODULE_STAGE" ] || continue

      setup="$module_dir/setup.sh"
      if [ ! -f "$setup" ]; then
        echo "::error::自定义外部模块缺少入口文件 setup.sh: $repo_url ($module_dir)"
        exit 1
      fi

      chmod +x "$setup"
      echo "::group::执行自定义外部模块 [$stage] $repo_url"
      (
        cd "$module_dir"
        export ABK_MODULE_ENTRY_KIND="${entry_kind:-module}"
        export ABK_MODULE_GROUP_REPO_URL="${group_repo_url:-}"
        export ABK_MODULE_CHILD_ID="${child_id:-}"
        bash "$setup"
      )
      echo "::endgroup::"
      executed=$((executed + 1))
    done < "$manifest"

    if [ "$executed" -eq 0 ]; then
      echo "没有配置 $CUSTOM_EXTERNAL_MODULE_STAGE 阶段的自定义外部模块。"
    fi

}

# ---- 校验 ABK 管理器桥接 ----
manager-bridge() {
    cd "$KERNEL_ROOT"
    set -euo pipefail

    needs_abk_control=false
    manifest="$CUSTOM_EXTERNAL_MODULES_MANIFEST"
    if [ -s "$manifest" ]; then
      while IFS=$'\t' read -r _stage module_dir repo_url; do
        if [ -f "$module_dir/module.conf" ] \
          && grep -Eq '^[[:space:]]*ABK_MODULE_ID=' "$module_dir/module.conf" \
          && grep -qF 'abk_control' "$module_dir/module.conf"; then
          needs_abk_control=true
        fi
        case "$repo_url" in
          *ABK_control_module*|*abk_control_module*) needs_abk_control=true ;;
        esac
      done < "$manifest"
    fi

    if [ "$needs_abk_control" != "true" ]; then
      echo "未检测到 ABK Control 外部模块，跳过管理器桥接校验。"
      exit 0
    fi

    echo "校验 ABK Control 管理器桥接:"
    echo "  package: ${ABK_MANAGER_PACKAGE:-}"
    echo "  cert size: ${ABK_MANAGER_CERT_SIZE:-}"
    echo "  cert sha256: ${ABK_MANAGER_CERT_SHA256:-}"

    require_marker() {
      local file="$1"
      shift
      if [ ! -f "$file" ]; then
        echo "::error::缺少 ABK 管理器桥接文件: $file"
        exit 1
      fi
      local marker
      for marker in "$@"; do
        if ! grep -qF "$marker" "$file"; then
          echo "::error::$file 缺少 ABK 管理器桥接标记: $marker"
          exit 1
        fi
      done
    }

    found_ksu=false
    for ksu_dir in common/drivers/kernelsu drivers/kernelsu KernelSU/kernel kernel; do
      [ -d "$ksu_dir" ] || continue
      if [ ! -f "$ksu_dir/Kbuild" ] \
        || [ ! -f "$ksu_dir/manager/apk_sign.c" ] \
        || [ ! -f "$ksu_dir/supercall/dispatch.c" ]; then
        echo "跳过非 KernelSU 源码目录: $ksu_dir"
        continue
      fi
      found_ksu=true
      echo "检查 KSU 源码目录: $ksu_dir"
      require_marker "$ksu_dir/Kbuild" \
        "ABK_MANAGER_PACKAGE" \
        "${ABK_MANAGER_PACKAGE:-com.abk.kernel}" \
        "ABK_MANAGER_CERT_SIZE" \
        "${ABK_MANAGER_CERT_SIZE:-1407}" \
        "ABK_MANAGER_CERT_SHA256" \
        "${ABK_MANAGER_CERT_SHA256:-34e5e843952277759603cd0f949770b24c868530d80d7baeff08776a7e132b16}" \
        "ABK_MANAGER_CERT_MAX_LENGTH" \
        "ABK_MANAGER_OFFICIAL_CERT"
      require_marker "$ksu_dir/manager/apk_sign.c" "ABK_MANAGER_CERT_SHA256" "ABK_MANAGER_CERT_MAX_LENGTH"
      require_marker "$ksu_dir/manager/throne_tracker.h" "abk_try_register_manager"
      require_marker "$ksu_dir/manager/throne_tracker.c" "abk_try_register_manager"
      require_marker "$ksu_dir/supercall/dispatch.c" \
        "abk_try_register_manager" \
        "KSU_GET_INFO_FLAG_MANAGER" \
        "ABK_CONTROL_IOCTL_GET_STATUS"

      # 必须与 ABK_control_module 的 abk_control_ksu_patch.py::is_bakasu_tracker()
      # 保持一致：上游 2026-10-05 改名 ReSukiSU -> BakaSU 时已将
      # TRACK_THRONE_FORCE_SEARCH_MGR 换成 TRACK_THRONE_FORCE_SYNCHRONOUS
      # (ABK_control_module#0f2947d)，此处若不同步会在真正的 BakaSU tracker 上
      # 误判为"需要移植"，导致本不存在的 manager_identity.h 桥接标记校验失败。
      if grep -qF "do_track_throne" "$ksu_dir/manager/throne_tracker.c" \
        && grep -qF "track_throne(unsigned int flags)" "$ksu_dir/manager/throne_tracker.h" \
        && grep -qF "TRACK_THRONE_FORCE_SYNCHRONOUS" "$ksu_dir/manager/throne_tracker.h"; then
        echo "检测到 BakaSU 多管理器实现，跳过 KernelSU/SukiSU 多管理器移植标记。"
      else
        require_marker "$ksu_dir/manager/manager_identity.h" \
          "ABK_MANAGER_MULTI_MANAGER_BRIDGE" \
          "ksu_register_manager" \
          "ksu_has_manager"
        require_marker "$ksu_dir/manager/throne_tracker.c" \
          "ABK_MANAGER_MULTI_MANAGER_BRIDGE" \
          "abk_prune_missing_managers" \
          "ksu_register_manager"
        if [ -f "$ksu_dir/policy/allowlist.c" ]; then
          require_marker "$ksu_dir/policy/allowlist.c" "is_uid_manager(uid)"
        fi
        if grep -qF "ABK Control: prefer ABK manager" "$ksu_dir/manager/throne_tracker.c"; then
          echo "::error::$ksu_dir/manager/throne_tracker.c 仍包含旧的 ABK 优先抢占逻辑。"
          exit 1
        fi
        if grep -qF "bool should_stop = get_pkg_from_apk_path" "$ksu_dir/manager/throne_tracker.c"; then
          echo "::error::$ksu_dir/manager/throne_tracker.c 仍包含只等 ABK 后停止扫描的旧逻辑。"
          exit 1
        fi
        if grep -qF "ksu_invalidate_manager_uid();" "$ksu_dir/manager/throne_tracker.c"; then
          echo "::error::$ksu_dir/manager/throne_tracker.c 仍会在 ABK 注册时清空官方管理器。"
          exit 1
        fi
      fi
    done

    if [ "$found_ksu" != "true" ]; then
      echo "::error::未找到 KernelSU/BakaSU 源码目录，无法校验 ABK 管理器桥接。"
      exit 1
    fi

    if ! grep -q '^CONFIG_ABK_CONTROL=y$' "$DEFCONFIG"; then
      echo "::error::缺少 CONFIG_ABK_CONTROL=y；请同时选择 ABK Control 的 before_build 阶段。"
      exit 1
    fi

    echo "ABK Control 管理器桥接校验通过。"

}

# ---- 应用自定义内核选项 ----
kernel-options-custom() {
    cd "$KERNEL_ROOT"
    set -euo pipefail

    python3 - <<'PY'
import os
import re
import subprocess
from pathlib import Path

defconfig = Path(os.environ["DEFCONFIG"])
config_files = [defconfig]
stock_defconfig = defconfig.parent / "stock_defconfig"
if stock_defconfig.is_file():
    # config_data 指向 stock_defconfig 时，用户配置也必须写入这份
    # 配置，否则 /proc/config.gz 仍会显示 stock 中的旧值。
    config_files.append(stock_defconfig)
    print(f"将用户配置覆盖到 stock_defconfig: {stock_defconfig}")

text_by_path = {
    path: path.read_text().splitlines()
    for path in config_files
}
lines = os.environ["CUSTOM_KERNEL_OPTIONS"].replace("\r", "").splitlines()

parsed = []
disabled_re = re.compile(r"^#\s*(CONFIG_[A-Za-z0-9_]+)\s+is\s+not\s+set$", re.I)
assigned_re = re.compile(r"^(CONFIG_[A-Za-z0-9_]+)=(.+)$")

def normalize_symbol(raw: str) -> str:
    candidate = raw.strip().replace(" ", "")
    if not candidate:
        return ""
    if not candidate.upper().startswith("CONFIG_"):
        candidate = f"CONFIG_{candidate}"
    candidate = candidate.upper()
    if not re.fullmatch(r"CONFIG_[A-Z0-9_]+", candidate):
        return ""
    return candidate

def config_defined(symbol: str) -> bool:
    name = symbol.removeprefix("CONFIG_")
    result = subprocess.run(
        [
            "bash",
            "-lc",
            "find . -type f -name 'Kconfig*' -print0 | xargs -0 grep -El \"^[[:space:]]*(menuconfig|config)[[:space:]]+" + name + "([[:space:]]|$)\" >/dev/null"
        ],
        cwd=os.getcwd(),
    )
    return result.returncode == 0

def set_config_value(text_lines, symbol: str, mode: str, value: str = ""):
    clean = symbol.removeprefix("CONFIG_")
    filtered = []
    for line in text_lines:
        if re.match(rf"^(CONFIG_{re.escape(clean)}=|# CONFIG_{re.escape(clean)} is not set$)", line):
            continue
        filtered.append(line)
    if mode == "disabled":
        filtered.append(f"# CONFIG_{clean} is not set")
    elif mode == "raw":
        filtered.append(f"CONFIG_{clean}={value}")
    else:
        filtered.append(f"CONFIG_{clean}={mode}")
    return filtered

for raw in lines:
    clean = raw.strip()
    if not clean:
        continue
    if clean.startswith("#") and not clean.startswith("# CONFIG_"):
        continue
    disabled = disabled_re.match(clean)
    assigned = assigned_re.match(clean)
    if disabled:
        symbol = normalize_symbol(disabled.group(1))
        if symbol:
            parsed.append((symbol, "disabled", ""))
        continue
    if assigned:
        symbol = normalize_symbol(assigned.group(1))
        value = assigned.group(2).strip()
        if not symbol:
            print(f"::warning::无效自定义内核选项，已跳过: {clean}")
            continue
        if value.lower() == "y":
            parsed.append((symbol, "y", ""))
        elif value.lower() == "m":
            parsed.append((symbol, "m", ""))
        elif value.lower() == "n":
            parsed.append((symbol, "disabled", ""))
        else:
            parsed.append((symbol, "raw", value))
        continue
    print(f"::warning::无法识别的自定义内核选项行，已跳过: {clean}")

deduped = []
seen = set()
for symbol, mode, value in reversed(parsed):
    if symbol in seen:
        continue
    seen.add(symbol)
    deduped.append((symbol, mode, value))
deduped.reverse()

applied = 0
skipped = 0
for symbol, mode, value in deduped:
    if not config_defined(symbol):
        print(f"::warning::{symbol} 未在 Kconfig 中定义，已跳过")
        skipped += 1
        continue
    for path in config_files:
        text_by_path[path] = set_config_value(
            text_by_path[path], symbol, mode, value
        )
    applied += 1
    if mode == "raw":
        print(f"Applied custom kernel option: {symbol}={value}")
    elif mode == "disabled":
        print(f"Applied custom kernel option: # {symbol} is not set")
    else:
        print(f"Applied custom kernel option: {symbol}={mode}")

for path, text_lines in text_by_path.items():
    path.write_text("\n".join(text_lines) + "\n")
print(
    f"自定义内核选项: 应用 {applied} 条，跳过 {skipped} 条，"
    f"目标配置: {', '.join(path.name for path in config_files)}"
)
PY

}

case "${1:-}" in
    modules-clone) modules-clone ;;
    flash-deps) flash-deps ;;
    after-patch) after-patch ;;
    before-build) before-build ;;
    manager-bridge) manager-bridge ;;
    kernel-options-custom) kernel-options-custom ;;
    *)
        echo "用法: $0 {modules-clone|flash-deps|after-patch|before-build|manager-bridge|kernel-options-custom}" >&2
        exit 2
        ;;
esac
