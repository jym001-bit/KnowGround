#!/usr/bin/env bash
set -euo pipefail

domain=jym-debug.duckdns.org
server_ip=23.252.124.29
image=authelia/authelia:4.39.25
base=/root/server-console-deploy

if [[ $(id -u) -ne 0 ]]; then
  echo 'Run as root' >&2
  exit 1
fi

resolved=$(getent ahostsv4 "$domain" | awk 'NR == 1 { print $1 }')
if [[ "$resolved" != "$server_ip" ]]; then
  echo "Domain resolves to ${resolved:-nothing}, expected $server_ip" >&2
  exit 1
fi

test -x /usr/local/bin/node
test -d /opt/server-console/src
test -f "$base/Caddyfile"
test -f "$base/config.json"
test -f "$base/authelia.configuration.yml.template"
systemctl is-active --quiet docker
systemctl is-active --quiet caddy

install -d -m 0700 -o root -g root /etc/authelia /etc/server-console
install -d -m 0700 -o root -g root /var/lib/server-console

credential_file=/root/server-console-initial-credentials.txt
if [[ ! -e "$credential_file" ]]; then
  umask 077
  docker run --rm "$image" authelia crypto hash generate argon2 \
    --random --random.length 32 --random.charset alphanumeric > "$credential_file"
fi
chmod 0600 "$credential_file"
password_hash=$(sed -n 's/^Digest: //p' "$credential_file")
if [[ -z "$password_hash" ]] || ! grep -q '^Random Password: ' "$credential_file"; then
  echo 'Could not parse initial credential output' >&2
  exit 1
fi

cat > /etc/authelia/users.yml <<EOF
users:
  admin:
    displayname: 'Server Admin'
    password: '$password_hash'
    email: 'admin@$domain'
    groups:
      - 'admins'
EOF
chmod 0600 /etc/authelia/users.yml

if [[ ! -e /etc/authelia/session_secret ]]; then
  openssl rand -hex 32 > /etc/authelia/session_secret
fi
if [[ ! -e /etc/authelia/storage_secret ]]; then
  openssl rand -hex 32 > /etc/authelia/storage_secret
fi
chmod 0600 /etc/authelia/session_secret /etc/authelia/storage_secret
session_secret=$(cat /etc/authelia/session_secret)
storage_secret=$(cat /etc/authelia/storage_secret)
sed -e "s/__SESSION_SECRET__/$session_secret/" \
    -e "s/__STORAGE_SECRET__/$storage_secret/" \
    "$base/authelia.configuration.yml.template" > /etc/authelia/configuration.yml
chmod 0600 /etc/authelia/configuration.yml
unset session_secret storage_secret password_hash

docker run --rm -v /etc/authelia:/config "$image" \
  authelia config validate --config /config/configuration.yml

if ! docker container inspect server-console-auth >/dev/null 2>&1; then
  docker run -d --name server-console-auth --restart unless-stopped \
    -p 127.0.0.1:9091:9091 \
    -v /etc/authelia:/config \
    "$image" >/dev/null
fi

install -m 0600 -o root -g root "$base/config.json" /etc/server-console/config.json
sed -e 's/^Group=.*/Group=caddy/' \
    -e 's|^ExecStart=.*|ExecStart=/usr/local/bin/node /opt/server-console/src/server.mjs|' \
    /opt/server-console/deploy/server-console.service > /etc/systemd/system/server-console.service
chmod 0644 /etc/systemd/system/server-console.service
systemctl daemon-reload
systemctl enable --now server-console

for _ in $(seq 1 20); do
  [[ -S /run/server-console/http.sock ]] && break
  sleep 0.5
done
test "$(stat -c '%U %G %a' /run/server-console/http.sock)" = 'root caddy 660'
test "$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --unix-socket /run/server-console/http.sock \
  -H 'Remote-User: admin' -H 'Cookie: authelia_session=smoke' \
  http://localhost/server/api/session)" = '200'
test "$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --unix-socket /run/server-console/http.sock \
  http://localhost/server/api/session)" = '403'

caddy validate --config "$base/Caddyfile" --adapter caddyfile
install -m 0644 -o root -g root "$base/Caddyfile" /etc/caddy/Caddyfile
systemctl reload caddy

echo 'Local services and Caddy configuration installed. Run the public access checks.'
