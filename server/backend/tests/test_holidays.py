from fastapi.testclient import TestClient

import app.main as main_module
from app.holiday_store import StoredHoliday
from app.holiday_sync import HolidaySyncError
from app.main import app


client = TestClient(app)


def test_holidays_requires_api_key():
    async def fake_ensure_range_loaded(*args, **kwargs):
        raise HolidaySyncError("KOREA_HOLIDAY_API_KEY is required")

    original = main_module.ensure_range_loaded
    main_module.ensure_range_loaded = fake_ensure_range_loaded
    try:
        response = client.get("/holidays", params={"startDate": "2026-03-01", "endDate": "2026-03-31"})
        assert response.status_code == 503
        data = response.json()
        assert data["mode"] == "holiday"
        assert data["errorCode"] == "NOT_CONFIGURED"
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
