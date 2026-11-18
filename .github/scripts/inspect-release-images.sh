#!/usr/bin/env bash
# 核对已有版本镜像的来源提交，输出复用状态和摘要。
set -euo pipefail
inspect_release() {
  local component="$1" repository="$2" digest config errors
  errors=$(mktemp)
  if digest=$(crane digest "$repository:$RELEASE_VERSION" 2>"$errors"); then
    [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]
    config=$(crane config --platform linux/amd64 "$repository@$digest")
    if ! jq -e --arg sha "$GITHUB_SHA" \
      '.config.Labels["org.opencontainers.image.revision"] == $sha' \
      <<< "$config" >/dev/null; then
      echo "::error::已有 $component 镜像不属于当前提交，拒绝覆盖版本标签"
      exit 1
    fi
    echo "${component}_exists=true" >> "$GITHUB_OUTPUT"
    echo "${component}_digest=$digest" >> "$GITHUB_OUTPUT"
  elif grep -Eqi 'MANIFEST_UNKNOWN|manifest unknown' "$errors"; then
    echo "${component}_exists=false" >> "$GITHUB_OUTPUT"
  else
    cat "$errors" >&2
    echo "::error::无法确认 $component 镜像状态，停止发布"
    exit 1
  fi
  rm -f "$errors"
}
inspect_release frontend "$TCR_REGISTRY/$TCR_NAMESPACE/maple-hush-admin-frontend"
inspect_release backend "$TCR_REGISTRY/$TCR_NAMESPACE/maple-hush-admin-backend"
