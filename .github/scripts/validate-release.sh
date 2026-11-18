#!/usr/bin/env bash
# 校验正式版本，并写入现有 version Job 输出。
set -euo pipefail

CURRENT_TAG="${GITHUB_REF_NAME}"
VERSION_REGEX='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'

# 1. 检查版本号格式
if [[ ! "$CURRENT_TAG" =~ $VERSION_REGEX ]]; then
  echo "::error::版本格式错误：$CURRENT_TAG"
  exit 1
fi

# 2. 获取 main 分支最新历史
git fetch --no-tags origin \
  +refs/heads/main:refs/remotes/origin/main

# 3. 确认当前 Tag 指向的代码属于 main 分支
if ! git merge-base --is-ancestor \
  "$GITHUB_SHA" refs/remotes/origin/main; then
  echo "::error::当前 Tag 对应的代码不属于 main 分支"
  exit 1
fi

# 4. 获取远程已有正式版本
REMOTE_TAG_REFS="$(git ls-remote --tags --refs origin)"

LATEST_TAG=$(
  printf '%s\n' "$REMOTE_TAG_REFS" |
  awk 'NF >= 2 {print $2}' |
  sed 's#refs/tags/##' |
  grep -E "$VERSION_REGEX" |
  grep -Fxv "$CURRENT_TAG" |
  sort -V |
  tail -n 1 || true
)

# 5. 检查正式版本是否递增
# 每次运行都检查；排除当前 tag 已允许最高版本重跑，不按运行次数绕过。
if [[ -n "$LATEST_TAG" ]]; then

  HIGHEST_TAG=$(
    printf '%s\n%s\n' "$LATEST_TAG" "$CURRENT_TAG" |
    sort -V |
    tail -n 1
  )

  if [[ "$HIGHEST_TAG" != "$CURRENT_TAG" ]]; then
    echo "::error::版本必须高于最新正式版本 $LATEST_TAG"
    exit 1
  fi

fi

echo "正式版本校验通过：$CURRENT_TAG"

# 将版本号传递给后续 Job
echo "version=$CURRENT_TAG" >> "$GITHUB_OUTPUT"
