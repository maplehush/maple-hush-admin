#!/usr/bin/env bash
# 在服务器执行；六个参数由本次 Tag 的 runner 脚本提供。
set -Eeuo pipefail
# 防止提前退出被当成发布成功；仅完整部署或核实同版本健康后允许正常退出。
DEPLOYMENT_COMPLETED=false
verify_completion() {
  local code=$?
  trap - EXIT
  if [[ "$code" == 0 && "$DEPLOYMENT_COMPLETED" != true ]]; then
    echo '错误：远程脚本提前结束，尚未完成部署验证。' >&2
    code=1
  fi
  exit "$code"
}
trap verify_completion EXIT
umask 077

RELEASE_VERSION="${1:?缺少版本}"
RELEASE_COMMIT="${2:?缺少源码提交}"
FRONTEND_REF="${3:?缺少前端摘要引用}"
BACKEND_REF="${4:?缺少后端摘要引用}"
RELEASE_RUN="${5:?缺少运行编号}"
PRODUCTION_ORIGIN="${6:?缺少生产 Origin}"
VERSION_REGEX='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
[[ "$RELEASE_VERSION" =~ $VERSION_REGEX ]]
[[ "$RELEASE_COMMIT" =~ ^[0-9a-f]{40}$ ]]
[[ "$RELEASE_RUN" =~ ^[0-9]+-[0-9]+$ ]]
[[ "$FRONTEND_REF" =~ ^ccr\.ccs\.tencentyun\.com/maplehush/maple-hush-admin-frontend@sha256:[0-9a-f]{64}$ ]]
[[ "$BACKEND_REF" =~ ^ccr\.ccs\.tencentyun\.com/maplehush/maple-hush-admin-backend@sha256:[0-9a-f]{64}$ ]]
[[ "$PRODUCTION_ORIGIN" =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?$ ]]

APP_ROOT="/opt/maplehush"
ENV_FILE="$APP_ROOT/config/.env"
COMPOSE_FILE="$APP_ROOT/compose/compose.yaml"
# 此文件固定当前运行镜像的摘要，手动运维也必须加载它。
IMAGE_LOCK="$APP_ROOT/config/image-lock.yaml"
STATE_DIR="$APP_ROOT/releases"
SUCCESS_FILE="$STATE_DIR/last-success.tsv"
PENDING_FILE="$STATE_DIR/deployment-pending"

for command in docker flock python3 curl sha256sum; do
  command -v "$command" >/dev/null
done
[[ -f "$ENV_FILE" && -f "$COMPOSE_FILE" ]]
cd "$APP_ROOT"
install -d -m 700 "$STATE_DIR"

# GitHub 并发组之外，再加服务器文件锁，串行保护版本检查和容器更新。
# 人工执行部署时也要遵守同一把锁。
exec 9>"$STATE_DIR/production.lock"
flock -w 900 9
if [[ -e "$PENDING_FILE" ]]; then
  echo '错误：上次部署未完整完成。请先检查部署记录、容器和数据库，人工处理后再发布。'
  exit 1
fi

compose() {
  local args=(--env-file "$ENV_FILE" -f "$COMPOSE_FILE")
  if [[ -f "$IMAGE_LOCK" ]]; then args+=(-f "$IMAGE_LOCK"); fi
  env -u FRONTEND_VERSION -u BACKEND_VERSION docker compose "${args[@]}" "$@"
}

# 只读取版本，不 source 整个 .env，不把密码打印到工作流日志。
VERSION_VALUES=$(python3 - "$ENV_FILE" <<'PY'
import pathlib, re, sys
lines = pathlib.Path(sys.argv[1]).read_text().splitlines()
for name in ('FRONTEND_VERSION', 'BACKEND_VERSION'):
    values = [s.split('=', 1)[1] for s in lines if s.startswith(name + '=')]
    if len(values) != 1 or not re.fullmatch(r'v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', values[0]):
        raise SystemExit('版本变量必须唯一且形如 FRONTEND_VERSION=v1.2.3：' + name)
    print(values[0])
PY
)
mapfile -t CURRENT_VERSIONS <<< "$VERSION_VALUES"

