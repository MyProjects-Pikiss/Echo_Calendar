#!/usr/bin/env python3
from __future__ import annotations

import argparse
import asyncio
from datetime import date, timedelta
from pathlib import Path
import sys

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.config import settings
from app.holiday_sync import HolidaySyncError, sync_holidays_range
from app.holiday_store import get_state, init_holiday_db, set_state

DEFAULT_CURSOR_KEY = "progressive_next_month"


class Reporter:
    def __init__(self, log_file: Path | None) -> None:
        self.log_file = log_file

    def emit(self, message: str) -> None:
        print(message, flush=True)
        if self.log_file is not None:
            self.log_file.parent.mkdir(parents=True, exist_ok=True)
            with self.log_file.open("a", encoding="utf-8") as f:
                f.write(message + "\n")


def _first_day(value: date) -> date:
    return date(value.year, value.month, 1)


def _add_months(value: date, months: int) -> date:
    total = value.year * 12 + (value.month - 1) + months
    year, month_idx = divmod(total, 12)
    return date(year, month_idx + 1, 1)


def _last_day_of_month(value: date) -> date:
    return _add_months(_first_day(value), 1) - timedelta(days=1)


def _shift_years(value: date, years: int) -> date:
    target_year = value.year + years
    try:
        return value.replace(year=target_year)
    except ValueError:
        # Handle leap day when target year is not leap year.
        return value.replace(year=target_year, day=28)


async def _sync_range_in_chunks(
    db_path: Path,
    *,
    start: date,
    end: date,
    chunk_months: int,
    sleep_seconds: float,
    reporter: Reporter,
) -> int:
    if start > end:
        raise ValueError("start date must be <= end date")

    current = start
    total_count = 0
    chunk_no = 0
    while current <= end:
        cursor_month = _first_day(current)
        next_month = _add_months(cursor_month, max(1, chunk_months))
        chunk_end = min(end, next_month - timedelta(days=1))
        result = await sync_holidays_range(db_path=db_path, start=current, end=chunk_end)
        chunk_no += 1
        total_count += result.fetched_count
        reporter.emit(
            f"holiday_sync_chunk_ok chunk={chunk_no} "
            f"range={result.start.isoformat()}..{result.end.isoformat()} "
            f"count={result.fetched_count}"
        )
        current = chunk_end + timedelta(days=1)
        if current <= end and sleep_seconds > 0:
            await asyncio.sleep(sleep_seconds)
    return total_count


