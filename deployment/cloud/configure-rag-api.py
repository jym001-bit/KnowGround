#!/usr/bin/env python3
"""Add the new public origin and /rag file URLs to the existing cloud env."""

from pathlib import Path

env = Path('/opt/knowground/.env.cloud')
backup = env.with_name('.env.cloud.pre-rag-20261007')
original = env.read_text(encoding='utf-8')
rows = original.splitlines()
found = set()
for index, row in enumerate(rows):
    if row.startswith('MINIO_PUBLIC_URL='):
        rows[index] = 'MINIO_PUBLIC_URL=/rag'
        found.add('MINIO_PUBLIC_URL')
    elif row.startswith('SECURITY_ALLOWED_ORIGINS='):
        key, value = row.split('=', 1)
        origins = [origin.strip() for origin in value.split(',') if origin.strip()]
        if 'https://jymcode.top' not in origins:
            origins.append('https://jymcode.top')
        rows[index] = key + '=' + ','.join(origins)
        found.add('SECURITY_ALLOWED_ORIGINS')
if found != {'MINIO_PUBLIC_URL', 'SECURITY_ALLOWED_ORIGINS'}:
    raise SystemExit('Required cloud variables are missing; no file changed.')
if not backup.exists():
    backup.write_text(original, encoding='utf-8')
    backup.chmod(0o600)
env.write_text('\n'.join(rows) + '\n', encoding='utf-8')
env.chmod(0o600)
print('Cloud origin and file path updated; previous env backed up.')
