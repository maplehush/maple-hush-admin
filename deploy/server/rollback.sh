#!/usr/bin/env bash
# 人工回退前后端镜像；不恢复数据库，不重建基础服务。
set -Eeuo pipefail
umask 077

APP_ROOT="/opt/maplehush"
ENV_FILE="$APP_ROOT/config/.env"
COMPOSE_FILE="$APP_ROOT/compose/compose.yaml"
IMAGE_LOCK="$APP_ROOT/config/image-lock.yaml"
STATE_DIR="$APP_ROOT/releases"
SUCCESS_FILE="$STATE_DIR/last-success.tsv"
PENDING_FILE="$STATE_DIR/deployment-pending"
PRODUCTION_ORIGIN="https://admin.maplehush.cn"

die() {
    echo "错误：$*" >&2
    exit 1
}

# ==============================
# 1. 参数与运行条件
# ==============================
if [[ $# -ne 2 || "$2" != "--confirm-compatible" ]]; then
    echo "用法：sudo bash rollback.sh <发布备份目录> --confirm-compatible"
    echo "该参数表示已人工确认旧代码兼容当前数据库及运行配置。"
    exit 1
fi
[[ "$EUID" -eq 0 ]] || die "请使用 sudo 执行"
for command in docker flock realpath python3 curl; do
    command -v "$command" >/dev/null || die "缺少命令：$command"
done
[[ -d "$STATE_DIR" ]] || die "发布记录目录不存在"
for file in "$ENV_FILE" "$COMPOSE_FILE" "$IMAGE_LOCK" "$SUCCESS_FILE"; do
    [[ -f "$file" ]] || die "缺少当前生产文件：$file"
done
STATE_DIR=$(realpath -e -- "$STATE_DIR")
RELEASE_DIR=$(realpath -e -- "$1")
[[ -d "$RELEASE_DIR" ]] || die "备份路径不是目录"
[[ "$(dirname -- "$RELEASE_DIR")" == "$STATE_DIR" ]] ||
    die "请选择 releases 下的直接子目录"
cd "$APP_ROOT"

# ==============================
# 2. 文件锁与发布基线
# ==============================
exec 9>"$STATE_DIR/production.lock"
flock -w 900 9 || die "等待部署锁超时"

for file in previous.env previous-success.tsv; do
    [[ -f "$RELEASE_DIR/$file" ]] || die "备份目录缺少：$file"
done

HAS_PENDING=false
if [[ -e "$PENDING_FILE" ]]; then
    # 待处理标记必须是单行的有效路径。
    mapfile -t pending_lines < "$PENDING_FILE"
    [[ "${#pending_lines[@]}" -eq 1 && -n "${pending_lines[0]}" ]] ||
        die "待处理标记格式无效，需要人工核查"
    pending_dir=$(realpath -e -- "${pending_lines[0]}") ||
        die "待处理标记指向的目录无效"
    if [[ "$(basename -- "$pending_dir")" == rollback-* ]]; then
        die "上次回滚未完成，请人工核查 $pending_dir；不能直接重复运行"
    fi
    [[ "$pending_dir" == "$RELEASE_DIR" ]] ||
        die "存在另一项未完成发布：$pending_dir，请核对所选目录"
    HAS_PENDING=true
fi

# 严格读取成功记录，不执行其中任何内容。
read_record() {
    python3 - "$1" <<'PY'
import pathlib
import re
import sys

lines = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
if len(lines) != 1:
    raise SystemExit("成功记录必须只有一行")
fields = lines[0].split("\t")
patterns = [
    r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)",
    r"[0-9a-f]{40}",
    r"ccr\.ccs\.tencentyun\.com/maplehush/maple-hush-admin-frontend@sha256:[0-9a-f]{64}",
    r"ccr\.ccs\.tencentyun\.com/maplehush/maple-hush-admin-backend@sha256:[0-9a-f]{64}",
]
if len(fields) != 4 or any(
    re.fullmatch(pattern, value) is None
    for value, pattern in zip(fields, patterns)
):
    raise SystemExit("成功记录字段格式错误")
print("\n".join(fields))
PY
}

get_version() {
    python3 - "$1" "$2" <<'PY'
import pathlib
import re
import sys

path, name = sys.argv[1:]
lines = pathlib.Path(path).read_text(encoding="utf-8").splitlines()
values = [line.split("=", 1)[1] for line in lines if line.startswith(name + "=")]
pattern = r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
if len(values) != 1 or re.fullmatch(pattern, values[0]) is None:
    raise SystemExit("版本变量必须唯一且格式正确：" + name)
print(values[0])
PY
}

TARGET_RECORD=$(read_record "$RELEASE_DIR/previous-success.tsv")
CURRENT_RECORD=$(read_record "$SUCCESS_FILE")
mapfile -t target_values <<< "$TARGET_RECORD"
mapfile -t current_values <<< "$CURRENT_RECORD"
PREV_VERSION="${target_values[0]}"
PREV_FRONTEND_REF="${target_values[2]}"
PREV_BACKEND_REF="${target_values[3]}"

[[ "$(get_version "$RELEASE_DIR/previous.env" FRONTEND_VERSION)" == "$PREV_VERSION" ]] ||
    die "历史前端版本与目标成功记录不一致"
[[ "$(get_version "$RELEASE_DIR/previous.env" BACKEND_VERSION)" == "$PREV_VERSION" ]] ||
    die "历史后端版本与目标成功记录不一致"

CURRENT_FRONTEND=$(get_version "$ENV_FILE" FRONTEND_VERSION)
CURRENT_BACKEND=$(get_version "$ENV_FILE" BACKEND_VERSION)
if [[ "$HAS_PENDING" == true ]]; then
    # 失败发布可能已修改 .env，故改为核对最后成功基线的全部四个字段。
    [[ "$TARGET_RECORD" == "$CURRENT_RECORD" ]] ||
        die "选中发布的上一成功记录与生产最后成功记录不一致"
else
    [[ "$CURRENT_FRONTEND" == "${current_values[0]}" &&
       "$CURRENT_BACKEND" == "${current_values[0]}" ]] ||
        die "当前 .env 与最后成功记录不一致，请先人工核查"
fi

compose() {
    env -u FRONTEND_VERSION -u BACKEND_VERSION docker compose \
        --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -f "$IMAGE_LOCK" "$@"
}

# ==============================
# 3. 基础服务检查，失败不修改生产配置
# ==============================
compose config --quiet
for service in mysql redis nginx; do
    cid=$(compose ps -q "$service")
    [[ -n "$cid" ]] || die "$service 容器不存在或未运行"
    [[ "$(docker inspect --format '{{.State.Running}}' "$cid")" == true ]] ||
        die "$service 未运行，请先人工处理"
done

echo "当前版本：frontend=$CURRENT_FRONTEND，backend=$CURRENT_BACKEND"
echo "目标版本：$PREV_VERSION；目标提交：${target_values[1]}"
echo "保留当前数据库及运行配置，仅切换前后端镜像。"

# ==============================
# 4. 保存现场，注册失败处理
# ==============================
ROLLBACK_DIR=$(mktemp -d "$STATE_DIR/rollback-$(date +%Y%m%d%H%M%S)-XXXXXXXX")
UPDATE_STARTED=false
ROLLBACK_COMPLETED=false

on_exit() {
    local code=$?
    trap - EXIT
    set +e
    if [[ "$code" -eq 0 && "$ROLLBACK_COMPLETED" != true ]]; then
        code=1
        echo "错误：脚本提前结束，回滚尚未完成。" >&2
    fi
    if [[ "$code" -ne 0 ]]; then
        compose ps > "$ROLLBACK_DIR/failed-status.txt" 2>&1
        compose logs --no-color --tail=200 frontend backend \
            > "$ROLLBACK_DIR/failed-containers.log" 2>&1
        if [[ "$UPDATE_STARTED" == true ]]; then
            echo "回滚中途失败，保留现场和 deployment-pending。" >&2
            echo "必须人工介入，不要删除标记后直接重跑；不会自动恢复数据库。" >&2
        else
            echo "准备阶段失败，当前生产配置未替换，原有 pending 标记保留。" >&2
        fi
        echo "诊断目录：$ROLLBACK_DIR" >&2
    fi
    exit "$code"
}
trap on_exit EXIT

install -m 600 "$ENV_FILE" "$ROLLBACK_DIR/current.env"
install -m 600 "$IMAGE_LOCK" "$ROLLBACK_DIR/current-image-lock.yaml"
install -m 600 "$SUCCESS_FILE" "$ROLLBACK_DIR/current-success.tsv"
if [[ -f "$PENDING_FILE" ]]; then
    install -m 600 "$PENDING_FILE" "$ROLLBACK_DIR/previous-pending"
fi
install -m 600 "$RELEASE_DIR/previous-success.tsv" "$ROLLBACK_DIR/target-success.tsv"
printf '%s\n' "$RELEASE_DIR" > "$ROLLBACK_DIR/source-release-dir.txt"
compose ps > "$ROLLBACK_DIR/current-status.txt"

# ==============================
# 5. 候选配置、固定摘要与镜像拉取
# ==============================
sed -e "s/^FRONTEND_VERSION=.*/FRONTEND_VERSION=$PREV_VERSION/" \
    -e "s/^BACKEND_VERSION=.*/BACKEND_VERSION=$PREV_VERSION/" \
    "$ENV_FILE" > "$ROLLBACK_DIR/candidate.env"
printf 'services:\n  frontend:\n    image: %s\n  backend:\n    image: %s\n' \
    "$PREV_FRONTEND_REF" "$PREV_BACKEND_REF" \
    > "$ROLLBACK_DIR/candidate-image-lock.yaml"

candidate_compose() {
    env -u FRONTEND_VERSION -u BACKEND_VERSION docker compose \
        --env-file "$ROLLBACK_DIR/candidate.env" -f "$COMPOSE_FILE" \
        -f "$ROLLBACK_DIR/candidate-image-lock.yaml" "$@"
}
candidate_compose config --quiet
candidate_compose pull frontend backend

# ==============================
# 6. 切换前后端，不重建基础服务
# ==============================
printf '%s\n' "$ROLLBACK_DIR" > "$PENDING_FILE"
UPDATE_STARTED=true
install -m 600 "$ROLLBACK_DIR/candidate.env" "$ENV_FILE.next"
mv -f "$ENV_FILE.next" "$ENV_FILE"
install -m 600 "$ROLLBACK_DIR/candidate-image-lock.yaml" "$IMAGE_LOCK.next"
mv -f "$IMAGE_LOCK.next" "$IMAGE_LOCK"
compose up -d --no-deps --pull never --wait --wait-timeout 600 frontend backend

# ==============================
# 7. 运行镜像、健康状态及 HTTPS 验证
# ==============================
check_image() {
    local service="$1" reference="$2" cid actual expected
    cid=$(compose ps -q "$service")
    [[ -n "$cid" ]] || die "$service 容器不存在"
    actual=$(docker inspect --format '{{.Image}}' "$cid")
    expected=$(docker image inspect --format '{{.Id}}' "$reference")
    [[ "$actual" == "$expected" ]] || die "$service 运行镜像与目标摘要不一致"
    [[ "$(docker inspect --format '{{.State.Running}}' "$cid")" == true ]] ||
        die "$service 未运行"
    [[ "$(docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
        "$cid")" == healthy ]] || die "$service 健康检查未通过"
}
check_image frontend "$PREV_FRONTEND_REF"
check_image backend "$PREV_BACKEND_REF"
curl --fail --silent --show-error --max-time 20 "$PRODUCTION_ORIGIN/healthz" >/dev/null
curl --fail --silent --show-error --max-time 20 "$PRODUCTION_ORIGIN/api/auth/captcha" >/dev/null
compose ps

# ==============================
# 8. 更新成功状态，最后解除标记
# ==============================
install -m 600 "$ROLLBACK_DIR/target-success.tsv" "$SUCCESS_FILE.next"
mv -f "$SUCCESS_FILE.next" "$SUCCESS_FILE"
printf '%s\n' "$PREV_VERSION" > "$ROLLBACK_DIR/rollback-completed"
rm -f "$PENDING_FILE"
ROLLBACK_COMPLETED=true
echo "回滚成功：$PREV_VERSION"
echo "回滚记录：$ROLLBACK_DIR"
