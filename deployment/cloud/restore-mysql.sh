#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 <mysqldump.sql>" >&2
  exit 2
fi

dump=$(realpath "$1")
cd "$(dirname "$0")/../.."
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -usmart smart' < "$dump"

docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -N -usmart smart -e "SHOW TABLES"' | wc -l
