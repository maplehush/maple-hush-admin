#!/bin/sh
set -eu
# Accept an origin only: no credentials, path, query or config injection.
case "${BACKEND_UPSTREAM:-}" in
    *[!A-Za-z0-9:./-]*) echo 'BACKEND_UPSTREAM must be an http(s) origin without a path or credentials.' >&2; exit 1 ;;
esac
if ! printf '%s\n' "${BACKEND_UPSTREAM:-}" | LC_ALL=C grep -Eq '^https?://[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]{1,5})?$'; then
    echo 'BACKEND_UPSTREAM must be an http(s) origin without a path or credentials.' >&2
    exit 1
fi
TRUSTED_PROXY_CIDR=${TRUSTED_PROXY_CIDR:-127.0.0.1/32}
case "$TRUSTED_PROXY_CIDR" in
    *[!0-9./]*) echo 'TRUSTED_PROXY_CIDR must be the verified gateway IPv4 CIDR.' >&2; exit 1 ;;
esac
if ! printf '%s\n' "$TRUSTED_PROXY_CIDR" | awk -F '[./]' '
    NF != 5 { exit 1 }
    { for (i=1; i<=5; i++) if ($i !~ /^[0-9]+$/) exit 1;
      for (i=1; i<=4; i++) if ($i > 255) exit 1;
      if ($5 > 32) exit 1 }'; then
    echo 'TRUSTED_PROXY_CIDR must be the verified gateway IPv4 CIDR.' >&2
    exit 1
fi
export BACKEND_UPSTREAM TRUSTED_PROXY_CIDR
# Preserve Nginx variables even if similar environment names exist.
envsubst '${BACKEND_UPSTREAM} ${TRUSTED_PROXY_CIDR}' \
    < /etc/nginx/templates/maple.conf.template > /tmp/nginx.conf
nginx -t -c /tmp/nginx.conf
exec nginx -c /tmp/nginx.conf -g 'daemon off;'
