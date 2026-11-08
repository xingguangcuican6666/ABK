#!/usr/bin/env bash
# Pick the newest successful lkm.yml run that still has downloadable artifacts.
#
# The app bundles LKMs straight out of ABK_control_module, so it needs a run whose
# artifacts are still live -- not merely the newest run that concluded "success".
# Artifact retention is 90 days, and an expired run keeps reporting
# conclusion=success with an empty artifact list, so the two conditions have to be
# checked separately.
#
# The run list must be ordered explicitly. GitHub's Actions listing is eventually
# consistent across replicas and "?status=success&per_page=1" does not guarantee the
# newest run comes back first -- asking repeatedly can return a months-old run and
# then the current one. Sorting by created_at ourselves makes the pick deterministic.
#
# Emits run_id/head_sha/artifact_count on stdout, one key=value per line, so callers
# can append it straight to $GITHUB_OUTPUT. Exits non-zero when no usable run exists,
# with the reason on stderr.

set -euo pipefail

: "${LKM_REPO:?LKM_REPO must be set to <owner>/<name>}"
: "${LKM_WORKFLOW:=lkm.yml}"
: "${LKM_SCAN_LIMIT:=20}"
: "${LKM_ARTIFACT_LIMIT:=100}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required to read cross-repo runs}"

api_get() {
  local url="$1"
  local tmp
  tmp="$(mktemp)"
  if curl -fsSL \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    "$url" -o "$tmp"; then
    cat "$tmp"
    rm -f "$tmp"
    return 0
  fi
  rm -f "$tmp"
  curl -fsSL -H "Accept: application/vnd.github+json" "$url"
}

runs_json="$(api_get "https://api.github.com/repos/${LKM_REPO}/actions/workflows/${LKM_WORKFLOW}/runs?status=success&per_page=${LKM_SCAN_LIMIT}")" || {
  echo "::error::failed to list ${LKM_WORKFLOW} runs for ${LKM_REPO}" >&2
  exit 1
}

candidate_count="$(printf '%s' "$runs_json" | jq -r '[.workflow_runs[]? | select(.status == "completed" and .conclusion == "success")] | length')"
if [ "$candidate_count" -eq 0 ]; then
  echo "::error::no successful ${LKM_WORKFLOW} run found for ${LKM_REPO}" >&2
  exit 1
fi

# created_at descending, id descending as a tiebreak so two runs stamped in the same
# second still resolve the same way on every call. created_at is a fixed-width UTC
# string, so plain string order is chronological order.
candidates="$(printf '%s' "$runs_json" | jq -r '
  [ .workflow_runs[]?
    | select(.status == "completed" and .conclusion == "success")
  ]
  | sort_by(.created_at, .id)
  | reverse
  | .[].id')"

while read -r run_id; do
  [ -n "$run_id" ] || continue

  artifacts_json="$(api_get "https://api.github.com/repos/${LKM_REPO}/actions/runs/${run_id}/artifacts?per_page=${LKM_ARTIFACT_LIMIT}")" || artifacts_json=""

  # expired=true means the archive is gone even though the run is still listed as
  # successful; a run whose every artifact has aged out cannot be bundled.
  live_count="$(printf '%s' "$artifacts_json" | jq -r '[.artifacts[]? | select(.expired != true)] | length' 2>/dev/null || echo 0)"
  if [ "$live_count" -eq 0 ]; then
    echo "run ${run_id}: no live artifacts, trying older run" >&2
    continue
  fi

  head_sha="$(printf '%s' "$runs_json" | jq -r --argjson id "$run_id" '.workflow_runs[] | select(.id == $id) | .head_sha // empty')"

  echo "run_id=${run_id}"
  echo "head_sha=${head_sha}"
  echo "artifact_count=${live_count}"
  exit 0
done <<< "$candidates"

echo "::error::every successful ${LKM_WORKFLOW} run for ${LKM_REPO} has expired artifacts; re-run ${LKM_WORKFLOW}" >&2
exit 1