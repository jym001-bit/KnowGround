#!/usr/bin/env python3
"""Prepare a Caddyfile candidate for jymcode.top/rag/ without altering the live file."""

from pathlib import Path
import sys


current = Path(sys.argv[1] if len(sys.argv) > 1 else "/etc/caddy/Caddyfile")
candidate = Path(sys.argv[2] if len(sys.argv) > 2 else "/tmp/Caddyfile.rag-candidate")
snippet = Path(__file__).with_name("Caddyfile.rag-domain").read_text(encoding="utf-8")
config = current.read_text(encoding="utf-8")

if "jymcode.top {" in config:
    raise SystemExit("The new domain is already present; inspect the live config before repeating.")

marker = "    handle /uploads/* {"
if config.count(marker) != 2:
    raise SystemExit(f"Expected two legacy upload routes, found {config.count(marker)}")

alias = """    handle /rag/uploads/* {
        route {
            @non_read not method GET HEAD
            respond @non_read "Method Not Allowed" 405
            uri strip_prefix /rag
            reverse_proxy 127.0.0.1:19000 {
                header_up Host rustfs:9000
            }
        }
    }

"""
candidate.write_text(config.replace(marker, alias + marker) + "\n" + snippet, encoding="utf-8")
print(f"Prepared {candidate}; live Caddyfile was not changed.")
