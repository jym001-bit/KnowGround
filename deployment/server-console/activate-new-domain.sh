#!/usr/bin/env bash
set -euo pipefail

stamp=pre-domain-move-20261008
for path in /etc/caddy/Caddyfile /etc/authelia/configuration.yml /etc/server-console/config.json; do
  test -s "$path"
  test ! -e "$path.$stamp"
  cp -p "$path" "$path.$stamp"
done

rollback() {
  set +e
  cp "/etc/caddy/Caddyfile.$stamp" /etc/caddy/Caddyfile
  cp "/etc/authelia/configuration.yml.$stamp" /etc/authelia/configuration.yml
  cp "/etc/server-console/config.json.$stamp" /etc/server-console/config.json
  docker restart server-console-auth >/dev/null
  systemctl restart server-console
  caddy reload --config /etc/caddy/Caddyfile
}
trap rollback ERR

cp /tmp/authelia.panel-candidate.yml /etc/authelia/configuration.yml
chmod 0600 /etc/authelia/configuration.yml
docker restart server-console-auth >/dev/null
cp /tmp/server-console.panel-candidate.json /etc/server-console/config.json
systemctl restart server-console
cp /tmp/Caddyfile.panel-candidate /etc/caddy/Caddyfile
caddy reload --config /etc/caddy/Caddyfile

trap - ERR
echo 'New domain panel routes and authentication activated; old domain remains for transition.'
