#!/bin/sh
set -eu
umask 077

# Redis has no official PASSWORD_FILE switch. Its password stays out of argv;
# generate a private ephemeral config, running as the container's redis user.
password_file=${REDIS_PASSWORD_FILE:-/run/secrets/redis_password}
password=$(cat "$password_file")
if [ -z "$password" ] || printf '%s' "$password" | LC_ALL=C grep -q '[[:cntrl:]]'; then
    echo 'Redis requires a non-empty, single-line password file.' >&2
    exit 1
fi
escaped=$(printf '%s' "$password" | sed 's/\\/\\\\/g; s/"/\\"/g')
config=${REDIS_CONFIG_FILE:-/tmp/maple-redis.conf}
cat > "$config" <<EOF
bind 0.0.0.0
protected-mode yes
port 6379
save ""
appendonly no
maxmemory 128mb
maxmemory-policy noeviction
dir /data
requirepass "$escaped"
EOF
unset password escaped
exec redis-server "$config"
