#!/bin/bash
set -euo pipefail
umask 077
ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
if [[ $# != 2 || ! $2 =~ ^maple_hush_restore_[a-z0-9_]+$ ]]; then
    echo 'Usage: restore-mysql.sh backup.sql maple_hush_restore_<isolated_name>' >&2
    exit 2
fi
INPUT=$1
TARGET=$2
[[ -s "$INPUT" && -f "$INPUT.sha256" ]] || { echo 'Backup and checksum are required.' >&2; exit 1; }
EXPECTED=$(awk 'NR==1 { print $1 }' "$INPUT.sha256")
[[ $EXPECTED =~ ^[a-f0-9]{64}$ ]] || { echo 'Invalid checksum.' >&2; exit 1; }
ACTUAL=$(sha256sum "$INPUT")
[[ ${ACTUAL%% *} == "$EXPECTED" ]] || { echo 'Backup checksum mismatch.' >&2; exit 1; }
INPUT=$(cd -- "$(dirname -- "$INPUT")" && pwd)/$(basename -- "$INPUT")
command -v docker >/dev/null 2>&1 || { echo 'Docker CLI is required.' >&2; exit 1; }
cd -- "$ROOT_DIR"
# Only a newly created isolated schema is accepted. Existing schemas are not erased.
docker compose exec -T --env "RESTORE_DATABASE=$TARGET" mysql sh -ec '
    test "$RESTORE_DATABASE" != "$MYSQL_DATABASE"
    export MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    mysql --user=root --execute="CREATE DATABASE \`$RESTORE_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    exec mysql --user=root --default-character-set=utf8mb4 "$RESTORE_DATABASE"
' < "$INPUT"
printf 'Restored to isolated schema: %s. Verify before switching any application.\n' "$TARGET"
