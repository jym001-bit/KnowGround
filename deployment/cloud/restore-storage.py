#!/usr/bin/env python3
"""Restore a StorageTransfer backup into the cloud S3 service, without deleting objects."""

import base64
import hashlib
import os
import pathlib
import sys

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError


def decode(value: str) -> str:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)).decode("utf-8")


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: restore-storage.py <storage-backup-dir>")
    root = pathlib.Path(__file__).resolve().parents[2]
    values = {}
    for line in (root / ".env.cloud").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value

    backup = pathlib.Path(sys.argv[1]).resolve()
    manifest = (backup / "manifest.tsv").read_text(encoding="utf-8").splitlines()
    client = boto3.client(
        "s3",
        endpoint_url="http://127.0.0.1:19000",
        aws_access_key_id=values["MINIO_ACCESS_KEY"],
        aws_secret_access_key=values["MINIO_SECRET_KEY"],
        region_name="us-east-1",
        config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
    )
    bucket = values["MINIO_BUCKET_NAME"]
    try:
        client.head_bucket(Bucket=bucket)
    except ClientError as error:
        if error.response["ResponseMetadata"]["HTTPStatusCode"] != 404:
            raise
        client.create_bucket(Bucket=bucket)

    total = 0
    for line in manifest:
        encoded, size_text, expected_hash, encoded_type = line.split("\t")
        file = backup / "objects" / encoded
        size = int(size_text)
        if file.stat().st_size != size or sha256(file) != expected_hash:
            raise RuntimeError(f"Backup integrity check failed: {encoded}")
        name = decode(encoded)
        client.upload_file(str(file), bucket, name, ExtraArgs={"ContentType": decode(encoded_type)})
        copied = client.head_object(Bucket=bucket, Key=name)
        if copied["ContentLength"] != size:
            raise RuntimeError(f"Cloud size mismatch: {encoded}")
        total += size
    print(f"Restored and size-verified {len(manifest)} objects, {total} bytes")


if __name__ == "__main__":
    main()
