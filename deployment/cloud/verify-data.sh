#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
compose=(docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml)

echo 'MySQL rows (users, file_upload, document_vectors, conversations, model_provider_configs):'
"${compose[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -N -B -usmart smart -e "SELECT (SELECT COUNT(*) FROM users), (SELECT COUNT(*) FROM file_upload), (SELECT COUNT(*) FROM document_vectors), (SELECT COUNT(*) FROM conversations), (SELECT COUNT(*) FROM model_provider_configs)"'

echo 'Newest conversation (id, answer bytes, reference mapping bytes; no content):'
"${compose[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -N -B -usmart smart -e "SELECT id, LENGTH(answer), LENGTH(reference_mappings_json) FROM conversations ORDER BY id DESC LIMIT 1"'

echo 'Elasticsearch indices (index, documents):'
"${compose[@]}" exec -T es sh -c \
  'curl -fsS -u "elastic:$ELASTIC_PASSWORD" "http://127.0.0.1:9200/_cat/indices?h=index,docs.count"'

echo 'Container status:'
"${compose[@]}" --profile app ps --format 'table {{.Service}}\t{{.State}}\t{{.Health}}'
