#!/usr/bin/env bash
# Pick primary or secondary branch when secondary is strictly ahead of primary on GitHub.

pick_secondary_from_compare_json() {
  local primary="$1"
  local secondary="$2"
  local json="$3"
  local ahead

  ahead="$(printf '%s' "$json" | jq -r '.ahead_by // 0')"
  if [ "${ahead:-0}" -gt 0 ]; then
    printf '%s\n' "$secondary"
  else
    printf '%s\n' "$primary"
  fi
}

pick_secondary_if_ahead() {
  local repo="$1"
  local primary="$2"
  local secondary="$3"
  local token="${4:-}"
  local auth=()
  local json

  if [ -n "$token" ]; then
    auth=(-H "Authorization: Bearer $token")
  fi

  json="$(curl -fsSL "${auth[@]}" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${repo}/compare/${primary}...${secondary}")" || return 1

  pick_secondary_from_compare_json "$primary" "$secondary" "$json"
}
