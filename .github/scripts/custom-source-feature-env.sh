#!/usr/bin/env bash

# Sourced through BASH_ENV by selected workflow steps. Each successful step is
# squashed into a feature-labelled commit so a later failure can revert the
# whole feature without discarding commits belonging to other features.
if [[ "${ABK_SOURCE_MODE:-}" != "custom_git" || -z "${ABK_FEATURE_ID:-}" ]]; then
  return 0 2>/dev/null || exit 0
fi

feature_id="$ABK_FEATURE_ID"
source_root="${ABK_CUSTOM_SOURCE_ROOT:?missing ABK_CUSTOM_SOURCE_ROOT}"
state_dir="${ABK_CUSTOM_SOURCE_TRANSACTION_DIR:?missing transaction directory}"
status_file="${ABK_CUSTOM_SOURCE_FEATURE_STATUS:?missing feature status file}"
status_script="${GITHUB_WORKSPACE:?}/.github/scripts/custom-source-feature-status.py"
baseline="${ABK_CUSTOM_SOURCE_BASELINE:?missing custom source baseline}"
kernel_root="${KERNEL_ROOT:?missing KERNEL_ROOT}"
mkdir -p "$state_dir"

disabled_file="$state_dir/$feature_id.disabled"
external_paths="$state_dir/$feature_id.external.paths"
external_present="$state_dir/$feature_id.external.present"
external_root="$kernel_root/KernelSU"
external_start=""

snapshot_external_trees() {
  if [[ -f "$external_paths" ]]; then
    return 0
  fi
  printf '%s\n' KernelSU kernel > "$external_paths"
  : > "$external_present"
  local relative
  while IFS= read -r relative; do
    if [[ -e "$kernel_root/$relative" || -L "$kernel_root/$relative" ]]; then
      printf '%s\n' "$relative" >> "$external_present"
      local safe_name="${relative//\//_}"
      local archive="$state_dir/$feature_id.external.$safe_name.tar"
      tar -C "$kernel_root" -cf "$archive" -- "$relative"
      if git -C "$kernel_root/$relative" rev-parse --git-dir >/dev/null 2>&1; then
        : > "$state_dir/$feature_id.external.$safe_name.git"
      fi
    fi
  done < "$external_paths"
}

restore_external_trees() {
  local id="$1"
  local paths="$state_dir/$id.external.paths"
  if [[ ! -f "$paths" ]]; then
    return 0
  fi
  # Restore each non-Git tree independently. Pre-existing Git trees are
  # reverted by feature-labelled commits; restoring their tar snapshot would
  # erase later successful features.
  local relative
  while IFS= read -r relative; do
    local safe_name="${relative//\//_}"
    if [[ -f "$state_dir/$id.external.$safe_name.git" ]]; then
      continue
    fi
    rm -rf -- "${kernel_root:?}/$relative"
    local archive="$state_dir/$id.external.$safe_name.tar"
    if [[ -s "$archive" ]]; then
      tar -C "$kernel_root" -xf "$archive"
    fi
  done < "$paths"
}

commit_pending_changes() {
  local message="$1"
  git -C "$source_root" add -A
  if ! git -C "$source_root" diff --cached --quiet; then
    git -C "$source_root" commit -m "$message" >/dev/null
  fi
}

commit_external_pending_changes() {
  local message="$1"
  if ! git -C "$external_root" rev-parse --git-dir >/dev/null 2>&1; then
    return 0
  fi
  git -C "$external_root" config user.name "ABK Custom Source"
  git -C "$external_root" config user.email "abk-custom-source@localhost"
  git -C "$external_root" add -A
  if ! git -C "$external_root" diff --cached --quiet; then
    git -C "$external_root" commit -m "$message" >/dev/null
  fi
}

