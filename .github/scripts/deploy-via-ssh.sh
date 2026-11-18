#!/usr/bin/env bash
# 在 GitHub runner 执行：连接服务器，交付并运行本次提交的部署脚本。
set -euo pipefail

for name in SERVER_HOST SERVER_USER SERVER_SSH_KEY SERVER_KNOWN_HOSTS \
  TCR_USERNAME TCR_PASSWORD RELEASE_VERSION RELEASE_COMMIT RELEASE_RUN \
  FRONTEND_REF BACKEND_REF PRODUCTION_ORIGIN; do
  if [[ -z "${!name:-}" ]]; then
    echo "::error::缺少部署配置：$name" >&2
    exit 1
  fi
done

# 这些值用于远程文件名；先限制格式，再用于命令参数。
[[ "$RELEASE_VERSION" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]
[[ "$RELEASE_COMMIT" =~ ^[0-9a-f]{40}$ ]]
[[ "$RELEASE_RUN" =~ ^[0-9]+-[0-9]+$ ]]
REPO_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
[[ "$(git -C "$REPO_ROOT" rev-parse HEAD)" == "$RELEASE_COMMIT" ]]
DEPLOY_SCRIPT="$REPO_ROOT/deploy/server/deploy.sh"
SCRIPT_DIGEST=$(sha256sum "$DEPLOY_SCRIPT")
SCRIPT_DIGEST=${SCRIPT_DIGEST%% *}

install -m 700 -d "$HOME/.ssh"
printf '%s\n' "$SERVER_SSH_KEY" > "$HOME/.ssh/id_ed25519"
printf '%s\n' "$SERVER_KNOWN_HOSTS" > "$HOME/.ssh/known_hosts"
chmod 600 "$HOME/.ssh/id_ed25519" "$HOME/.ssh/known_hosts"
if ! ssh-keygen -F "$SERVER_HOST" -f "$HOME/.ssh/known_hosts" >/dev/null; then
  echo '::error::SERVER_KNOWN_HOSTS 中没有 SERVER_HOST 对应的记录' >&2
  exit 1
fi

SSH_OPTS=(
  -i "$HOME/.ssh/id_ed25519"
  -o IdentitiesOnly=yes
  -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts"
  -o ConnectTimeout=15
)

# 密码只通过标准输入交给 Docker，不作为远程命令参数。
printf -v REMOTE_LOGIN_COMMAND \
  'sudo -n docker login ccr.ccs.tencentyun.com --username %q --password-stdin' \
  "$TCR_USERNAME"
printf '%s' "$TCR_PASSWORD" |
  ssh "${SSH_OPTS[@]}" "$SERVER_USER@$SERVER_HOST" "$REMOTE_LOGIN_COMMAND"

# bootstrap 本身通过 bash -c 参数传递；stdin 只传文件内容，不传可执行脚本流。
# root 创建临时文件，校验后按版本/提交/运行号保存，再关闭 stdin 执行。
BOOTSTRAP=$(cat <<'REMOTE'
set -euo pipefail
umask 077
expected_digest=$1
shift
script_dir=/opt/maplehush/scripts
install -d -m 700 -o root -g root "$script_dir"
upload=$(mktemp "$script_dir/.deploy-upload-XXXXXXXX")
trap 'rm -f -- "$upload"' EXIT
cat > "$upload"
printf '%s  %s\n' "$expected_digest" "$upload" | sha256sum --check --status
target="$script_dir/deploy-$1-$2-$5.sh"
if [[ -e "$target" ]]; then
  # 同一个运行标识不可覆盖为另一份脚本。
  printf '%s  %s\n' "$expected_digest" "$target" | sha256sum --check --status
  rm -f -- "$upload"
else
  chmod 700 "$upload"
  mv -T -- "$upload" "$target"
fi
trap - EXIT
echo "执行已校验的部署脚本：$target"
exec bash "$target" "$@" </dev/null
REMOTE
)

printf -v REMOTE_DEPLOY_COMMAND 'sudo -n bash -c %q -- %q %q %q %q %q %q %q' \
  "$BOOTSTRAP" "$SCRIPT_DIGEST" "$RELEASE_VERSION" "$RELEASE_COMMIT" \
  "$FRONTEND_REF" "$BACKEND_REF" "$RELEASE_RUN" "$PRODUCTION_ORIGIN"

ssh "${SSH_OPTS[@]}" "$SERVER_USER@$SERVER_HOST" \
  "$REMOTE_DEPLOY_COMMAND" < "$DEPLOY_SCRIPT"
