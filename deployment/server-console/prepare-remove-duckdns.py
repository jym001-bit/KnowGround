#!/usr/bin/env python3
"""Prepare candidates without the former DuckDNS domain."""

from pathlib import Path

old = "jym-debug.duckdns.org"
new = "jymcode.top"

caddy = Path("/etc/caddy/Caddyfile").read_text(encoding="utf-8")
start = caddy.index(old + " {\n")
end = caddy.index("http://23.252.124.29 {\n", start)
if start < 1 or "jymcode.top {" not in caddy or caddy.count(old + " {\n") != 1:
    raise SystemExit("Unexpected live Caddyfile; no candidate written")
Path("/tmp/Caddyfile.no-duckdns").write_text(caddy[:start] + caddy[end:], encoding="utf-8")

auth = Path("/etc/authelia/configuration.yml").read_text(encoding="utf-8")
rule = (
    "    - domain: 'jym-debug.duckdns.org'\n"
    "      policy: 'one_factor'\n"
    "      subject:\n"
    "        - 'user:admin'\n"
)
if auth.count(rule) != 1 or f"domain: '{new}'" not in auth:
    raise SystemExit("Unexpected Authelia access rules")
auth = auth.replace(rule, "", 1)
old_cookie = auth.index("    - name: 'authelia_session'\n      domain: 'jym-debug.duckdns.org'")
new_cookie = auth.index("    - name: 'authelia_session'\n      domain: 'jymcode.top'", old_cookie)
auth = auth[:old_cookie] + auth[new_cookie:]
if old in auth:
    raise SystemExit("Former domain remains in Authelia candidate")
auth_candidate = Path("/tmp/authelia.no-duckdns.yml")
auth_candidate.write_text(auth, encoding="utf-8")
auth_candidate.chmod(0o600)

env_path = Path("/opt/knowground/.env.cloud")
env = env_path.read_text(encoding="utf-8")
rows = env.splitlines()
found = False
for index, row in enumerate(rows):
    if row.startswith("SECURITY_ALLOWED_ORIGINS="):
        key, value = row.split("=", 1)
        origins = [x.strip() for x in value.split(",") if x.strip()]
        if "https://" + old not in origins or "https://" + new not in origins:
            raise SystemExit("Unexpected API allowed origins")
        rows[index] = key + "=" + ",".join(x for x in origins if x != "https://" + old)
        found = True
if not found:
    raise SystemExit("Allowed origins missing")
env_candidate = Path("/tmp/knowground.no-duckdns.env")
env_candidate.write_text("\n".join(rows) + "\n", encoding="utf-8")
env_candidate.chmod(0o600)
print("Prepared candidates without the former domain; live files were not changed.")
