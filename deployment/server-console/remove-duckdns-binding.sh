#!/usr/bin/env bash
set -euo pipefail

stamp=pre-duckdns-removal-20261008
for path in /etc/caddy/Caddyfile /etc/authelia/configuration.yml /opt/knowground/.env.cloud; do
  test -s "$path"
  test ! -e "$path.$stamp"
  cp -p "$path" "$path.$stamp"
done

rollback() {
  set +e
  cp "/etc/caddy/Caddyfile.$stamp" /etc/caddy/Caddyfile
  cp "/etc/authelia/configuration.yml.$stamp" /etc/authelia/configuration.yml
  cp "/opt/knowground/.env.cloud.$stamp" /opt/knowground/.env.cloud
  caddy reload --config /etc/caddy/Caddyfile
  docker restart server-console-auth >/dev/null
  cd /opt/knowground
  docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app up -d --no-deps --force-recreate api
}
trap rollback ERR

cp /tmp/Caddyfile.no-duckdns /etc/caddy/Caddyfile
caddy reload --config /etc/caddy/Caddyfile
cp /tmp/authelia.no-duckdns.yml /etc/authelia/configuration.yml
chmod 0600 /etc/authelia/configuration.yml
docker restart server-console-auth >/dev/null
cp /tmp/knowground.no-duckdns.env /opt/knowground/.env.cloud
chmod 0600 /opt/knowground/.env.cloud
cd /opt/knowground
docker compose --env-file .env.cloud -f deployment/cloud/compose.yaml --profile app up -d --no-deps --force-recreate api

trap - ERR
echo 'Former DuckDNS binding removed from Caddy, Authelia, and API origins.'
