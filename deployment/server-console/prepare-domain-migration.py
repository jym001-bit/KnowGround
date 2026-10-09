#!/usr/bin/env python3
"""Prepare new-domain Caddy, Authelia, and panel candidates without changing live files."""

from pathlib import Path

old = "jym-debug.duckdns.org"
new = "jymcode.top"

caddy = Path("/etc/caddy/Caddyfile").read_text(encoding="utf-8")
panel_start = caddy.index("    @authelia path /authelia /authelia/*")
panel_end = caddy.index("    handle /api/* {", panel_start)
panel_routes = caddy[panel_start:panel_end]
if panel_routes.count(old) != 4:
    raise SystemExit("Unexpected number of old-domain references in panel routes")
new_site_marker = "jymcode.top {\n    encode zstd gzip\n"
if caddy.count(new_site_marker) != 1 or "authelia_url=https://jymcode.top" in caddy:
    raise SystemExit("Expected one unmodified jymcode.top site")
new_caddy = caddy.replace(new_site_marker, new_site_marker + "\n" + panel_routes.replace(old, new), 1)
Path("/tmp/Caddyfile.panel-candidate").write_text(new_caddy, encoding="utf-8")

auth = Path("/etc/authelia/configuration.yml").read_text(encoding="utf-8")
rule = (
    "    - domain: 'jym-debug.duckdns.org'\n"
    "      policy: 'one_factor'\n"
    "      subject:\n"
    "        - 'user:admin'\n"
)
if auth.count(rule) != 1:
    raise SystemExit("Old-domain access rule not found exactly once")
auth = auth.replace(rule, rule + rule.replace(old, new), 1)
cookie_start = auth.index("    - name: 'authelia_session'\n")
cookie_end = auth.index("\nregulation:\n", cookie_start)
cookie = auth[cookie_start:cookie_end].rstrip("\n") + "\n"
if cookie.count(old) != 3:
    raise SystemExit("Unexpected old-domain cookie configuration")
auth = auth[:cookie_end] + cookie.replace(old, new) + auth[cookie_end:]
auth_candidate = Path("/tmp/authelia.panel-candidate.yml")
auth_candidate.write_text(auth, encoding="utf-8")
auth_candidate.chmod(0o600)

panel = Path("/etc/server-console/config.json").read_text(encoding="utf-8")
if panel.count(f'"origin": "https://{old}"') != 1:
    raise SystemExit("Unexpected server-console origin")
Path("/tmp/server-console.panel-candidate.json").write_text(
    panel.replace(f'"origin": "https://{old}"', f'"origin": "https://{new}"'),
    encoding="utf-8",
)
print("Prepared all candidates; live files were not changed.")
