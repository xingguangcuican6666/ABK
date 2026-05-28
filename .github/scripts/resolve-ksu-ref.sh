#!/usr/bin/env bash
set -euo pipefail

KSU_VARIANT="${KSU_VARIANT:?KSU_VARIANT is required}"
KSU_BRANCH="${KSU_BRANCH:?KSU_BRANCH is required}"
CUSTOM_REF="${CUSTOM_REF:-}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

OFFICIAL_STABLE_REF="3f388ef137c78e1ca0c92c0ada3b8717cdcc4302"
SUKISU_STABLE_REF="c5af9eadac43b1f0b9751471be78e6eef681554b"
RESUKISU_STABLE_REF="b6706363b95525acd4007da19edadb1d41b2ea27"
SUKISU_REPO="SukiSU-Ultra/SukiSU-Ultra"

OFFICIAL_DEV_REF="96a72dd0107d660adcb273ab1911e1e77d87e562"
SUKISU_DEV_REF="aac170bcb86ce45516b3e2c0e2b32b57004b8f73"
RESUKISU_DEV_REF="fb771414f249ab886c09f2cfcbbf91c4b4dab2d1"

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
  local index=$((nabe - 1))
  local auth=()
  if [ -n "${GITHUB_TOKEN:-}" ] && [ "$repo" = "${GITHUB_REPOSITORY:-}" ]; then
    auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  fi
  curl -fsSL "${auth[@]}" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${repo}/actions/workflows/build-manager.yml/runs?status=success&branch=${branch}&per_page=${nabe}" \
    | jq -r --argjson idx "$index" '.workflow_runs[$idx].head_sha'
}

latest_sha_from_build_manager() {
  local repo="$1"
  local branch="$2"
  local sha
  sha="$(get_success_action_sha "$repo" "$branch" 1)"
  if [ -z "$sha" ] || [ "$sha" = "null" ]; then
    echo "::error::No successful build-manager run on ${repo}@${branch} (required for Latest)" >&2
    return 1
  fi
  printf '%s\n' "$sha"
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
  local repo primary secondary source_branch sha

  case "$KSU_VARIANT" in
    Official)
      repo="tiann/KernelSU"
      source_branch="main"
      sha="$(latest_sha_from_build_manager "$repo" "$source_branch")"
      ;;
    SukiSU)
      # GKI builds use main; builtin is for OnePlus only (oneplus-build.yml, not resolve-ksu-ref).
      repo="$SUKISU_REPO"
      source_branch="main"
      sha="$(latest_sha_from_build_manager "$repo" "$source_branch")"
      ;;
    ReSukiSU)
      repo="ReSukiSU/ReSukiSU"
      source_branch="main"
      sha="$(latest_sha_from_build_manager "$repo" "$source_branch")"
      ;;
    *)
      echo "::error::Unknown KSU variant for Latest: ${KSU_VARIANT}" >&2
      exit 1
      ;;
  esac

  RESOLVED_KSU_REPO="$repo"
  RESOLVED_KSU_SOURCE_BRANCH="$source_branch"
  RESOLVED_KSU_SHA="$sha"
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
  echo "Latest resolved: repo=${RESOLVED_KSU_REPO} branch=${RESOLVED_KSU_SOURCE_BRANCH} sha=${RESOLVED_KSU_SHA}"
fi
