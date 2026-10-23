#!/bin/bash
set -euo pipefail
umask 077
ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
if [[ $# != 1 || $1 != *.sql ]]; then
    echo 'Usage: backup-mysql.sh /controlled/path/backup.sql' >&2
    exit 2
fi
command -v docker >/dev/null 2>&1 || { echo 'Docker CLI is required.' >&2; exit 1; }
mkdir -p -- "$(dirname -- "$1")"
DIRECTORY=$(cd -- "$(dirname -- "$1")" && pwd)
OUTPUT="$DIRECTORY/$(basename -- "$1")"
for file in "$OUTPUT" "$OUTPUT.sha256" "$OUTPUT.metadata" "$OUTPUT.images"; do
    [[ ! -e "$file" ]] || { echo 'Refusing to overwrite an existing backup artifact.' >&2; exit 1; }
done
PARTIAL="$OUTPUT.partial.$$"
trap 'rm -f -- "$PARTIAL"' EXIT
cd -- "$ROOT_DIR"
# Credentials remain inside the container, out of command arguments and backups.
# No --databases: the dump has no CREATE DATABASE/USE and can restore elsewhere.
docker compose exec -T mysql sh -ec '
    export MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    exec mysqldump --user=root --single-transaction --routines --events --triggers \
        --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 "$MYSQL_DATABASE"
' > "$PARTIAL"
[[ -s "$PARTIAL" ]] || { echo 'Backup is empty.' >&2; exit 1; }
FLYWAY=$(docker compose exec -T mysql sh -ec '
    export MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    exec mysql --user=root --batch --skip-column-names "$MYSQL_DATABASE" \
        --execute="SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1"
')
[[ -n "$FLYWAY" ]] || { echo 'Flyway version could not be recorded.' >&2; exit 1; }
docker compose images > "$OUTPUT.images"
printf 'application_commit=%s\nflyway_version=%s\ncreated_utc=%s\n' \
    "$(git rev-parse HEAD)" "$FLYWAY" "$(date -u +%FT%TZ)" > "$OUTPUT.metadata"
mv -- "$PARTIAL" "$OUTPUT"
sha256sum "$OUTPUT" > "$OUTPUT.sha256"
printf 'Backup and checksum ready: %s\n' "$OUTPUT"
