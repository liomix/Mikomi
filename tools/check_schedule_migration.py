"""校验时间表迁移后的表结构与 Room 期望 schema 是否一致。

改动 core:database 的迁移或实体后执行：python tools/check_schedule_migration.py
（脚本内的 MIGRATION_SQL 需与 Migrations.kt 保持同步）。
"""

import json
import re
import sqlite3
import sys

SCHEMA_DIR = "D:/Code/Mikomi/core/database/schemas/com.liomix.mikomi.core.database.MikomiDatabase"
NEW_TABLES = ["schedule_source", "schedule_source_entry", "anime_identity", "schedule_conflict"]

MIGRATION_SQL = [
    "ALTER TABLE schedule ADD COLUMN canonicalKey TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE schedule ADD COLUMN sourceId TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE schedule ADD COLUMN airTimeDate TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE schedule ADD COLUMN airTime TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE schedule ADD COLUMN precision TEXT NOT NULL DEFAULT 'UNKNOWN'",
    "ALTER TABLE schedule ADD COLUMN inferred INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE schedule ADD COLUMN detailLink TEXT NOT NULL DEFAULT ''",
    """CREATE TABLE IF NOT EXISTS `schedule_source` (
        `sourceId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `priority` INTEGER NOT NULL,
        `version` INTEGER NOT NULL DEFAULT 0, `itemCount` INTEGER NOT NULL DEFAULT 0,
        `fetchedAtMs` INTEGER NOT NULL DEFAULT 0, `anchorStartIndex` INTEGER NOT NULL DEFAULT 0,
        `lastStatus` TEXT NOT NULL DEFAULT 'UNKNOWN', PRIMARY KEY(`sourceId`))""",
    """CREATE TABLE IF NOT EXISTS `schedule_source_entry` (
        `sourceId` TEXT NOT NULL, `entryId` TEXT NOT NULL,
        `sourceVersion` INTEGER NOT NULL DEFAULT 0, `canonicalKey` TEXT NOT NULL DEFAULT '',
        `title` TEXT NOT NULL, `coverUrl` TEXT, `synopsis` TEXT,
        `airTimeDate` TEXT NOT NULL DEFAULT '', `airTime` TEXT NOT NULL DEFAULT '',
        `precision` TEXT NOT NULL DEFAULT 'UNKNOWN', `episode` INTEGER NOT NULL DEFAULT 0,
        `episodeText` TEXT NOT NULL DEFAULT '', `updateText` TEXT NOT NULL DEFAULT '',
        `detailLink` TEXT NOT NULL DEFAULT '', `channelId` TEXT NOT NULL DEFAULT '',
        `fetchedAtMs` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`sourceId`, `entryId`))""",
    """CREATE TABLE IF NOT EXISTS `anime_identity` (
        `canonicalKey` TEXT NOT NULL, `primaryTitle` TEXT NOT NULL,
        `aliases` TEXT NOT NULL DEFAULT '', `externalIds` TEXT NOT NULL DEFAULT '',
        `matchState` TEXT NOT NULL DEFAULT 'AUTO', `updatedAt` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`canonicalKey`))""",
    """CREATE TABLE IF NOT EXISTS `schedule_conflict` (
        `id` TEXT NOT NULL, `canonicalKey` TEXT NOT NULL, `title` TEXT NOT NULL,
        `type` TEXT NOT NULL, `baseSourceId` TEXT NOT NULL, `baseDate` TEXT NOT NULL,
        `baseTime` TEXT NOT NULL, `candidateSourceId` TEXT NOT NULL,
        `candidateDate` TEXT NOT NULL, `candidateTime` TEXT NOT NULL,
        `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
]

COLUMN_RE = re.compile(r"`(\w+)`\s+([A-Z]+)([^,)]*)")


def create_sql(schema, table):
    for entity in schema["database"]["entities"]:
        if entity["tableName"] == table:
            return entity["createSql"].replace("${TABLE_NAME}", table)
    raise KeyError(table)


def expected_columns(schema, table):
    sql = create_sql(schema, table)
    body = sql[sql.index("(") + 1: sql.rindex(")")]
    columns = {}
    for name, ctype, rest in COLUMN_RE.findall(body):
        default = None
        match = re.search(r"DEFAULT\s+('[^']*'|\S+)", rest)
        if match:
            default = match.group(1)
        columns[name] = (ctype, "NOT NULL" in rest, default)
    return columns


def actual_columns(connection, table):
    columns = {}
    for row in connection.execute(f"PRAGMA table_info('{table}')"):
        # cid, name, type, notnull, dflt_value, pk
        default = row[4]
        if default is not None:
            default = default if default.startswith("'") else default
        columns[row[1]] = (row[2], bool(row[3]), default)
    return columns


def main():
    with open(f"{SCHEMA_DIR}/9.json", encoding="utf-8") as handle:
        v9 = json.load(handle)
    with open(f"{SCHEMA_DIR}/10.json", encoding="utf-8") as handle:
        v10 = json.load(handle)

    connection = sqlite3.connect(":memory:")
    for entity in v9["database"]["entities"]:
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

    for statement in MIGRATION_SQL:
        connection.execute(statement)

    failures = 0
    for table in ["schedule"] + NEW_TABLES:
        expected = expected_columns(v10, table)
        actual = actual_columns(connection, table)
        if expected == actual:
            print(f"[OK] {table} ({len(expected)} columns)")
            continue
        failures += 1
        print(f"[FAIL] {table}")
        for name in sorted(set(expected) | set(actual)):
            if expected.get(name) != actual.get(name):
                print(f"   {name}: expected={expected.get(name)} actual={actual.get(name)}")

    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
