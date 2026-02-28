from __future__ import annotations

import sqlite3
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Iterator


@dataclass(frozen=True)
class StoredHoliday:
    date: date
    label: str
    kind: str


def _ensure_parent(db_path: Path) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)


@contextmanager
def _connect(db_path: Path) -> Iterator[sqlite3.Connection]:
    _ensure_parent(db_path)
    connection = sqlite3.connect(str(db_path))
    try:
        yield connection
        connection.commit()
    finally:
        connection.close()


def init_holiday_db(db_path: Path) -> None:
    with _connect(db_path) as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS holidays (
                date TEXT NOT NULL,
                label TEXT NOT NULL,
                kind TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (date, kind)
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS sync_state (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_holidays_date ON holidays(date)"
        )


def get_holidays_in_range(db_path: Path, start: date, end: date) -> list[StoredHoliday]:
    with _connect(db_path) as conn:
        rows = conn.execute(
            """
            SELECT date, label, kind
            FROM holidays
            WHERE date BETWEEN ? AND ?
            ORDER BY date ASC, kind ASC, label ASC
            """,
            (start.isoformat(), end.isoformat()),
        ).fetchall()
    return [
        StoredHoliday(
            date=date.fromisoformat(row[0]),
            label=row[1],
            kind=row[2],
        )
        for row in rows
    ]


def replace_holidays_in_range(
    db_path: Path,
    *,
    start: date,
    end: date,
    items: list[StoredHoliday],
) -> None:
    now_millis = int(datetime.now(timezone.utc).timestamp() * 1000)
    with _connect(db_path) as conn:
        conn.execute(
            "DELETE FROM holidays WHERE date BETWEEN ? AND ?",
            (start.isoformat(), end.isoformat()),
        )
        conn.executemany(
            """
            INSERT INTO holidays(date, label, kind, updated_at)
            VALUES (?, ?, ?, ?)
            """,
            [(item.date.isoformat(), item.label, item.kind, now_millis) for item in items],
        )


def get_state(db_path: Path, key: str) -> str | None:
    with _connect(db_path) as conn:
        row = conn.execute(
            "SELECT value FROM sync_state WHERE key = ?",
            (key,),
        ).fetchone()
    if row is None:
        return None
    return str(row[0])


def set_state(db_path: Path, key: str, value: str) -> None:
    with _connect(db_path) as conn:
        conn.execute(
            """
            INSERT INTO sync_state(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """,
            (key, value),
        )


def has_any_holiday_data(db_path: Path) -> bool:
    with _connect(db_path) as conn:
        row = conn.execute("SELECT COUNT(1) FROM holidays").fetchone()
    return bool(row and int(row[0]) > 0)
