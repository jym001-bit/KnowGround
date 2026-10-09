#!/usr/bin/env bash
set -euo pipefail

test -s /tmp/rag-dist.tar
test -s /tmp/Caddyfile.rag-candidate
caddy validate --config /tmp/Caddyfile.rag-candidate >/dev/null

site=/opt/knowground/frontend/dist-rag
mkdir -p "$site"
tar -xf /tmp/rag-dist.tar -C "$site"
test -s "$site/index.html"

backup=/etc/caddy/Caddyfile.pre-rag-20261007
if [ ! -e "$backup" ]; then
  cp -p /etc/caddy/Caddyfile "$backup"
fi
cp /tmp/Caddyfile.rag-candidate /etc/caddy/Caddyfile
if ! caddy reload --config /etc/caddy/Caddyfile; then
  cp "$backup" /etc/caddy/Caddyfile
  caddy reload --config /etc/caddy/Caddyfile
  exit 1
fi

echo 'RAG entry activated; original Caddyfile backed up.'
