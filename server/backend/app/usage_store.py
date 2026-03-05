from __future__ import annotations

import hashlib
import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator


def _ensure_parent(db_path: Path) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)


@contextmanager
def _connect(db_path: Path) -> Iterator[sqlite3.Connection]:
    _ensure_parent(db_path)
    connection = sqlite3.connect(str(db_path))
    connection.row_factory = sqlite3.Row
    try:
        yield connection
        connection.commit()
    finally:
        connection.close()


def init_usage_db(db_path: Path) -> None:
    with _connect(db_path) as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'user',
                created_at INTEGER NOT NULL
            )
            """
        )
        _ensure_users_role_column(conn)
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS usage_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT,
                endpoint TEXT NOT NULL,
                model TEXT NOT NULL,
                transcript TEXT NOT NULL,
                success INTEGER NOT NULL,
                input_tokens INTEGER NOT NULL DEFAULT 0,
                output_tokens INTEGER NOT NULL DEFAULT 0,
                total_tokens INTEGER NOT NULL DEFAULT 0,
                latency_ms INTEGER NOT NULL DEFAULT 0,
                llm_input_text TEXT,
                llm_output_text TEXT,
                error_code TEXT,
                error_message TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
            )
            """
        )
        _ensure_usage_events_input_column(conn)
        _ensure_usage_events_output_column(conn)
        conn.execute("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_usage_user_created ON usage_events(user_id, created_at)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_usage_created ON usage_events(created_at)")


def create_user(db_path: Path, username: str, password: str, role: str = "user") -> dict[str, Any]:
    now = int(time.time() * 1000)
    user_id = secrets.token_hex(16)
    password_hash = _hash_password(password)
    normalized_role = _normalize_role(role)
    with _connect(db_path) as conn:
        conn.execute(
            "INSERT INTO users(id, username, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)",
            (user_id, username, password_hash, normalized_role, now),
        )
    return {
        "id": user_id,
        "username": username,
        "role": normalized_role,
        "createdAt": now,
    }


def authenticate_user(db_path: Path, username: str, password: str) -> dict[str, Any] | None:
    with _connect(db_path) as conn:
        row = conn.execute(
            "SELECT id, username, password_hash, role, created_at FROM users WHERE username = ?",
            (username,),
        ).fetchone()
    if row is None:
        return None
    if not _verify_password(password, str(row["password_hash"])):
        return None
    return {
        "id": str(row["id"]),
        "username": str(row["username"]),
        "role": str(row["role"] or "user"),
        "createdAt": int(row["created_at"]),
    }


def create_session(db_path: Path, user_id: str) -> str:
    now = int(time.time() * 1000)
    token = secrets.token_urlsafe(32)
    with _connect(db_path) as conn:
        conn.execute(
            "INSERT INTO sessions(token, user_id, created_at) VALUES (?, ?, ?)",
            (token, user_id, now),
        )
    return token


def get_user_by_session(db_path: Path, token: str) -> dict[str, Any] | None:
    with _connect(db_path) as conn:
        row = conn.execute(
            """
            SELECT u.id, u.username, u.role, u.created_at
            FROM sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.token = ?
            """,
            (token,),
        ).fetchone()
    if row is None:
        return None
    return {
        "id": str(row["id"]),
        "username": str(row["username"]),
        "role": str(row["role"] or "user"),
        "createdAt": int(row["created_at"]),
    }


def ensure_admin_user(db_path: Path, username: str, password: str) -> dict[str, Any]:
    now = int(time.time() * 1000)
    normalized_role = "admin"
    with _connect(db_path) as conn:
        target_row = conn.execute(
            "SELECT id, created_at FROM users WHERE username = ?",
            (username,),
        ).fetchone()
        source_admin_row = conn.execute(
            """
            SELECT id, created_at
            FROM users
            WHERE role = 'admin'
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            """,
        ).fetchone()

        if source_admin_row is not None:
            # Preserve existing admin account identity/data and migrate its username.
            user_id = str(source_admin_row["id"])
            created_at = int(source_admin_row["created_at"] or now)
            if target_row is not None and str(target_row["id"]) != user_id:
                # Free the env username by removing conflicting account.
                conn.execute("DELETE FROM users WHERE id = ?", (str(target_row["id"]),))
        else:
            if target_row is not None:
                user_id = str(target_row["id"])
                created_at = int(target_row["created_at"] or now)
            else:
                user_id = secrets.token_hex(16)
                created_at = now
                conn.execute(
                    "INSERT INTO users(id, username, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)",
                    (user_id, username, _hash_password(password), normalized_role, created_at),
                )

        conn.execute(
            "UPDATE users SET username = ?, password_hash = ?, role = ? WHERE id = ?",
            (username, _hash_password(password), normalized_role, user_id),
        )
        conn.execute(
            "DELETE FROM users WHERE role = 'admin' AND id != ?",
            (user_id,),
        )
        return {
            "id": user_id,
            "username": username,
            "role": normalized_role,
            "createdAt": created_at,
        }


