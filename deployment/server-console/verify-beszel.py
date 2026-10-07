#!/usr/bin/env python3
"""Print only nonsecret Beszel health facts from the local SQLite database."""

import sqlite3

connection = sqlite3.connect("file:/var/lib/beszel-hub/data.db?mode=ro", uri=True)
tables = {
    row[0]
    for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")
}
system = connection.execute(
    "SELECT name, status FROM systems WHERE name = ?", ("jym-vps",)
).fetchone()
print(f"system={system[0] if system else 'missing'} status={system[1] if system else 'missing'}")
for table in sorted(tables):
    if table in {"system_stats", "system_stats_hourly", "system_stats_daily"}:
        count = connection.execute(f'SELECT count(*) FROM "{table}"').fetchone()[0]
        print(f"{table}_rows={count}")
