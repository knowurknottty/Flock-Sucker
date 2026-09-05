#!/usr/bin/env python3
"""Host-side structural proof for the Room v11 -> v12 observation migration."""
from __future__ import annotations

import json
import re
import sqlite3
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "app/schemas/com.flockyou.data.repository.FlockYouDatabase"
DATABASE_KT = ROOT / "app/src/main/java/com/flockyou/data/repository/Database.kt"


def schema(version: int) -> dict:
    return json.loads((SCHEMA_DIR / f"{version}.json").read_text())["database"]


def create_schema(conn: sqlite3.Connection, db_schema: dict) -> None:
    for entity in db_schema["entities"]:
        table = entity["tableName"]
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            conn.execute(index["createSql"].replace("${TABLE_NAME}", table))
    for query in db_schema.get("setupQueries", []):
        conn.execute(query)


def migration_sql() -> list[str]:
    source = DATABASE_KT.read_text()
    match = re.search(
        r"internal val MIGRATION_11_12 = object : Migration\(11, 12\) \{(.*?)\n        \}\n\n        fun getDatabase",
        source,
        re.S,
    )
    if not match:
        raise AssertionError("MIGRATION_11_12 block not found")
    block = match.group(1)
    statements = re.findall(r'db\.execSQL\("""(.*?)"""\)', block, re.S)
    statements += re.findall(r'db\.execSQL\("([^"\n]+)"\)', block)
    if len(statements) != 6:
        raise AssertionError(f"Expected 6 v11->v12 SQL statements, found {len(statements)}")
    return [statement.strip() for statement in statements]


def expected_observation_schema(db_schema: dict) -> dict:
    return next(e for e in db_schema["entities"] if e["tableName"] == "observations")


def normalize_not_null(field: dict) -> int:
    return 1 if field.get("notNull", False) else 0


def verify() -> None:
    v11, v12 = schema(11), schema(12)
    expected = expected_observation_schema(v12)
    with tempfile.TemporaryDirectory(prefix="flock-room-") as tmp:
        path = Path(tmp) / "migration.sqlite"
        conn = sqlite3.connect(path)
        create_schema(conn, v11)
        v11_tables = {
            row[0] for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            )
        }
        conn.execute(
            "INSERT INTO sightings (id,detectionId,timestamp,sequence,protocol,sourceScanner,"
            "detectorHealthGeneration,disposition) VALUES (?,?,?,?,?,?,?,?)",
            ("s1", "d1", 1000, 1, "BLUETOOTH_LE", "BLE", 0, "new_device"),
        )
        for statement in migration_sql():
            conn.execute(statement)
        conn.commit()

        actual_fields = {
            row[1]: (row[2].upper(), row[3], row[5])
            for row in conn.execute("PRAGMA table_info(observations)")
        }
        expected_fields = {
            field["columnName"]: (
                field["affinity"].upper(),
                normalize_not_null(field),
                1 if field["columnName"] in expected["primaryKey"]["columnNames"] else 0,
            )
            for field in expected["fields"]
        }
        if actual_fields != expected_fields:
            raise AssertionError(f"Observation columns differ:\nactual={actual_fields}\nexpected={expected_fields}")

        actual_indices = {
            row[1]: [info[2] for info in conn.execute(f"PRAGMA index_info('{row[1]}')")]
            for row in conn.execute("PRAGMA index_list('observations')")
            if not row[1].startswith("sqlite_autoindex")
        }
        expected_indices = {idx["name"]: idx["columnNames"] for idx in expected["indices"]}
        if actual_indices != expected_indices:
            raise AssertionError(f"Observation indices differ: {actual_indices} != {expected_indices}")

        post_tables = {
            row[0] for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            )
        }
        if not v11_tables.issubset(post_tables) or "observations" not in post_tables:
            raise AssertionError("Migration dropped a v11 table or failed to create observations")

        preserved = conn.execute("SELECT COUNT(*) FROM sightings WHERE id='s1'").fetchone()[0]
        empty_observations = conn.execute("SELECT COUNT(*) FROM observations").fetchone()[0]
        if preserved != 1 or empty_observations != 0:
            raise AssertionError("Migration did not preserve legacy sighting or observation ledger was not empty")
        conn.close()

    print(
        "Observation migration structural proof PASS: "
        f"v11 tables preserved; {len(expected_fields)} fields; {len(expected_indices)} indices"
    )


if __name__ == "__main__":
    verify()
