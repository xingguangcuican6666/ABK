#!/usr/bin/env bash
set -euo pipefail

REPO="${1:?repo owner/name required}"
BRANCH="${2:?branch required}"
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

download_via_nightlylink() {
  local zip_name url tmp_zip
  zip_name="$(nightly_artifact_zip)"
  url="https://nightly.link/${REPO}/workflows/build-manager/${BRANCH}/${zip_name}"
  mkdir -p "$VARIANT_DIR"
  tmp_zip="$(mktemp "${TMPDIR:-/tmp}/manager-XXXXXX.zip")"

  echo "Downloading manager via nightly.link: ${url}"
  if ! curl -fsSL -o "$tmp_zip" "$url"; then
    rm -f "$tmp_zip"
    echo "::error::nightly.link download failed for ${REPO}@${BRANCH} (${zip_name})" >&2
    return 1
  fi

  unzip -o "$tmp_zip" -d "$VARIANT_DIR"
  rm -f "$tmp_zip"
}

# Fork GITHUB_TOKEN cannot download another repo's Actions artifacts (HTTP 401).
# Stable ReSukiSU already uses nightly.link in get-manager.yml; Latest uses the same
# proxy with the resolved branch (Latest uses main for all GKI variants).
if [ -z "$GITHUB_REPOSITORY" ] || [ "$REPO" != "$GITHUB_REPOSITORY" ]; then
  download_via_nightlylink
  if ! find "$VARIANT_DIR" -type f -name '*.apk' -print -quit | grep -q .; then
    echo "::error::Downloaded artifact did not contain an APK in ${VARIANT_DIR}" >&2
    find "$VARIANT_DIR" -type f | head -20 >&2 || true
    exit 1
  fi
  echo "Downloaded manager from ${REPO}@${BRANCH} via nightly.link"
  exit 0
fi

curl_auth_args=(-H "Authorization: Bearer $GITHUB_TOKEN")

github_api_curl() {
  curl -fsSL "${curl_auth_args[@]}" -H "Accept: application/vnd.github+json" "$@"
}

runs_json="$(github_api_curl \
  "https://api.github.com/repos/${REPO}/actions/workflows/build-manager.yml/runs?status=success&branch=${BRANCH}&per_page=1")"

run_id="$(printf '%s' "$runs_json" | jq -r '.workflow_runs[0].id // empty')"
if [ -z "$run_id" ]; then
  echo "::error::No successful build-manager run for ${REPO} on branch ${BRANCH}" >&2
  printf '%s\n' "$runs_json" | jq -r '.message // empty' >&2 || true
  exit 1
fi

artifacts_json="$(github_api_curl \
  "https://api.github.com/repos/${REPO}/actions/runs/${run_id}/artifacts")"

artifact_id="$(printf '%s' "$artifacts_json" | jq -r '
  ([.artifacts[]
    | select((.name | ascii_downcase | contains("manager"))
      or (.name | ascii_downcase | endswith(".apk")))]
  | .[0].id) // .artifacts[0].id // empty')"

if [ -z "$artifact_id" ]; then
  echo "::error::No artifacts on build-manager run ${run_id} for ${REPO}@${BRANCH}" >&2
  exit 1
fi

mkdir -p "$VARIANT_DIR"
tmp_zip="$(mktemp "${TMPDIR:-/tmp}/manager-XXXXXX.zip")"
trap 'rm -f "$tmp_zip"' EXIT

github_api_curl -L \
  "https://api.github.com/repos/${REPO}/actions/artifacts/${artifact_id}/zip" \
  -o "$tmp_zip"

unzip -o "$tmp_zip" -d "$VARIANT_DIR"

if ! find "$VARIANT_DIR" -type f -name '*.apk' -print -quit | grep -q .; then
  echo "::error::Downloaded artifact did not contain an APK in ${VARIANT_DIR}" >&2
  find "$VARIANT_DIR" -type f | head -20 >&2 || true
  exit 1
fi

echo "Downloaded manager from ${REPO}@${BRANCH} (run ${run_id})"
