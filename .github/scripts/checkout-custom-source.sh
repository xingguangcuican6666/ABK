#!/usr/bin/env bash
set -euo pipefail

# This script runs in its own process. Git authentication exists only in its
# environment, never in the runner's global config or the checkout.
if [ "${SOURCE_PRIVATE:-false}" = "true" ]; then
  if [ -z "${ABK_CUSTOM_SOURCE_GITHUB_TOKEN:-}" ]; then
    echo "::error::缺少 ABK_CUSTOM_SOURCE_GITHUB_TOKEN，无法访问私有源码仓。"
    exit 1
  fi
  access_header="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$ABK_CUSTOM_SOURCE_GITHUB_TOKEN" | base64 | tr -d '\r\n')"
  printf '::add-mask::%s\n' "$access_header"
  config_count="${GIT_CONFIG_COUNT:-0}"
  # Reset inherited headers before adding this request's credentials.
  export "GIT_CONFIG_KEY_${config_count}=http.https://github.com/.extraheader"
  export "GIT_CONFIG_VALUE_${config_count}="
  export "GIT_CONFIG_KEY_$((config_count + 1))=http.https://github.com/.extraheader"
  export "GIT_CONFIG_VALUE_$((config_count + 1))=$access_header"
  export GIT_CONFIG_COUNT="$((config_count + 2))"
fi

rm -rf common
git init common
git -C common remote add origin "$SOURCE_REPO"
git -C common fetch --depth=1 origin "$SOURCE_COMMIT"
git -C common checkout --detach "$SOURCE_COMMIT"