def log_usage_event(
    db_path: Path,
    *,
    user_id: str | None,
    endpoint: str,
    model: str,
    transcript: str,
    success: bool,
    input_tokens: int = 0,
    output_tokens: int = 0,
    total_tokens: int = 0,
    latency_ms: int = 0,
    llm_input_text: str | None = None,
    llm_output_text: str | None = None,
    error_code: str | None = None,
    error_message: str | None = None,
) -> None:
    now = int(time.time() * 1000)
    with _connect(db_path) as conn:
        conn.execute(
            """
            INSERT INTO usage_events(
                user_id, endpoint, model, transcript, success,
                input_tokens, output_tokens, total_tokens, latency_ms,
                llm_input_text, llm_output_text, error_code, error_message, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                user_id,
                endpoint,
                model,
                transcript,
                1 if success else 0,
                max(0, input_tokens),
                max(0, output_tokens),
                max(0, total_tokens),
                max(0, latency_ms),
                llm_input_text,
                llm_output_text,
                error_code,
                error_message,
                now,
            ),
        )


def usage_overview(db_path: Path) -> dict[str, Any]:
    with _connect(db_path) as conn:
        row = conn.execute(
            """
            SELECT
                COUNT(1) AS request_count,
                SUM(total_tokens) AS total_tokens,
                AVG(total_tokens) AS avg_tokens_per_request,
                AVG(CASE WHEN success = 1 THEN 1.0 ELSE 0.0 END) AS success_rate
            FROM usage_events
            """
        ).fetchone()
        user_rows = conn.execute(
            """
            SELECT
                COALESCE(u.username, 'anonymous') AS username,
                ue.user_id AS user_id,
                COUNT(1) AS request_count,
                SUM(ue.total_tokens) AS total_tokens,
                AVG(ue.total_tokens) AS avg_tokens_per_request,
                AVG(CASE WHEN ue.success = 1 THEN 1.0 ELSE 0.0 END) AS success_rate
            FROM usage_events ue
            LEFT JOIN users u ON u.id = ue.user_id
            GROUP BY ue.user_id, u.username
            ORDER BY total_tokens DESC
            """
        ).fetchall()

    users = [
        {
            "userId": str(r["user_id"]) if r["user_id"] is not None else None,
            "username": str(r["username"]),
            "requestCount": int(r["request_count"] or 0),
            "totalTokens": int(r["total_tokens"] or 0),
            "avgTokensPerRequest": float(r["avg_tokens_per_request"] or 0.0),
            "successRate": float(r["success_rate"] or 0.0),
        }
        for r in user_rows
    ]
    avg_total = 0.0
    if users:
        avg_total = sum(float(item["totalTokens"]) for item in users) / float(len(users))
    outliers = [item for item in users if float(item["totalTokens"]) > avg_total * 1.8 and item["totalTokens"] > 0]
    return {
        "requestCount": int(row["request_count"] or 0),
        "totalTokens": int(row["total_tokens"] or 0),
        "avgTokensPerRequest": float(row["avg_tokens_per_request"] or 0.0),
        "successRate": float(row["success_rate"] or 0.0),
        "userAverageTotalTokens": avg_total,
        "outliers": outliers,
        "users": users,
    }


def usage_user_detail(db_path: Path, user_id: str | None, limit: int = 100) -> dict[str, Any]:
    with _connect(db_path) as conn:
        if user_id:
            params = (user_id,)
            where_sql = "ue.user_id = ?"
        else:
            params = tuple()
            where_sql = "ue.user_id IS NULL"
        summary = conn.execute(
            f"""
            SELECT
                COALESCE(u.username, 'anonymous') AS username,
                ue.user_id AS user_id,
                COUNT(1) AS request_count,
                SUM(ue.total_tokens) AS total_tokens,
                AVG(ue.total_tokens) AS avg_tokens_per_request,
                AVG(CASE WHEN ue.success = 1 THEN 1.0 ELSE 0.0 END) AS success_rate
            FROM usage_events ue
            LEFT JOIN users u ON u.id = ue.user_id
            WHERE {where_sql}
            """,
            params,
        ).fetchone()
        events = conn.execute(
            f"""
            SELECT
                endpoint, model, transcript, success,
                input_tokens, output_tokens, total_tokens, latency_ms,
                llm_input_text, llm_output_text, error_code, error_message, created_at
            FROM usage_events ue
            WHERE {where_sql}
            ORDER BY created_at DESC
            LIMIT ?
            """,
            (*params, int(limit)),
        ).fetchall()

    return {
        "userId": str(summary["user_id"]) if summary and summary["user_id"] is not None else None,
        "username": str(summary["username"]) if summary else "unknown",
        "requestCount": int(summary["request_count"] or 0) if summary else 0,
        "totalTokens": int(summary["total_tokens"] or 0) if summary else 0,
        "avgTokensPerRequest": float(summary["avg_tokens_per_request"] or 0.0) if summary else 0.0,
        "successRate": float(summary["success_rate"] or 0.0) if summary else 0.0,
        "events": [
            {
                "endpoint": str(row["endpoint"]),
                "model": str(row["model"]),
                "transcript": str(row["transcript"]),
                "success": bool(int(row["success"] or 0)),
                "inputTokens": int(row["input_tokens"] or 0),
                "outputTokens": int(row["output_tokens"] or 0),
                "totalTokens": int(row["total_tokens"] or 0),
                "latencyMs": int(row["latency_ms"] or 0),
                "llmInput": str(row["llm_input_text"]) if row["llm_input_text"] is not None else None,
                "llmOutput": str(row["llm_output_text"]) if row["llm_output_text"] is not None else None,
                "errorCode": str(row["error_code"]) if row["error_code"] is not None else None,
                "errorMessage": str(row["error_message"]) if row["error_message"] is not None else None,
                "createdAt": int(row["created_at"] or 0),
            }
            for row in events
        ],
    }


class UserDeleteError(ValueError):
    pass


def list_users(db_path: Path) -> list[dict[str, Any]]:
    with _connect(db_path) as conn:
        rows = conn.execute(
            """
            SELECT id, username, role, created_at
            FROM users
            ORDER BY created_at ASC, username ASC
            """
        ).fetchall()
    return [
        {
            "id": str(row["id"]),
            "username": str(row["username"]),
            "role": str(row["role"] or "user"),
            "createdAt": int(row["created_at"] or 0),
        }
        for row in rows
    ]


def delete_user(db_path: Path, user_id: str, protected_user_id: str | None = None) -> dict[str, Any]:
    with _connect(db_path) as conn:
        row = conn.execute(
            "SELECT id, username, role FROM users WHERE id = ?",
            (user_id,),
        ).fetchone()
        if row is None:
            raise UserDeleteError("USER_NOT_FOUND")
        target_user_id = str(row["id"])
        if protected_user_id and target_user_id == protected_user_id:
            raise UserDeleteError("CANNOT_DELETE_SELF")
        target_role = str(row["role"] or "user").strip().lower()
        if target_role == "admin":
            admin_count = int(
                conn.execute("SELECT COUNT(1) AS cnt FROM users WHERE role = 'admin'").fetchone()["cnt"] or 0
            )
            if admin_count <= 1:
                raise UserDeleteError("LAST_ADMIN")
        conn.execute("DELETE FROM sessions WHERE user_id = ?", (target_user_id,))
        conn.execute("DELETE FROM users WHERE id = ?", (target_user_id,))
        return {
            "id": target_user_id,
            "username": str(row["username"]),
            "role": target_role,
        }


def _hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 200_000)
    return f"{salt}${digest.hex()}"


def _verify_password(password: str, stored_hash: str) -> bool:
    parts = stored_hash.split("$", 1)
    if len(parts) != 2:
        return False
    salt, expected = parts
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 200_000)
    return secrets.compare_digest(digest.hex(), expected)


def _ensure_users_role_column(conn: sqlite3.Connection) -> None:
    columns = conn.execute("PRAGMA table_info(users)").fetchall()
    column_names = {str(row["name"]) for row in columns}
    if "role" not in column_names:
        conn.execute("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'")


def _ensure_usage_events_input_column(conn: sqlite3.Connection) -> None:
    columns = conn.execute("PRAGMA table_info(usage_events)").fetchall()
    column_names = {str(row["name"]) for row in columns}
    if "llm_input_text" not in column_names:
        conn.execute("ALTER TABLE usage_events ADD COLUMN llm_input_text TEXT")


def _ensure_usage_events_output_column(conn: sqlite3.Connection) -> None:
    columns = conn.execute("PRAGMA table_info(usage_events)").fetchall()
    column_names = {str(row["name"]) for row in columns}
    if "llm_output_text" not in column_names:
        conn.execute("ALTER TABLE usage_events ADD COLUMN llm_output_text TEXT")


def _normalize_role(role: str) -> str:
    value = role.strip().lower()
    if value == "admin":
        return "admin"
    return "user"
