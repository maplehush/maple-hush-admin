#!/bin/bash
set -euo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
FRONTEND_DIR=$(cd -- "$SCRIPT_DIR/../.." && pwd)
IMAGE_NAME=${1:-maple-hush-frontend:local}
command -v docker >/dev/null 2>&1 || { echo 'Docker CLI is required.' >&2; exit 1; }
# Frozen installation/build run inside Docker. Failed builds preserve old services.
docker build --file "$SCRIPT_DIR/Dockerfile" --tag "$IMAGE_NAME" "$FRONTEND_DIR"
printf 'Image built: %s. Start/recreate it using frontend/docker-compose.yml.\n' "$IMAGE_NAME"
