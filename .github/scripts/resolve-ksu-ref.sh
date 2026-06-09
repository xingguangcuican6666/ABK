#!/usr/bin/env bash
# KernelSU ref resolution for GKI builds. Manager CI helpers below are also sourced by
# download-manager-from-actions.sh — keep them free of required env vars until main runs.

KSU_BUILD_MANAGER_WORKFLOW="build-manager.yml"
KSU_RELEASE_WORKFLOW="release.yml"

ksu_github_api_curl() {
  local repo="${KSU_API_REPO:-}"
  local auth=()
  if [ -n "${GITHUB_TOKEN:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ] && [ "$repo" = "$GITHUB_REPOSITORY" ]; then
    auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  fi
  curl -fsSL "${auth[@]}" -H "Accept: application/vnd.github+json" "$@"
}

ksu_workflow_run_id_for_head_sha() {
  local repo="$1"
  local workflow_file="$2"
  local sha="$3"
  local run_json run_id

  KSU_API_REPO="$repo"
  run_json="$(ksu_github_api_curl \
    "https://api.github.com/repos/${repo}/actions/workflows/${workflow_file}/runs?head_sha=${sha}&status=success&per_page=1")"
  run_id="$(printf '%s' "$run_json" | jq -r '.workflow_runs[0].id // empty')"
  if [ -n "$run_id" ] && [ "$run_id" != "null" ]; then
    printf '%s\n' "$run_id"
    return 0
  fi
  return 1
}

ksu_workflow_run_id_for_branch() {
  local repo="$1"
  local workflow_file="$2"
  local branch="$3"
  local run_json run_id

  KSU_API_REPO="$repo"
  run_json="$(ksu_github_api_curl \
    "https://api.github.com/repos/${repo}/actions/workflows/${workflow_file}/runs?branch=${branch}&status=success&per_page=1")"
  run_id="$(printf '%s' "$run_json" | jq -r '.workflow_runs[0].id // empty')"
  if [ -n "$run_id" ] && [ "$run_id" != "null" ]; then
    printf '%s\n' "$run_id"
    return 0
  fi
  return 1
}

ksu_main_head_sha() {
  local repo="$1"
  local sha

  KSU_API_REPO="$repo"
  sha="$(ksu_github_api_curl "https://api.github.com/repos/${repo}/git/ref/heads/main" \
    | jq -r '.object.sha // empty')"
  if [ -z "$sha" ] || [ "$sha" = "null" ]; then
    return 1
  fi
  printf '%s\n' "$sha"
}

ksu_latest_build_manager_sha_on_branch() {
  local repo="$1"
  local branch="$2"
  local nabe="${3:-1}"
  local index=$((nabe - 1))
  local sha

  KSU_API_REPO="$repo"
  sha="$(ksu_github_api_curl \
    "https://api.github.com/repos/${repo}/actions/workflows/${KSU_BUILD_MANAGER_WORKFLOW}/runs?status=success&branch=${branch}&per_page=${nabe}" \
    | jq -r --argjson idx "$index" '.workflow_runs[$idx].head_sha // empty')"
  if [ -z "$sha" ] || [ "$sha" = "null" ]; then
    return 1
  fi
  printf '%s\n' "$sha"
}

# Sets KSU_RESOLVED_LATEST_SHA and KSU_LATEST_SOURCE (no stdout; safe under set -u).
ksu_resolve_latest_sha() {
  local repo="$1"
  local main_head sha

  KSU_RESOLVED_LATEST_SHA=""
  KSU_LATEST_SOURCE=""

  main_head="$(ksu_main_head_sha "$repo")" || {
    echo "::error::Failed to read main HEAD for ${repo}" >&2
    return 1
  }

  if ksu_workflow_run_id_for_head_sha "$repo" "$KSU_RELEASE_WORKFLOW" "$main_head" >/dev/null; then
    KSU_LATEST_SOURCE="main-head-release"
    KSU_RESOLVED_LATEST_SHA="$main_head"
    return 0
  fi

  if ksu_workflow_run_id_for_head_sha "$repo" "$KSU_BUILD_MANAGER_WORKFLOW" "$main_head" >/dev/null; then
    KSU_LATEST_SOURCE="main-head-build-manager"
    KSU_RESOLVED_LATEST_SHA="$main_head"
    return 0
  fi

  sha="$(ksu_latest_build_manager_sha_on_branch "$repo" "main" 1)" || {
    echo "::error::No successful Release or build-manager run on ${repo}@main (required for Latest)" >&2
    return 1
  }
  KSU_LATEST_SOURCE="main-fallback"
  KSU_RESOLVED_LATEST_SHA="$sha"
  return 0
}

