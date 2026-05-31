#!/usr/bin/env bash
set -euo pipefail

REPO="${1:?repo owner/name required}"
KSU_SHA="${2:?commit SHA required}"
VARIANT_DIR="${3:?output dir required}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"

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

github_api_curl() {
  local auth=()
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  fi
  curl -fsSL "${auth[@]}" -H "Accept: application/vnd.github+json" "$@"
}

workflow_run_id_from_json() {
  printf '%s' "$1" | jq -r '.workflow_runs[0].id // empty'
}

find_build_manager_run_id() {
  local sha="$1"
  local run_json run_id
  if ! [[ "$sha" =~ ^[A-Fa-f0-9]{40}$ ]]; then
    echo "::error::Manager download requires a 40-char commit SHA, got: ${sha}" >&2
    return 1
  fi
  run_json="$(github_api_curl \
    "https://api.github.com/repos/${REPO}/actions/workflows/build-manager.yml/runs?head_sha=${sha}&status=success&per_page=1")"
  run_id="$(workflow_run_id_from_json "$run_json")"
  if [ -n "$run_id" ] && [ "$run_id" != "null" ]; then
    MANAGER_RUN_FALLBACK_MAIN=0
    printf '%s\n' "$run_id"
    return 0
  fi

  echo "::notice::No successful build-manager run for ${REPO} at head_sha=${sha}; falling back to latest successful run on main" >&2
  run_json="$(github_api_curl \
    "https://api.github.com/repos/${REPO}/actions/workflows/build-manager.yml/runs?branch=main&status=success&per_page=1")"
  run_id="$(workflow_run_id_from_json "$run_json")"
  if [ -z "$run_id" ] || [ "$run_id" = "null" ]; then
    echo "::error::No successful build-manager run for ${REPO} on main either" >&2
    printf '%s' "$run_json" | jq -r '.message // empty' >&2 || true
    return 1
  fi
  MANAGER_RUN_FALLBACK_MAIN=1
  printf '%s\n' "$run_id"
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
  local artifact_name tmp_zip artifact_id artifacts_json artifact_url

  if [ -z "${GITHUB_TOKEN:-}" ]; then
    return 1
  fi

  artifact_name="$(manager_artifact_name)"
  artifacts_json="$(github_api_curl \
    "https://api.github.com/repos/${REPO}/actions/runs/${run_id}/artifacts")"
  artifact_id="$(printf '%s' "$artifacts_json" | jq -r --arg name "$artifact_name" '
    (.artifacts[] | select(.name == $name) | .id) // empty')"
  if [ -z "$artifact_id" ] || [ "$artifact_id" = "null" ]; then
    echo "::warning::No manager artifact on run ${run_id} for ${REPO}" >&2
    return 1
  fi

  mkdir -p "$VARIANT_DIR"
  tmp_zip="$(mktemp "${TMPDIR:-/tmp}/manager-XXXXXX.zip")"
  if ! github_api_curl -L \
    "https://api.github.com/repos/${REPO}/actions/artifacts/${artifact_id}/zip" \
    -o "$tmp_zip"; then
    rm -f "$tmp_zip"
    return 1
  fi
  extract_apk_from_zip "$tmp_zip"
  echo "Downloaded manager from ${REPO}@${KSU_SHA} (run ${run_id}) via Actions API"
}

try_download_nightly_run() {
  local run_id="$1"
  local zip_name url tmp_zip
  zip_name="$(nightly_artifact_zip)"
  url="https://nightly.link/${REPO}/actions/runs/${run_id}/${zip_name}"
  mkdir -p "$VARIANT_DIR"
  tmp_zip="$(mktemp "${TMPDIR:-/tmp}/manager-XXXXXX.zip")"

  echo "Downloading manager via nightly.link (run ${run_id}, sha ${KSU_SHA}): ${url}"
  if ! curl -fsSL -o "$tmp_zip" "$url"; then
    rm -f "$tmp_zip"
    return 1
  fi
  extract_apk_from_zip "$tmp_zip"
  echo "Downloaded manager from ${REPO}@${KSU_SHA} (run ${run_id}) via nightly.link"
}

MANAGER_RUN_FALLBACK_MAIN=0
run_id="$(find_build_manager_run_id "$KSU_SHA")"
if [ "${MANAGER_RUN_FALLBACK_MAIN}" = "1" ]; then
  echo "::notice::Manager APK from latest successful build-manager on main (KSU ref was ${KSU_SHA})"
fi
echo "build-manager run for ${REPO}@${KSU_SHA}: https://github.com/${REPO}/actions/runs/${run_id}"

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

echo "::error::Failed to download manager for ${REPO}@${KSU_SHA} (run ${run_id}) via API and nightly.link" >&2
exit 1
