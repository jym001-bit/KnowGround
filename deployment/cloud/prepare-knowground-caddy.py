#!/usr/bin/env python3
"""Prepare, but never activate, the new KnowGround Caddy site."""

from pathlib import Path
import sys


current = Path(sys.argv[1] if len(sys.argv) > 1 else "/etc/caddy/Caddyfile")
candidate = Path(sys.argv[2] if len(sys.argv) > 2 else "/tmp/Caddyfile.knowground-candidate")
config = current.read_text(encoding="utf-8")
snippet = Path(__file__).with_name("Caddyfile.knowground-domain").read_text(encoding="utf-8")

apex = "jymcode.top {"
new_site = "knowground.jymcode.top {"
old_start = "    @rag_without_slash path /rag\n"
old_end = '    handle {\n        respond "Not Found" 404\n    }\n'

if new_site in config:
    raise SystemExit("New site already exists; inspect the live config before repeating.")
if config.count(apex) != 1:
    raise SystemExit("Expected exactly one jymcode.top site; inspect the live config.")

apex_start = config.index(apex)
apex_end = config.find("\n}\n", apex_start)
if apex_end == -1:
    raise SystemExit("Could not find the end of the apex site; inspect the live config.")
rag_start = config.find(old_start, apex_start)
if rag_start == -1 or rag_start > apex_end:
    raise SystemExit("Expected the old /rag route in the apex site; inspect the live config.")
rag_end = config.find(old_end, rag_start)
if rag_end == -1 or rag_end > apex_end:
    raise SystemExit("Expected the apex catch-all after /rag; inspect the live config.")
apex_before = config[apex_start:rag_start]
if "handle /server/*" not in apex_before or "@authelia" not in apex_before:
    raise SystemExit("Server console routes not found before /rag; refusing to alter candidate.")

updated = config[:rag_start].rstrip() + "\n\n" + config[rag_end:]
candidate.write_text(updated.rstrip() + "\n\n" + snippet, encoding="utf-8")
print(f"Prepared {candidate}; live Caddyfile was not changed.")
