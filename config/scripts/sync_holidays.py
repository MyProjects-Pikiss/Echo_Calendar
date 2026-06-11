#!/usr/bin/env python3
from __future__ import annotations

from datetime import date, timedelta
from pathlib import Path
import subprocess
import sys


REPO_ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = REPO_ROOT / "config" / "echo-calendar.config.env"


def _parse_config() -> dict[str, str]:
    values: dict[str, str] = {}
    if not CONFIG_PATH.is_file():
        return values
    for raw_line in CONFIG_PATH.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def _shift_years(value: date, years: int) -> date:
    target_year = value.year + years
    try:
        return value.replace(year=target_year)
    except ValueError:
        return value.replace(year=target_year, day=28)


def _range_label(start: date, end: date) -> str:
    return f"{start.isoformat()} .. {end.isoformat()}"


def _menu_state() -> dict[str, date]:
    config = _parse_config()
    today = date.today()
    bootstrap_start = date.fromisoformat(config.get("HOLIDAY_BOOTSTRAP_START_DATE", "1970-01-01"))
    forward_years = float(config.get("HOLIDAY_BOOTSTRAP_FORWARD_YEARS", "5"))
    bootstrap_end = today + timedelta(days=int(forward_years * 365.25))
    return {
        "today": today,
        "bootstrap_start": bootstrap_start,
        "bootstrap_end": bootstrap_end,
    }


def _run_backend_sync(args: list[str]) -> int:
    command = [
        "docker",
        "compose",
        "run",
        "--rm",
        "backend",
        "python",
        "scripts/sync_holidays.py",
        *args,
    ]
    return subprocess.run(command, cwd=REPO_ROOT / "server", check=False).returncode


def _input_with_default(prompt: str, default: str) -> str:
    raw = input(f"{prompt} [{default}]: ").strip()
    return raw or default


def _interactive_args() -> list[str] | None:
    state = _menu_state()
    print("")
    print("Echo Calendar server holiday DB sync")
    print("This updates the server DB used by the /holidays API.")
    print(f"Configured full range: {_range_label(state['bootstrap_start'], state['bootstrap_end'])}")
    print("")
    print("1. Sync the configured full range")
    print(f"   Update every holiday from {_range_label(state['bootstrap_start'], state['bootstrap_end'])}.")
    print("2. Sync around today")
    print("   Ask for N, then update today -N years through today +N years.")
    print("3. Sync one year")
    print("   Ask for a year, then update YYYY-01-01 through YYYY-12-31.")
    print("4. Sync a custom date range")
    print("   Update only the start/end dates you enter.")
    print("5. Exit without syncing")
    print("")

    choice = input("Choose an option [1]: ").strip() or "1"
    if choice == "1":
        return [
            "--start-date",
            state["bootstrap_start"].isoformat(),
            "--end-date",
            state["bootstrap_end"].isoformat(),
        ]
    if choice == "2":
        years = _input_with_default("Years before/after today", "5")
        try:
            parsed_years = max(0, int(years))
            start = _shift_years(state["today"], -parsed_years)
            end = _shift_years(state["today"], parsed_years)
            print(f"Selected range: {_range_label(start, end)}")
        except ValueError:
            pass
        return ["--today-window-years", years]
    if choice == "3":
        year = _input_with_default("Year (YYYY)", str(state["today"].year))
        start = f"{year}-01-01"
        end = f"{year}-12-31"
        print(f"Selected range: {start} .. {end}")
        return ["--start-date", start, "--end-date", end]
    if choice == "4":
        start = _input_with_default("Start date (YYYY-MM-DD)", "2020-01-01")
        end = _input_with_default("End date (YYYY-MM-DD)", "2030-12-31")
        return ["--start-date", start, "--end-date", end]
    if choice == "5":
        return None

    print(f"Unknown option: {choice}")
    return None


def main(argv: list[str] | None = None) -> int:
    args = list(argv or [])
    if args:
        print("Command-line holiday sync options are not supported from this wrapper.")
        print("Run config\\SYNC_HOLIDAYS.bat and choose from the menu.")
        return 2

    selected_args = _interactive_args()
    if selected_args is None:
        return 0
    args = selected_args

    return _run_backend_sync(args)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