# 在拿到服务器锁后比较现有版本，防止旧构建晚完成导致生产降级。
for current in "${CURRENT_VERSIONS[@]}"; do
  highest=$(printf '%s\n%s\n' "$current" "$RELEASE_VERSION" | sort -V | tail -n 1)
  if [[ "$highest" != "$RELEASE_VERSION" ]]; then
    echo "错误：生产已配置版本 $current，拒绝部署较低版本 $RELEASE_VERSION"
    exit 1
  fi
done

check_running_images() {
  local service reference cid actual expected
  for service in frontend backend; do
    if [[ "$service" == frontend ]]; then reference="$FRONTEND_REF"; else reference="$BACKEND_REF"; fi
    cid=$(compose ps -q "$service")
    [[ -n "$cid" ]]
    actual=$(docker inspect --format '{{.Image}}' "$cid")
    expected=$(docker image inspect --format '{{.Id}}' "$reference")
    [[ "$actual" == "$expected" ]]
    [[ "$(docker inspect --format '{{.State.Running}}' "$cid")" == true ]]
    [[ "$(docker inspect --format '{{.State.Health.Status}}' "$cid")" == healthy ]]
  done
}

if [[ -f "$SUCCESS_FILE" ]]; then
  IFS=$'\t' read -r last_version last_commit last_frontend last_backend < "$SUCCESS_FILE"
  [[ "$last_version" =~ $VERSION_REGEX ]]
  # .env 版本被人工改动后，先重新核对生产基线，不自动覆盖现场。
  [[ "${CURRENT_VERSIONS[0]}" == "$last_version" && "${CURRENT_VERSIONS[1]}" == "$last_version" ]]
  if [[ "$last_version" == "$RELEASE_VERSION" ]]; then
    [[ "$last_commit" == "$RELEASE_COMMIT" && "$last_frontend" == "$FRONTEND_REF" && "$last_backend" == "$BACKEND_REF" ]]
    [[ -f "$IMAGE_LOCK" ]]
    check_running_images
    curl --fail --silent --show-error --max-time 20 "$PRODUCTION_ORIGIN/healthz" >/dev/null
    curl --fail --silent --show-error --max-time 20 "$PRODUCTION_ORIGIN/api/auth/captcha" >/dev/null
    echo "同一版本、提交、摘要已成功部署且健康：$RELEASE_VERSION，无需重复更新"
    DEPLOYMENT_COMPLETED=true
    exit 0
  fi
else
  # 首次接管自动部署要求新版本高于现有两个版本；不能把同版本未知镜像当成已验证发布。
  if [[ "${CURRENT_VERSIONS[0]}" == "$RELEASE_VERSION" || "${CURRENT_VERSIONS[1]}" == "$RELEASE_VERSION" ]]; then
    echo '错误：没有成功发布记录，且待发布版本已存在于 .env。请核对部署基线或使用下一个新版本。'
    exit 1
  fi
fi

# 本脚本是已有环境的应用更新入口，不负责首次初始化五个服务。
compose config --quiet
for service in mysql redis nginx; do
  cid=$(compose ps -q "$service")
  [[ -n "$cid" && "$(docker inspect --format '{{.State.Running}}' "$cid")" == true ]]
done

RELEASE_DIR=$(mktemp -d "$STATE_DIR/${RELEASE_VERSION}-${RELEASE_RUN}-XXXXXXXX")
UPDATE_STARTED=false

on_error() {
  local code="$1"
  trap - ERR
  set +e
  compose ps > "$RELEASE_DIR/failed-status.txt" 2>&1
  compose logs --no-color --tail=200 frontend backend > "$RELEASE_DIR/failed-containers.log" 2>&1
  if [[ "$UPDATE_STARTED" == true ]]; then
    echo "更新阶段失败：保留待处理标记、候选配置和现场。不会假称已回滚。记录：$RELEASE_DIR"
    echo '请核对数据库迁移兼容性，再决定恢复旧镜像或修复后继续；不得盲目自动恢复数据库。'
  else
    echo "更新前失败：原 .env 和镜像锁未修改。记录：$RELEASE_DIR"
  fi
  exit "$code"
}
trap 'on_error $?' ERR

