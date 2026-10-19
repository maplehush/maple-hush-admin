#!/bin/sh
set -eu

# Run from backend/. Dev only; existing files are never overwritten.
umask 077
directory=${1:-./secrets}
mkdir -p "$directory"
chmod 700 "$directory"
write_once() {
    if [ ! -e "$directory/$1" ]; then
        printf '%s' "$2" > "$directory/$1"
        # File secrets are bind mounts. Non-root containers must read them;
        # host access is restricted by the parent directory's 0700.
        chmod 644 "$directory/$1"
    fi
}
write_once mysql_root_password 123456
write_once mysql_password 123456
write_once redis_password 123456
if [ ! -e "$directory/auth_jwt_secret" ]; then
    write_once auth_jwt_secret "$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
fi
write_once oss_access_key_id ''
write_once oss_access_key_secret ''
write_once oss_security_token ''
printf 'Development secret files ready in %s; existing files kept.\n' "$directory"
