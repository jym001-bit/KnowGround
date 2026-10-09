#!/usr/bin/env bash
set -euo pipefail

# Certbot's six-day IP certificate is renewed independently of Caddy 2.6.
docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /opt/knowground/acme-webroot:/var/www/letsencrypt \
  certbot/certbot:v5.4.0 renew --cert-name 23.252.124.29 --quiet

install -d -o caddy -g caddy -m 700 /etc/caddy/ip-cert
install -o caddy -g caddy -m 600 \
  /etc/letsencrypt/live/23.252.124.29/fullchain.pem /etc/caddy/ip-cert/fullchain.pem
install -o caddy -g caddy -m 600 \
  /etc/letsencrypt/live/23.252.124.29/privkey.pem /etc/caddy/ip-cert/privkey.pem

caddy validate --config /etc/caddy/Caddyfile
systemctl reload caddy
