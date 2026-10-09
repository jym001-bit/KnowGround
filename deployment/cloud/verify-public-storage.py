#!/usr/bin/env python3
"""Check that a real migrated object is readable through the public signed URL route."""

import base64
import hashlib
import os
import pathlib
import sys
import urllib.request

import boto3
from botocore.config import Config


def decode(value: str) -> str:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)).decode("utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify-public-storage.py <storage-backup-dir>")
    root = pathlib.Path(__file__).resolve().parents[2]
    values = {}
    for line in (root / ".env.cloud").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value

    manifest = (pathlib.Path(sys.argv[1]) / "manifest.tsv").read_text(encoding="utf-8").splitlines()
    encoded, size_text, expected_hash, _ = min(
        (line.split("\t") for line in manifest), key=lambda fields: int(fields[1])
    )
    internal = "http://rustfs:9000"
    public = values["MINIO_PUBLIC_URL"].rstrip("/")
    if public == "same-origin":
        public = ""
    client = boto3.client(
        "s3",
        endpoint_url=internal,
        aws_access_key_id=values["MINIO_ACCESS_KEY"],
        aws_secret_access_key=values["MINIO_SECRET_KEY"],
        region_name="us-east-1",
        config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
    )
    signed = client.generate_presigned_url(
        "get_object",
        Params={"Bucket": values["MINIO_BUCKET_NAME"], "Key": decode(encoded)},
        ExpiresIn=60,
    )
    url = signed.replace(internal, public, 1)
    if url.startswith("/"):
        url = os.environ.get("KNOWGROUND_PUBLIC_ORIGIN", "https://jymcode.top").rstrip("/") + url
    with urllib.request.urlopen(url, timeout=20) as response:
        payload = response.read()
    if len(payload) != int(size_text) or hashlib.sha256(payload).hexdigest() != expected_hash:
        raise RuntimeError("Public download checksum mismatch")
    print(f"Public presigned download: 200, {len(payload)} bytes, SHA-256 matched")


if __name__ == "__main__":
    main()
