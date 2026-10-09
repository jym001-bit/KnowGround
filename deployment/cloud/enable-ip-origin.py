#!/usr/bin/env python3
"""Allow the IP origin and make new S3 download URLs work on both site origins."""

from pathlib import Path
import shutil
import sys


path = Path(sys.argv[1] if len(sys.argv) > 1 else "/opt/knowground/.env.cloud")
backup = path.with_name(path.name + ".pre-ip-20261007")
if backup.exists():
    raise SystemExit(f"Refusing to overwrite existing backup: {backup}")

lines = path.read_text(encoding="utf-8").splitlines()
found = set()
updated = []
for line in lines:
    if line.startswith("SECURITY_ALLOWED_ORIGINS="):
        key, value = line.split("=", 1)
        origins = [part.strip() for part in value.split(",") if part.strip()]
        if "https://23.252.124.29" not in origins:
            origins.append("https://23.252.124.29")
        updated.append(key + "=" + ",".join(origins))
        found.add(key)
    elif line.startswith("MINIO_PUBLIC_URL="):
        updated.append("MINIO_PUBLIC_URL=same-origin")
        found.add("MINIO_PUBLIC_URL")
    else:
        updated.append(line)

required = {"SECURITY_ALLOWED_ORIGINS", "MINIO_PUBLIC_URL"}
if found != required:
    raise SystemExit("Missing expected cloud configuration keys: " + ", ".join(sorted(required - found)))

shutil.copy2(path, backup)
path.write_text("\n".join(updated) + "\n", encoding="utf-8")
path.chmod(0o600)
print("Updated cloud origins and same-origin S3 public URL; backup created. No secrets printed.")