async def _run_progressive(args: argparse.Namespace, db_path: Path, reporter: Reporter) -> int:
    today = date.today()
    start = date.fromisoformat(args.progressive_start_date)
    if args.progressive_end_date:
        end = date.fromisoformat(args.progressive_end_date)
    else:
        end = today + timedelta(days=int(settings.holiday_bootstrap_forward_years * 365.25))
    if start > end:
        raise ValueError("progressive start date must be <= progressive end date")

    if args.full_refresh:
        cursor = _first_day(start)
    else:
        cursor_raw = get_state(db_path, args.cursor_key)
        cursor = _first_day(start)
        if cursor_raw:
            parsed = date.fromisoformat(cursor_raw)
            cursor = _first_day(parsed)
        if cursor < _first_day(start):
            cursor = _first_day(start)

    run_months = max(1, args.progressive_months_per_run)
    while True:
        if cursor > end:
            reporter.emit(
                "holiday_sync_progressive_complete "
                f"cursor={cursor.isoformat()} end={end.isoformat()}"
            )
            return 0

        run_start = max(cursor, start)
        run_end = min(end, _add_months(cursor, run_months) - timedelta(days=1))
        total_count = await _sync_range_in_chunks(
            db_path,
            start=run_start,
            end=run_end,
            chunk_months=max(1, args.chunk_months),
            sleep_seconds=max(0.0, args.sleep_seconds),
            reporter=reporter,
        )
        next_cursor = _add_months(_first_day(run_start), run_months)
        set_state(db_path, args.cursor_key, next_cursor.isoformat())
        reporter.emit(
            "holiday_sync_progressive_ok "
            f"range={run_start.isoformat()}..{run_end.isoformat()} "
            f"next_cursor={next_cursor.isoformat()} total_count={total_count}"
        )
        if not args.until_complete:
            return 0
        cursor = next_cursor
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync holiday data into local server DB")
    parser.add_argument("--start-date", help="YYYY-MM-DD")
    parser.add_argument("--end-date", help="YYYY-MM-DD")
    parser.add_argument(
        "--today-window-years",
        type=int,
        help="Sync range [today-years, today+years], e.g. 5 => today±5y",
    )
    parser.add_argument(
        "--chunk-months",
        type=int,
        default=1,
        help="Number of months to sync per chunk (default: 1)",
    )
    parser.add_argument(
        "--sleep-seconds",
        type=float,
        default=1.5,
        help="Sleep seconds between chunks (default: 1.5)",
    )
    parser.add_argument(
        "--progressive-months-per-run",
        type=int,
        default=2,
        help="Months to sync in one progressive run (default: 2)",
    )
    parser.add_argument(
        "--progressive-start-date",
        default=settings.holiday_bootstrap_start_date,
        help="Progressive sync start date (default: HOLIDAY_BOOTSTRAP_START_DATE)",
    )
    parser.add_argument(
        "--progressive-end-date",
        help="Progressive sync end date (default: today + HOLIDAY_BOOTSTRAP_FORWARD_YEARS)",
    )
    parser.add_argument(
        "--cursor-key",
        default=DEFAULT_CURSOR_KEY,
        help=f"sync_state key for progressive cursor (default: {DEFAULT_CURSOR_KEY})",
    )
    parser.add_argument(
        "--until-complete",
        action="store_true",
        help="Keep running progressive sync until cursor reaches the end date",
    )
    parser.add_argument(
        "--full-refresh",
        action="store_true",
        help="Ignore saved cursor and resync the full progressive range from start",
    )
    parser.add_argument(
        "--log-file",
        help="Optional log file path for mirroring console messages",
    )
    args = parser.parse_args()

    db_path = Path(settings.holiday_db_path)
    init_holiday_db(db_path)
    reporter = Reporter(Path(args.log_file) if args.log_file else None)

    async def _run() -> int:
        try:
            has_start = bool(args.start_date)
            has_end = bool(args.end_date)
            if has_start != has_end:
                raise ValueError("both --start-date and --end-date are required together")

            if has_start and has_end:
                start = date.fromisoformat(args.start_date)
                end = date.fromisoformat(args.end_date)
                total_count = await _sync_range_in_chunks(
                    db_path,
                    start=start,
                    end=end,
                    chunk_months=max(1, args.chunk_months),
                    sleep_seconds=max(0.0, args.sleep_seconds),
                    reporter=reporter,
                )
                reporter.emit(f"holiday_sync_ok range={start.isoformat()}..{end.isoformat()} count={total_count}")
                return 0
            if args.today_window_years is not None:
                years = max(0, int(args.today_window_years))
                today = date.today()
                start = _shift_years(today, -years)
                end = _shift_years(today, years)
                total_count = await _sync_range_in_chunks(
                    db_path,
                    start=start,
                    end=end,
                    chunk_months=max(1, args.chunk_months),
                    sleep_seconds=max(0.0, args.sleep_seconds),
                    reporter=reporter,
                )
                reporter.emit(
                    f"holiday_sync_ok mode=today_window years={years} "
                    f"range={start.isoformat()}..{end.isoformat()} count={total_count}"
                )
                return 0
            return await _run_progressive(args, db_path, reporter)
        except HolidaySyncError as exc:
            reporter.emit(f"holiday_sync_failed reason={exc}")
            return 1
        except ValueError as exc:
            reporter.emit(f"holiday_sync_failed reason={exc}")
            return 1

    return asyncio.run(_run())


if __name__ == "__main__":
    sys.exit(main())
