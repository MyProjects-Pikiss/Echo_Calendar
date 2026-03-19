from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app
from app.usage_store import create_session, create_user, init_usage_db


client = TestClient(app)


@pytest.fixture
def auth_headers(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> dict[str, str]:
    db_path = tmp_path / "usage.db"
    init_usage_db(db_path)
    user = create_user(db_path, username="contract_user", password="pw-1234")
    token = create_session(db_path, user["id"])
    monkeypatch.setattr(main_module, "usage_db_path", db_path)
    return {"Authorization": f"Bearer {token}"}


def test_ai_requires_auth():
    response = client.post(
        "/ai/search-interpret",
        json={"mode": "search", "transcript": "병원 일정 찾아줘"},
    )
    assert response.status_code == 401
    data = response.json()
    assert data["mode"] == "auth"
    assert data["errorCode"] == "UNAUTHORIZED"


def test_input_requires_mode_match(auth_headers: dict[str, str]):
    response = client.post(
        "/ai/input-interpret",
        json={"mode": "search", "transcript": "내일 회의", "selectedDate": "2026-02-11"},
        headers=auth_headers,
    )
    assert response.status_code == 400
    data = response.json()
    assert data["mode"] == "input"
    assert data["errorCode"] == "MODE_MISMATCH"


def test_search_missing_transcript(auth_headers: dict[str, str]):
    response = client.post(
        "/ai/search-interpret",
        json={"mode": "search", "transcript": ""},
        headers=auth_headers,
    )
    assert response.status_code == 400
    data = response.json()
    assert data["mode"] == "search"
    assert data["errorCode"] == "MISSING_TRANSCRIPT"


def test_refine_invalid_field(auth_headers: dict[str, str]):
    response = client.post(
        "/ai/refine-field",
        json={
            "mode": "refine",
            "transcript": "3시로 바꿔",
            "field": "unknown",
            "currentValue": "",
            "selectedDate": "2026-02-11",
        },
        headers=auth_headers,
    )
    assert response.status_code == 400
    data = response.json()
    assert data["mode"] == "refine"
    assert data["errorCode"] == "INVALID_FIELD"


def test_input_success_includes_mode(auth_headers: dict[str, str]):
    response = client.post(
        "/ai/input-interpret",
        json={"mode": "input", "transcript": "내일 9시 회의", "selectedDate": "2026-02-11"},
        headers=auth_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "input"
    assert "summary" in data


def test_search_success_includes_labels(auth_headers: dict[str, str]):
    response = client.post(
        "/ai/search-interpret",
        json={"mode": "search", "transcript": "병원 #진료 일정 찾아줘"},
        headers=auth_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "search"
    assert data["strategy"] in {"combined", "all_events", "date_range", "category", "label", "keyword"}
    assert "labels" in data
    assert data["sortOrder"] in {"asc", "desc"}


def test_modify_success_includes_patch_fields(auth_headers: dict[str, str]):
    response = client.post(
        "/ai/modify-interpret",
        json={
            "mode": "modify",
            "transcript": "시간을 3시 30분으로 바꿔줘",
            "selectedDate": "2026-02-11",
            "currentSummary": "회의",
            "currentTime": "09:00",
            "currentCategoryId": "work",
            "currentPlaceText": "",
            "currentBody": "",
            "currentLabels": [],
        },
        headers=auth_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "modify"
    assert "time" in data


def test_login_sets_http_only_session_cookie(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    db_path = tmp_path / "usage.db"
    init_usage_db(db_path)
    create_user(db_path, username="cookie_user", password="pw-1234")
    monkeypatch.setattr(main_module, "usage_db_path", db_path)

    response = client.post("/auth/login", json={"username": "cookie_user", "password": "pw-1234"})

    assert response.status_code == 200
    set_cookie = response.headers.get("set-cookie", "")
    assert "echo_usage_session=" in set_cookie
    assert "HttpOnly" in set_cookie


def test_auth_me_accepts_session_cookie(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    db_path = tmp_path / "usage.db"
    init_usage_db(db_path)
    user = create_user(db_path, username="cookie_user", password="pw-1234")
    token = create_session(db_path, user["id"])
    monkeypatch.setattr(main_module, "usage_db_path", db_path)

    response = client.get("/auth/me", cookies={"echo_usage_session": token})

    assert response.status_code == 200
    data = response.json()
    assert data["user"]["username"] == "cookie_user"


def test_logout_clears_cookie_session(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    db_path = tmp_path / "usage.db"
    init_usage_db(db_path)
    user = create_user(db_path, username="cookie_user", password="pw-1234")
    token = create_session(db_path, user["id"])
    monkeypatch.setattr(main_module, "usage_db_path", db_path)

    response = client.post("/auth/logout", cookies={"echo_usage_session": token})

    assert response.status_code == 200
    assert get_user_by_session(db_path, token) is None
    assert "echo_usage_session=" in response.headers.get("set-cookie", "")
