#!/usr/bin/env python3
"""Count references to the former public hostname in MySQL text columns."""

import subprocess

CONTAINER = "knowground-mysql-1"
OLD = "jym-debug.duckdns.org"


def query(sql: str) -> str:
    result = subprocess.run(
        [
            "docker", "exec", CONTAINER, "sh", "-c",
            'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" -D "$MYSQL_DATABASE" -N -B -e "$1"',
            "mysql-query", sql,
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


columns = query(
    "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
    "WHERE TABLE_SCHEMA = DATABASE() "
    "AND DATA_TYPE IN ('char','varchar','tinytext','text','mediumtext','longtext','json')"
)
hits = []
for row in columns.splitlines():
    table, column = row.split("\t")
    if not table.replace("_", "").isalnum() or not column.replace("_", "").isalnum():
        raise SystemExit("Unexpected database identifier")
    count = int(query(
        f"SELECT COUNT(*) FROM `{table}` WHERE CAST(`{column}` AS CHAR) "
        f"LIKE '%{OLD}%'"
    ))
    if count:
        hits.append((table, column, count))

print("Former-domain references:", sum(count for _, _, count in hits))
for table, column, count in hits:
    print(f"{table}.{column}: {count}")