# Sets KSU_MANAGER_RUN_ID, MANAGER_RUN_SOURCE, and MANAGER_RUN_FALLBACK_MAIN (no stdout; safe under set -u).
ksu_find_manager_run_id() {
  local repo="$1"
  local sha="$2"
  local run_id

  if ! [[ "$sha" =~ ^[A-Fa-f0-9]{40}$ ]]; then
    echo "::error::Manager download requires a 40-char commit SHA, got: ${sha}" >&2
    return 1
  fi

  KSU_MANAGER_RUN_ID=""
  MANAGER_RUN_SOURCE=""
  MANAGER_RUN_FALLBACK_MAIN=0

  if run_id="$(ksu_workflow_run_id_for_head_sha "$repo" "$KSU_BUILD_MANAGER_WORKFLOW" "$sha")"; then
    MANAGER_RUN_SOURCE="build-manager"
    KSU_MANAGER_RUN_ID="$run_id"
    return 0
  fi

  if run_id="$(ksu_workflow_run_id_for_head_sha "$repo" "$KSU_RELEASE_WORKFLOW" "$sha")"; then
    MANAGER_RUN_SOURCE="release"
    KSU_MANAGER_RUN_ID="$run_id"
    return 0
  fi

  echo "::notice::No successful build-manager or Release run for ${repo} at head_sha=${sha}; falling back to latest successful build-manager on main" >&2
  if run_id="$(ksu_workflow_run_id_for_branch "$repo" "$KSU_BUILD_MANAGER_WORKFLOW" "main")"; then
    MANAGER_RUN_SOURCE="fallback-main"
    MANAGER_RUN_FALLBACK_MAIN=1
    KSU_MANAGER_RUN_ID="$run_id"
    return 0
  fi

  echo "::error::No successful build-manager run for ${repo} on main either" >&2
  return 1
}

if [[ "${BASH_SOURCE[0]}" != "${0}" ]]; then
  return 0 2>/dev/null || true
fi

set -euo pipefail

KSU_VARIANT="${KSU_VARIANT:?KSU_VARIANT is required}"
KSU_BRANCH="${KSU_BRANCH:?KSU_BRANCH is required}"
CUSTOM_REF="${CUSTOM_REF:-}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

OFFICIAL_STABLE_REF="3f388ef137c78e1ca0c92c0ada3b8717cdcc4302"
SUKISU_STABLE_REF="c5af9eadac43b1f0b9751471be78e6eef681554b"
RESUKISU_STABLE_REF="b6706363b95525acd4007da19edadb1d41b2ea27"
SUKISU_REPO="SukiSU-Ultra/SukiSU-Ultra"

OFFICIAL_DEV_REF="16a2a9c50c2936aaabd952bf2b29249c09eddb4b"
SUKISU_DEV_REF="3635a45b191bff175ac3ee3ff1e753c9c384acc0"
RESUKISU_DEV_REF="4e779d4d4a51c6c931e01bd48dbd64c1914f7844"

emit_env() {
  local key="$1"
  local value="$2"
  if [ -n "${GITHUB_ENV:-}" ]; then
    echo "${key}=${value}" >> "$GITHUB_ENV"
  fi
  export "${key}=${value}"
}

get_success_action_sha() {
  local repo="$1"
  local branch="$2"
  local nabe="$3"
  ksu_latest_build_manager_sha_on_branch "$repo" "$branch" "$nabe" || true
}

check_ref() {
  local repo="$1"
  local ref="$2"
  local msg
  if [[ "$ref" =~ ^[A-Fa-f0-9]{40}$ ]]; then
    msg="$(curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} \
      "https://api.github.com/repos/${repo}/commits/${ref}" | jq -r '.message // empty')"
  elif [[ "$ref" =~ ^[Vv]?[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    msg="$(curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} \
      "https://api.github.com/repos/${repo}/git/ref/tags/${ref}" | jq -r '.message // empty')"
  else
    msg="$(curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} \
      "https://api.github.com/repos/${repo}/branches/${ref}" | jq -r '.message // empty')"
  fi
  if echo "$msg" | grep -qi "no.*found"; then
    echo "::error::'${ref}' not found in ${repo}" >&2
    exit 1
  fi
}

resolve_latest() {
  local repo source_branch sha

  case "$KSU_VARIANT" in
    Official)
      repo="tiann/KernelSU"
      ;;
    SukiSU)
      # GKI builds use main; builtin is for OnePlus only (oneplus-build.yml, not resolve-ksu-ref).
      repo="$SUKISU_REPO"
      ;;
    ReSukiSU)
      repo="ReSukiSU/ReSukiSU"
      ;;
    *)
      echo "::error::Unknown KSU variant for Latest: ${KSU_VARIANT}" >&2
      exit 1
      ;;
  esac

  source_branch="main"
  if ! ksu_resolve_latest_sha "$repo"; then
    return 1
  fi

  RESOLVED_KSU_REPO="$repo"
  RESOLVED_KSU_SOURCE_BRANCH="$source_branch"
  RESOLVED_KSU_SHA="$KSU_RESOLVED_LATEST_SHA"
}