# 配置备份含敏感值，保存在 root 可访问的 0700 目录中，不上传到 GitHub。
install -m 600 "$ENV_FILE" "$RELEASE_DIR/previous.env"
if [[ -f "$IMAGE_LOCK" ]]; then install -m 600 "$IMAGE_LOCK" "$RELEASE_DIR/previous-image-lock.yaml"; fi
if [[ -f "$SUCCESS_FILE" ]]; then install -m 600 "$SUCCESS_FILE" "$RELEASE_DIR/previous-success.tsv"; fi
compose ps > "$RELEASE_DIR/previous-status.txt"

# 候选配置先在独立文件中完成校验和拉取，失败不修改正式 .env。
sed -e "s/^FRONTEND_VERSION=.*/FRONTEND_VERSION=$RELEASE_VERSION/" \
    -e "s/^BACKEND_VERSION=.*/BACKEND_VERSION=$RELEASE_VERSION/" \
    "$ENV_FILE" > "$RELEASE_DIR/candidate.env"
printf 'services:\n  frontend:\n    image: %s\n  backend:\n    image: %s\n' \
  "$FRONTEND_REF" "$BACKEND_REF" > "$RELEASE_DIR/candidate-image-lock.yaml"

candidate_compose() {
  env -u FRONTEND_VERSION -u BACKEND_VERSION docker compose \
    --env-file "$RELEASE_DIR/candidate.env" -f "$COMPOSE_FILE" \
    -f "$RELEASE_DIR/candidate-image-lock.yaml" "$@"
}
candidate_compose config --quiet
candidate_compose pull frontend backend

# 更新后端前备份数据库；备份失败立即停止，不启动新版本。
# 此命令适配当前 Compose 的环境变量密码方式，不依赖 secrets/ 文件。
# -T 只关闭伪终端；另关闭交互输入并隔离 stdin，避免读走后续脚本。
compose exec -T --interactive=false mysql sh -ec '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysqldump --user=root --single-transaction --routines --events --triggers \
    --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 "$MYSQL_DATABASE"
' </dev/null > "$RELEASE_DIR/database.sql.partial"
test -s "$RELEASE_DIR/database.sql.partial"
mv "$RELEASE_DIR/database.sql.partial" "$RELEASE_DIR/database.sql"
sha256sum "$RELEASE_DIR/database.sql" > "$RELEASE_DIR/database.sql.sha256"

# 从此处开始，失败可能已影响容器或数据库，保留标记并阻止后续自动发布。
printf '%s\n' "$RELEASE_DIR" > "$PENDING_FILE"
UPDATE_STARTED=true
install -m 600 "$RELEASE_DIR/candidate.env" "$ENV_FILE.next"
mv "$ENV_FILE.next" "$ENV_FILE"
install -m 600 "$RELEASE_DIR/candidate-image-lock.yaml" "$IMAGE_LOCK.next"
mv "$IMAGE_LOCK.next" "$IMAGE_LOCK"

# 固定摘要拉取完成后不再根据可变 tag 拉取；数据库、Redis、网关不重建。
compose up -d --no-deps --pull never --wait --wait-timeout 600 frontend backend
check_running_images
curl --fail --silent --show-error --max-time 20 "$PRODUCTION_ORIGIN/healthz" >/dev/null
curl --fail --silent --show-error --max-time 20 "$PRODUCTION_ORIGIN/api/auth/captcha" >/dev/null

# 仅在容器与外部 HTTPS 检查都通过后，原子更新成功发布记录。
printf '%s\t%s\t%s\t%s\n' "$RELEASE_VERSION" "$RELEASE_COMMIT" "$FRONTEND_REF" "$BACKEND_REF" > "$SUCCESS_FILE.next"
mv "$SUCCESS_FILE.next" "$SUCCESS_FILE"
rm -f "$PENDING_FILE"
trap - ERR
compose ps
echo "生产部署成功：$RELEASE_VERSION；记录：$RELEASE_DIR"
DEPLOYMENT_COMPLETED=true
