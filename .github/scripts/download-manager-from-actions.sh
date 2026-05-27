#!/usr/bin/env bash
set -euo pipefail

REPO="${1:?repo owner/name required}"
BRANCH="${2:?branch required}"
VARIANT_DIR="${3:?output dir required}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"

# GITHUB_TOKEN from a fork workflow is only valid for that repository.
# Sending it to another repo's Actions API returns HTTP 401.
curl_auth_args=()
if [ -n "$GITHUB_TOKEN" ] && [ -n "$GITHUB_REPOSITORY" ] && [ "$REPO" = "$GITHUB_REPOSITORY" ]; then
  curl_auth_args=(-H "Authorization: Bearer $GITHUB_TOKEN")
fi

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

if unzip -l "$tmp_zip" | grep -qi '\.apk'; then
  unzip -o "$tmp_zip" -d "$VARIANT_DIR"
else
  unzip -o "$tmp_zip" -d "$VARIANT_DIR"
fi

if ! find "$VARIANT_DIR" -type f -name '*.apk' -print -quit | grep -q .; then
  echo "::error::Downloaded artifact did not contain an APK in ${VARIANT_DIR}" >&2
  find "$VARIANT_DIR" -type f | head -20 >&2 || true
  exit 1
fi

echo "Downloaded manager from ${REPO}@${BRANCH} (run ${run_id})"
