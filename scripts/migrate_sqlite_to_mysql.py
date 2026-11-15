#!/usr/bin/env python3
"""One-shot data migration from the legacy SQLite file to a MySQL database.

Only the standard library is used. The script emits SQL on stdout (or --output);
it never connects to MySQL itself, so it can be piped into the mysql CLI that
lives inside the MySQL container:

    python3 scripts/migrate_sqlite_to_mysql.py \
        --sqlite /tmp/minidesk-live.db \
        --schema backend/src/main/resources/db/schema-mysql.sql \
        --output /tmp/minidesk-import.sql
    docker exec -i mysql mysql -u minidesk -p minidesk < /tmp/minidesk-import.sql

Notes:
  * Copy minidesk.db together with -wal/-shm before running, otherwise recent
    transactions in the WAL are invisible.
  * Values are emitted as utf8mb4 hex literals, so quotes, backslashes, newlines
    and emoji in notes need no SQL escaping.
  * Only columns present in BOTH the SQLite source and the MySQL schema are
    imported; extra legacy columns are dropped and new nullable columns stay NULL.
  * Run this BEFORE the first MySQL boot of the app, so the seed inserts do not
    compete with the imported rows.
"""

from __future__ import annotations

import argparse
import re
import sqlite3
import sys
from pathlib import Path

NUMERIC_OR_DATE = ("TINYINT", "SMALLINT", "INT", "BIGINT", "DECIMAL", "FLOAT",
                   "DOUBLE", "DATE", "DATETIME", "TIMESTAMP", "TIME")


def parse_mysql_schema(path: Path) -> dict[str, dict[str, str]]:
    """table -> {column: declared type}, keeping declaration order."""
    text = path.read_text(encoding="utf-8")
    tables: dict[str, dict[str, str]] = {}
    current: str | None = None
    for raw in text.splitlines():
        line = raw.strip()
        header = re.match(r"CREATE TABLE IF NOT EXISTS `(\w+)` \(", line)
        if header:
            current = header.group(1)
            tables[current] = {}
            continue
        if current is None:
            continue
        if line.startswith(") ENGINE"):
            current = None
            continue
        column = re.match(r"`(\w+)`\s+([A-Za-z]+)", line)
        if column and column.group(1).lower() not in ("primary", "unique", "key", "constraint"):
            tables[current][column.group(1)] = column.group(2).upper()
    return tables


def hex_literal(value: object) -> str:
    """A MySQL string literal that cannot be broken by any byte in the value."""
    if isinstance(value, bytes):
        data = value
    else:
        data = str(value).encode("utf-8")
    return "CONVERT(X'%s' USING utf8mb4)" % data.hex()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sqlite", required=True, help="path to the copied minidesk.db")
    parser.add_argument("--schema", required=True, help="path to schema-mysql.sql")
    parser.add_argument("--output", help="write SQL here instead of stdout")
    parser.add_argument("--batch", type=int, default=200, help="rows per INSERT statement")
    args = parser.parse_args()

    sqlite_path = Path(args.sqlite)
    if not sqlite_path.is_file():
        print("SQLite file not found: %s" % sqlite_path, file=sys.stderr)
        return 1
    target = parse_mysql_schema(Path(args.schema))
    if not target:
        print("No tables parsed from the MySQL schema", file=sys.stderr)
        return 1

    connection = sqlite3.connect(str(sqlite_path))
    connection.row_factory = None
    source_tables = {
        row[0] for row in connection.execute(
            "select name from sqlite_master where type='table' and name not like 'sqlite_%'")
    }

    statements: list[str] = [
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS=0;",
        "START TRANSACTION;",
    ]
    summary: list[tuple[str, int]] = []
    for table, columns in target.items():
        if table not in source_tables:
            continue
        source_columns = [row[1] for row in connection.execute("pragma table_info(%s)" % table)]
        shared = [name for name in columns if name in source_columns]
        if not shared:
            continue
        quoted = ", ".join("`%s`" % name for name in shared)
        rows = list(connection.execute(
            "select %s from `%s`" % (", ".join("`%s`" % c for c in shared), table)))
        statements.append("DELETE FROM `%s`;" % table)
        for start in range(0, len(rows), args.batch):
            chunk = rows[start:start + args.batch]
            values = []
            for row in chunk:
                cells = []
                for name, value in zip(shared, row):
                    if value is None:
                        cells.append("NULL")
                    elif value == "" and columns[name].startswith(NUMERIC_OR_DATE):
                        cells.append("NULL")
                    else:
                        cells.append(hex_literal(value))
                values.append("(" + ", ".join(cells) + ")")
            statements.append("INSERT INTO `%s` (%s) VALUES\n  %s;" %
                              (table, quoted, ",\n  ".join(values)))
        summary.append((table, len(rows)))
    connection.close()

    statements.append("COMMIT;")
    statements.append("SET FOREIGN_KEY_CHECKS=1;")
    sql = "\n".join(statements) + "\n"

    if args.output:
        Path(args.output).write_text(sql, encoding="utf-8")
    else:
        sys.stdout.write(sql)

    total = sum(count for _, count in summary)
    print("migration SQL: %d tables, %d rows, %d bytes" %
          (len(summary), total, len(sql.encode("utf-8"))), file=sys.stderr)
    for table, count in summary:
        if count:
            print("  %-24s %d" % (table, count), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
