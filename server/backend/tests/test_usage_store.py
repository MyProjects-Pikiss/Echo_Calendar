from pathlib import Path
import sqlite3

from app.usage_store import (
    authenticate_user,
    create_user,
    create_session,
    ensure_admin_user,
    get_user_by_session,
    init_usage_db,
)


def _fetch_user_roles(db_path: Path) -> dict[str, str]:
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        rows = conn.execute("SELECT username, role FROM users").fetchall()
        return {str(row["username"]): str(row["role"]) for row in rows}
    finally:
        conn.close()


def test_ensure_admin_user_replaces_admin_account_with_env_username_and_keeps_identity(tmp_path):
    db_path = tmp_path / "usage.db"
    init_usage_db(db_path)
    legacy = create_user(db_path, username="legacy_admin", password="pw-legacy", role="admin")
    token = create_session(db_path, legacy["id"])
    create_user(db_path, username="other_admin", password="pw-other", role="admin")
    create_user(db_path, username="target_user", password="pw-target", role="user")

    out = ensure_admin_user(db_path, username="target_user", password="new-admin-pw")

    assert out["id"] == legacy["id"]
    assert out["username"] == "target_user"
    assert out["role"] == "admin"
    roles = _fetch_user_roles(db_path)
    assert roles["target_user"] == "admin"
    assert "legacy_admin" not in roles
    assert "other_admin" not in roles
    assert authenticate_user(db_path, "target_user", "new-admin-pw") is not None
    session_user = get_user_by_session(db_path, token)
    assert session_user is not None
    assert session_user["id"] == legacy["id"]
    assert session_user["username"] == "target_user"


def test_ensure_admin_user_renames_existing_admin_when_target_missing(tmp_path):
    db_path = tmp_path / "usage.db"
    init_usage_db(db_path)
    legacy = create_user(db_path, username="legacy_admin", password="pw-legacy", role="admin")

    out = ensure_admin_user(db_path, username="env_admin", password="env-pw")

    assert out["id"] == legacy["id"]
    assert out["username"] == "env_admin"
    assert out["role"] == "admin"
    roles = _fetch_user_roles(db_path)
    assert "legacy_admin" not in roles
    assert roles["env_admin"] == "admin"
    assert authenticate_user(db_path, "legacy_admin", "pw-legacy") is None
    assert authenticate_user(db_path, "env_admin", "env-pw") is not None


def test_ensure_admin_user_promotes_existing_target_when_no_admin_exists(tmp_path):
    db_path = tmp_path / "usage.db"
    init_usage_db(db_path)
    target = create_user(db_path, username="env_admin", password="pw-user", role="user")

    out = ensure_admin_user(db_path, username="env_admin", password="env-pw")

    assert out["id"] == target["id"]
    assert out["username"] == "env_admin"
    assert out["role"] == "admin"
    roles = _fetch_user_roles(db_path)
    assert roles["env_admin"] == "admin"
    assert authenticate_user(db_path, "env_admin", "env-pw") is not None
