#!/usr/bin/env python3
"""Register the local Beszel agent through the loopback-only Hub API."""

import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8090/api/collections"
IDENTITY = "admin@jym-debug.duckdns.org"
HEADERS = {"X-Beszel-Identity": IDENTITY}


def api(path, payload=None):
    body = None if payload is None else json.dumps(payload).encode()
    headers = dict(HEADERS)
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(BASE + path, data=body, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = json.load(error)
        raise RuntimeError(f"Beszel API {error.code}: {detail.get('message', 'unknown error')}") from error


users = api("/users/records?fields=id,email")["items"]
matches = [user for user in users if user.get("email") == IDENTITY]
if len(matches) != 1:
    raise RuntimeError(f"Expected one monitor admin, found {len(matches)}")

systems = api("/systems/records?fields=id,name,host,port,status")["items"]
existing = [system for system in systems if system.get("name") == "jym-vps"]
if existing:
    system = existing[0]
    print(f"System exists: {system['name']} {system['host']}:{system['port']} ({system['status']})")
else:
    system = api(
        "/systems/records",
        {"name": "jym-vps", "host": "127.0.0.1", "port": "45876", "users": [matches[0]["id"]]},
    )
    print(f"System created: {system['name']} {system['host']}:{system['port']}")
