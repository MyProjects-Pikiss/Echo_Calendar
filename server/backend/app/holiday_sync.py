from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from .config import settings
from .holiday_client import HolidayClientError, HolidayItem, fetch_holiday_items
from .holiday_store import (
    StoredHoliday,
    get_holidays_in_range,
    get_state,
    init_holiday_db,
    replace_holidays_in_range,
    set_state,
)


class HolidaySyncError(Exception):
    pass


@dataclass(frozen=True)
class HolidaySyncResult:
    start: date
    end: date
    fetched_count: int


def _bootstrap_range(today: date) -> tuple[date, date]:
    start = date.fromisoformat(settings.holiday_bootstrap_start_date)
    end = today + timedelta(days=int(settings.holiday_bootstrap_forward_years * 365.25))
    return start, end


def _daily_range(today: date) -> tuple[date, date]:
    span = timedelta(days=int(settings.holiday_daily_window_years * 365.25))
    return today - span, today + span


async def sync_holidays_range(
    *,
    db_path: Path,
    start: date,
    end: date,
) -> HolidaySyncResult:
    if not settings.holiday_api_key:
        raise HolidaySyncError("KOREA_HOLIDAY_API_KEY is required")
    if start > end:
        raise HolidaySyncError("start date must be <= end date")

    try:
        fetched = await fetch_holiday_items(
            start=start,
            end=end,
            service_key=settings.holiday_api_key,
            base_url=settings.holiday_api_base_url,
            timeout_seconds=settings.holiday_api_timeout_seconds,
            max_concurrency=settings.holiday_api_max_concurrency,
        )
    except HolidayClientError as exc:
        raise HolidaySyncError(str(exc)) from exc

    to_store = [
        StoredHoliday(date=item.date, label=item.label, kind=item.kind)
        for item in fetched
    ]
    replace_holidays_in_range(db_path, start=start, end=end, items=to_store)
    set_state(db_path, "last_sync_at", datetime.now(timezone.utc).isoformat())
    return HolidaySyncResult(start=start, end=end, fetched_count=len(to_store))


async def run_startup_sync(db_path: Path) -> HolidaySyncResult | None:
    init_holiday_db(db_path)
    today = date.today()
    bootstrap_done = get_state(db_path, "bootstrap_done") == "true"
    if not settings.holiday_sync_enabled:
        return None
    if not settings.holiday_api_key:
        return None

    if not bootstrap_done:
        start, end = _bootstrap_range(today)
        result = await sync_holidays_range(db_path=db_path, start=start, end=end)
        set_state(db_path, "bootstrap_done", "true")
        set_state(db_path, "last_daily_sync_date", today.isoformat())
        return result

    last_daily_sync = get_state(db_path, "last_daily_sync_date")
    if last_daily_sync == today.isoformat():
        return None
    start, end = _daily_range(today)
    result = await sync_holidays_range(db_path=db_path, start=start, end=end)
    set_state(db_path, "last_daily_sync_date", today.isoformat())
    return result


async def ensure_range_loaded(db_path: Path, start: date, end: date) -> list[StoredHoliday]:
    init_holiday_db(db_path)
    return get_holidays_in_range(db_path, start, end)