revert_feature_commits() {
  local id="$1"
  local commits=()
  mapfile -t commits < <(
    git -C "$source_root" log --format='%H%x09%s' "$baseline..HEAD" |
      awk -F '\t' -v subject="ABK custom source feature: $id" '$2 == subject { print $1 }'
  )
  if [[ ${#commits[@]} -eq 0 ]]; then
    return 0
  fi
  local commit
  for commit in "${commits[@]}"; do
    if ! git -C "$source_root" revert --no-commit "$commit"; then
      git -C "$source_root" revert --abort >/dev/null 2>&1 || true
      echo "::error::Unable to roll back custom source feature $id cleanly."
      return 1
    fi
  done
  commit_pending_changes "ABK rollback custom source feature: $id"
}

revert_external_feature_commits() {
  local id="$1"
  if ! git -C "$external_root" rev-parse --git-dir >/dev/null 2>&1; then
    return 0
  fi
  local commits=()
  mapfile -t commits < <(
    git -C "$external_root" log --format='%H%x09%s' |
      awk -F '\t' -v subject="ABK custom source feature: $id" '$2 == subject { print $1 }'
  )
  local commit
  for commit in "${commits[@]}"; do
    if ! git -C "$external_root" revert --no-commit "$commit"; then
      git -C "$external_root" revert --abort >/dev/null 2>&1 || true
      echo "::error::Unable to roll back external KernelSU feature $id cleanly."
      return 1
    fi
  done
  commit_external_pending_changes "ABK rollback custom source feature: $id"
}

revert_feature() {
  local id="$1"
  revert_feature_commits "$id"
  revert_external_feature_commits "$id"
  restore_external_trees "$id"
}

mark_dependency_skipped() {
  local id="$1"
  local requested
  requested="$(python3 - "$status_file" "$id" <<'PY'
import json
import sys
from pathlib import Path

status = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
value = status.get("requested", {}).get(sys.argv[2], False)
print("true" if value is True else "false")
PY
)"
  if [[ "$requested" != "true" ]]; then
    return 0
  fi
  revert_feature "$id"
  : > "$state_dir/$id.disabled"
  python3 "$status_script" mark \
    --file "$status_file" \
    --id "$id" \
    --state skipped \
    --reason-code dependency_skipped \
    --message "KernelSU integration was skipped"
}

if [[ "$feature_id" == "susfs" || "$feature_id" == "kpm" ]] && [[ -f "$state_dir/kernelsu.disabled" ]]; then
  echo "::warning::Custom source feature $feature_id depends on skipped KernelSU; skipping it."
  mark_dependency_skipped "$feature_id"
  exit 0
fi
if [[ -f "$disabled_file" ]]; then
  echo "::warning::Custom source feature $feature_id was already skipped; skipping this step."
  exit 0
fi

# Keep strict/shared edits outside this feature in their own commit.
commit_pending_changes "ABK checkpoint before $feature_id"
snapshot_external_trees
if git -C "$external_root" rev-parse --git-dir >/dev/null 2>&1; then
  commit_external_pending_changes "ABK checkpoint before $feature_id"
  external_start="$(git -C "$external_root" rev-parse HEAD)"
fi
step_start="$(git -C "$source_root" rev-parse HEAD)"

abk_custom_source_feature_exit() {
  local rc=$?
  trap - EXIT
  if [[ $rc -ne 0 ]]; then
    git -C "$source_root" reset --hard "$step_start"
    git -C "$source_root" clean -ffdx
    if [[ -n "$external_start" ]] && git -C "$external_root" rev-parse --git-dir >/dev/null 2>&1; then
      git -C "$external_root" reset --hard "$external_start"
      git -C "$external_root" clean -ffdx
    fi
    if [[ "$feature_id" == "kernelsu" ]]; then
      mark_dependency_skipped kpm || exit "$rc"
      mark_dependency_skipped susfs || exit "$rc"
    fi
    revert_feature "$feature_id" || exit "$rc"
    : > "$disabled_file"
    python3 "$status_script" mark \
      --file "$status_file" \
      --id "$feature_id" \
      --state skipped \
      --reason-code patch_failed \
      --message "${ABK_FEATURE_FAILURE_MESSAGE:-Feature integration failed and was rolled back}"
    echo "::warning::Custom source feature $feature_id failed and was rolled back."
    exit 0
  fi

  git -C "$source_root" add -A
  if [[ "$(git -C "$source_root" rev-parse HEAD)" != "$step_start" ]] ||
    ! git -C "$source_root" diff --cached --quiet; then
    git -C "$source_root" reset --soft "$step_start"
    git -C "$source_root" add -A
    git -C "$source_root" commit -m "ABK custom source feature: $feature_id" >/dev/null
  fi
  if git -C "$external_root" rev-parse --git-dir >/dev/null 2>&1; then
    git -C "$external_root" add -A
    if [[ -n "$external_start" && "$(git -C "$external_root" rev-parse HEAD)" != "$external_start" ]] ||
      ! git -C "$external_root" diff --cached --quiet; then
      if [[ -n "$external_start" ]]; then
        git -C "$external_root" reset --soft "$external_start"
      fi
      git -C "$external_root" add -A
      if ! git -C "$external_root" diff --cached --quiet; then
        git -C "$external_root" commit -m "ABK custom source feature: $feature_id" >/dev/null
      fi
    fi
  fi
  python3 "$status_script" mark --file "$status_file" --id "$feature_id" --state effective
}

trap abk_custom_source_feature_exit EXIT
