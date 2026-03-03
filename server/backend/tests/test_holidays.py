from fastapi.testclient import TestClient

import app.main as main_module
from app.holiday_store import StoredHoliday
from app.main import app


client = TestClient(app)


def test_holidays_empty_when_not_synced():
    async def fake_ensure_range_loaded(*args, **kwargs):
        return []

    original = main_module.ensure_range_loaded
    main_module.ensure_range_loaded = fake_ensure_range_loaded
    try:
        response = client.get("/holidays", params={"startDate": "2026-03-01", "endDate": "2026-03-31"})
        assert response.status_code == 200
        data = response.json()
        assert data["holidays"] == []
    finally:
        main_module.ensure_range_loaded = original


def test_holidays_success_shape():
    async def fake_ensure_range_loaded(*args, **kwargs):
        return [
            StoredHoliday(date=main_module.date(2026, 3, 1), label="삼일절", kind="PUBLIC_HOLIDAY"),
            StoredHoliday(date=main_module.date(2026, 3, 14), label="화이트데이", kind="COMMEMORATIVE"),
        ]

    original = main_module.ensure_range_loaded
    main_module.ensure_range_loaded = fake_ensure_range_loaded
    try:
        response = client.get("/holidays", params={"startDate": "2026-03-01", "endDate": "2026-03-31"})
        assert response.status_code == 200
        data = response.json()
        assert "holidays" in data
        assert data["holidays"][0]["date"] == "2026-03-01"
        assert data["holidays"][0]["kind"] == "PUBLIC_HOLIDAY"
    finally:
        main_module.ensure_range_loaded = original