OFFICIAL_CUSTOM_REF=""
SUKISU_CUSTOM_REF=""
RESUKISU_CUSTOM_REF=""

if [ "$KSU_BRANCH" = "Custom(自定义)" ]; then
  if [[ "$CUSTOM_REF" =~ ^([A-Za-z0-9._/-]+):([0-9]+)$ ]]; then
    branch="${BASH_REMATCH[1]}"
    nabe="${BASH_REMATCH[2]}"
    case "$KSU_VARIANT" in
      Official) OFFICIAL_CUSTOM_REF="$(get_success_action_sha "tiann/KernelSU" "$branch" "$nabe")" ;;
      SukiSU) SUKISU_CUSTOM_REF="$(get_success_action_sha "$SUKISU_REPO" "$branch" "$nabe")" ;;
      ReSukiSU) RESUKISU_CUSTOM_REF="$(get_success_action_sha "ReSukiSU/ReSukiSU" "$branch" "$nabe")" ;;
    esac
  else
    case "$KSU_VARIANT" in
      Official) check_ref "tiann/KernelSU" "$CUSTOM_REF" ;;
      SukiSU) check_ref "$SUKISU_REPO" "$CUSTOM_REF" ;;
      ReSukiSU) check_ref "ReSukiSU/ReSukiSU" "$CUSTOM_REF" ;;
    esac
    OFFICIAL_CUSTOM_REF="$CUSTOM_REF"
    SUKISU_CUSTOM_REF="$CUSTOM_REF"
    RESUKISU_CUSTOM_REF="$CUSTOM_REF"
  fi
fi

case "$KSU_BRANCH" in
  "Stable(标准)")
    OFFICIAL_REF="$OFFICIAL_STABLE_REF"
    SUKISU_REF="$SUKISU_STABLE_REF"
    RESUKISU_REF="$RESUKISU_STABLE_REF"
    ;;
  "Dev(开发)")
    OFFICIAL_REF="$OFFICIAL_DEV_REF"
    SUKISU_REF="$SUKISU_DEV_REF"
    RESUKISU_REF="$RESUKISU_DEV_REF"
    ;;
  "Latest(最新)")
    resolve_latest
    OFFICIAL_REF="$RESOLVED_KSU_SHA"
    SUKISU_REF="$RESOLVED_KSU_SHA"
    RESUKISU_REF="$RESOLVED_KSU_SHA"
    ;;
  "Custom(自定义)")
    OFFICIAL_REF="$OFFICIAL_CUSTOM_REF"
    SUKISU_REF="$SUKISU_CUSTOM_REF"
    RESUKISU_REF="$RESUKISU_CUSTOM_REF"
    ;;
  *)
    echo "::error::Unknown KSU branch: ${KSU_BRANCH}" >&2
    exit 1
    ;;
esac

case "$KSU_VARIANT" in
  Official)
    BRANCH="${OFFICIAL_REF}"
    RESOLVED_KSU_REPO="${RESOLVED_KSU_REPO:-tiann/KernelSU}"
    ;;
  SukiSU)
    BRANCH="${SUKISU_REF}"
    RESOLVED_KSU_REPO="${RESOLVED_KSU_REPO:-$SUKISU_REPO}"
    ;;
  ReSukiSU)
    BRANCH="${RESUKISU_REF}"
    RESOLVED_KSU_REPO="${RESOLVED_KSU_REPO:-ReSukiSU/ReSukiSU}"
    ;;
  *)
    echo "::error::Unknown KSU variant: ${KSU_VARIANT}" >&2
    exit 1
    ;;
esac

if [ -z "$BRANCH" ] || [ "$BRANCH" = "null" ] || [[ "$BRANCH" == *"null"* ]]; then
  echo "::error::Failed to resolve KernelSU ref (branch=${KSU_BRANCH} variant=${KSU_VARIANT})" >&2
  exit 1
fi

emit_env "EFFECTIVE_KSU_BRANCH" "$KSU_BRANCH"
emit_env "BRANCH" "$BRANCH"
emit_env "RESOLVED_KSU_SHA" "${RESOLVED_KSU_SHA:-$OFFICIAL_REF}"
emit_env "RESOLVED_KSU_SOURCE_BRANCH" "${RESOLVED_KSU_SOURCE_BRANCH:-}"
emit_env "RESOLVED_KSU_REPO" "${RESOLVED_KSU_REPO:-}"

echo "KSU branch: ${KSU_BRANCH} -> ${BRANCH}"
if [ "$KSU_BRANCH" = "Latest(最新)" ]; then
  emit_env "KSU_LATEST_SOURCE" "${KSU_LATEST_SOURCE:-unknown}"
  echo "Latest resolved: repo=${RESOLVED_KSU_REPO} branch=${RESOLVED_KSU_SOURCE_BRANCH} sha=${RESOLVED_KSU_SHA} source=${KSU_LATEST_SOURCE:-unknown}"
fi
