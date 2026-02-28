#!/usr/bin/env python3
from __future__ import annotations

import argparse
import asyncio
from datetime import date
from pathlib import Path
import sys

from app.config import settings
from app.holiday_sync import HolidaySyncError, run_startup_sync, sync_holidays_range
from app.holiday_store import init_holiday_db


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync holiday data into local server DB")
    parser.add_argument("--start-date", help="YYYY-MM-DD")
    parser.add_argument("--end-date", help="YYYY-MM-DD")
    args = parser.parse_args()

    db_path = Path(settings.holiday_db_path)
    init_holiday_db(db_path)

    async def _run() -> int:
        try:
            if args.start_date and args.end_date:
                start = date.fromisoformat(args.start_date)
                end = date.fromisoformat(args.end_date)
                result = await sync_holidays_range(db_path=db_path, start=start, end=end)
                print(
                    f"holiday_sync_ok range={result.start.isoformat()}..{result.end.isoformat()} "
                    f"count={result.fetched_count}"
                )
                return 0
            result = await run_startup_sync(db_path)
            if result is None:
                print("holiday_sync_skipped")
                return 0
            print(
                f"holiday_sync_ok range={result.start.isoformat()}..{result.end.isoformat()} "
                f"count={result.fetched_count}"
            )
            return 0
        except HolidaySyncError as exc:
            print(f"holiday_sync_failed reason={exc}")
            return 1
        except ValueError as exc:
            print(f"holiday_sync_failed reason={exc}")
            return 1

    return asyncio.run(_run())


if __name__ == "__main__":
    sys.exit(main())
