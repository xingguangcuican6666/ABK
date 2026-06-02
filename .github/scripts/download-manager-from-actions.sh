#!/usr/bin/env bash
set -euo pipefail

REPO="${1:?repo owner/name required}"
KSU_SHA="${2:?commit SHA required}"
VARIANT_DIR="${3:?output dir required}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=resolve-ksu-ref.sh
source "${SCRIPT_DIR}/resolve-ksu-ref.sh"

# Artifact zip names on nightly.link match actions/upload-artifact names + ".zip".
nightly_artifact_zip() {
  case "$REPO" in
    ReSukiSU/ReSukiSU) printf '%s\n' "Manager-release.zip" ;;
    tiann/KernelSU | SukiSU-Ultra/SukiSU-Ultra) printf '%s\n' "manager.zip" ;;
    *)
      echo "::error::Unknown repo for nightly.link manager download: ${REPO}" >&2
      exit 1
      ;;
  esac
}

manager_artifact_name() {
  case "$REPO" in
    ReSukiSU/ReSukiSU) printf '%s\n' "Manager-release" ;;
    *) printf '%s\n' "manager" ;;
  esac
}

extract_apk_from_zip() {
  local tmp_zip="$1"
  unzip -o "$tmp_zip" -d "$VARIANT_DIR"
  rm -f "$tmp_zip"
  if ! find "$VARIANT_DIR" -type f -name '*.apk' -print -quit | grep -q .; then
    echo "::error::Downloaded artifact did not contain an APK in ${VARIANT_DIR}" >&2
    find "$VARIANT_DIR" -type f | head -20 >&2 || true
    return 1
  fi
}

try_download_artifact_api() {
  local run_id="$1"
  local artifact_name tmp_zip artifact_id artifacts_json

  if [ -z "${GITHUB_TOKEN:-}" ]; then
    return 1
  fi

  artifact_name="$(manager_artifact_name)"
  KSU_API_REPO="$REPO"
  artifacts_json="$(ksu_github_api_curl \
    "https://api.github.com/repos/${REPO}/actions/runs/${run_id}/artifacts")"
  artifact_id="$(printf '%s' "$artifacts_json" | jq -r --arg name "$artifact_name" '
    (.artifacts[] | select(.name == $name) | .id) // empty')"
  if [ -z "$artifact_id" ] || [ "$artifact_id" = "null" ]; then
    echo "::warning::No manager artifact on run ${run_id} for ${REPO}" >&2
    return 1
  fi

  mkdir -p "$VARIANT_DIR"
  tmp_zip="$(mktemp "${TMPDIR:-/tmp}/manager-XXXXXX.zip")"
  if ! ksu_github_api_curl -L \
    "https://api.github.com/repos/${REPO}/actions/artifacts/${artifact_id}/zip" \
    -o "$tmp_zip"; then
    rm -f "$tmp_zip"
    return 1
  fi
  extract_apk_from_zip "$tmp_zip"
  echo "Downloaded manager from ${REPO}@${KSU_SHA} (run ${run_id}, source ${MANAGER_RUN_SOURCE}) via Actions API"
}

try_download_nightly_run() {
  local run_id="$1"
  local zip_name url tmp_zip
  zip_name="$(nightly_artifact_zip)"
  url="https://nightly.link/${REPO}/actions/runs/${run_id}/${zip_name}"
  mkdir -p "$VARIANT_DIR"
  tmp_zip="$(mktemp "${TMPDIR:-/tmp}/manager-XXXXXX.zip")"

  echo "Downloading manager via nightly.link (run ${run_id}, sha ${KSU_SHA}, source ${MANAGER_RUN_SOURCE}): ${url}"
  if ! curl -fsSL -o "$tmp_zip" "$url"; then
    rm -f "$tmp_zip"
    return 1
  fi
  extract_apk_from_zip "$tmp_zip"
  echo "Downloaded manager from ${REPO}@${KSU_SHA} (run ${run_id}, source ${MANAGER_RUN_SOURCE}) via nightly.link"
}

if ! ksu_find_manager_run_id "$REPO" "$KSU_SHA"; then
  exit 1
fi
run_id="${KSU_MANAGER_RUN_ID}"
if [ "${MANAGER_RUN_FALLBACK_MAIN:-0}" = "1" ]; then
  echo "::notice::Manager APK from latest successful build-manager on main (KSU ref was ${KSU_SHA})" >&2
fi
echo "Manager workflow run for ${REPO}@${KSU_SHA} (source=${MANAGER_RUN_SOURCE}): https://github.com/${REPO}/actions/runs/${run_id}"

if [ -n "${GITHUB_REPOSITORY:-}" ] && [ "$REPO" = "$GITHUB_REPOSITORY" ]; then
  if try_download_artifact_api "$run_id"; then
    exit 0
  fi
  echo "::warning::Actions artifact API failed; trying nightly.link for the same run..." >&2
else
  if try_download_artifact_api "$run_id"; then
    exit 0
  fi
  echo "Fork/cross-repo: Actions artifact zip not available; trying nightly.link for the same run..." >&2
fi

if try_download_nightly_run "$run_id"; then
  exit 0
fi

echo "::error::Failed to download manager for ${REPO}@${KSU_SHA} (run ${run_id}, source ${MANAGER_RUN_SOURCE}) via API and nightly.link" >&2
exit 1
