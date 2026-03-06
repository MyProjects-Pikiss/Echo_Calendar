from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_input_requires_mode_match():
    response = client.post(
        "/ai/input-interpret",
        json={"mode": "search", "transcript": "내일 회의", "selectedDate": "2026-02-11"},
    )
    assert response.status_code == 400
    data = response.json()
    assert data["mode"] == "input"
    assert data["errorCode"] == "MODE_MISMATCH"


def test_search_missing_transcript():
    response = client.post("/ai/search-interpret", json={"mode": "search", "transcript": ""})
    assert response.status_code == 400
    data = response.json()
    assert data["mode"] == "search"
    assert data["errorCode"] == "MISSING_TRANSCRIPT"


def test_refine_invalid_field():
    response = client.post(
        "/ai/refine-field",
        json={
            "mode": "refine",
            "transcript": "3시로 바꿔",
            "field": "unknown",
            "currentValue": "",
            "selectedDate": "2026-02-11",
        },
    )
    assert response.status_code == 400
    data = response.json()
    assert data["mode"] == "refine"
    assert data["errorCode"] == "INVALID_FIELD"


def test_input_success_includes_mode():
    response = client.post(
        "/ai/input-interpret",
        json={"mode": "input", "transcript": "내일 9시 회의", "selectedDate": "2026-02-11"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "input"
    assert "summary" in data


def test_search_success_includes_labels():
    response = client.post(
        "/ai/search-interpret",
        json={"mode": "search", "transcript": "병원 #진료 일정 찾아줘"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "search"
    assert data["strategy"] in {"combined", "all_events", "date_range", "category", "label", "keyword"}
    assert "labels" in data
    assert data["sortOrder"] in {"asc", "desc"}


def test_modify_success_includes_patch_fields():
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
    )
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "modify"
    assert "time" in data
