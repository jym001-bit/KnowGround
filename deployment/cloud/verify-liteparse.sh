#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1/manifest.tsv" ]]; then
  echo "Usage: $0 <storage-backup-dir>" >&2
  exit 2
fi

backup=$(realpath "$1")
object=$(python3 - "$backup/manifest.tsv" <<'PY'
import sys
with open(sys.argv[1], encoding="utf-8") as manifest:
    entries = [line.rstrip("\n").split("\t") for line in manifest]
print(min(entries, key=lambda entry: int(entry[1]))[0])
PY
)

docker run --rm --memory 896m --entrypoint sh \
  --mount "type=bind,source=$backup/objects,target=/source,readonly" \
  -e KG_OBJECT="$object" knowground-api:latest -c '
    set -e
    cp "/source/$KG_OBJECT" /tmp/knowground-sample.pdf
    lit parse /tmp/knowground-sample.pdf --format json --output /tmp/knowground-result.json --max-pages 1000 --dpi 150 --no-ocr --quiet
    test -s /tmp/knowground-result.json
    node -e "JSON.parse(require(\"node:fs\").readFileSync(\"/tmp/knowground-result.json\", \"utf8\"))"
    echo "LiteParse PDF parse: ok"
  '
