from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import date
from urllib.parse import unquote
import xml.etree.ElementTree as ET

import httpx


class HolidayClientError(Exception):
    pass


@dataclass(frozen=True)
class HolidayItem:
    date: date
    label: str
    kind: str  # PUBLIC_HOLIDAY | COMMEMORATIVE


def _month_sequence(start: date, end: date) -> list[tuple[int, int]]:
    months: list[tuple[int, int]] = []
    year = start.year
    month = start.month
    while (year, month) <= (end.year, end.month):
        months.append((year, month))
        if month == 12:
            year += 1
            month = 1
        else:
            month += 1
    return months


def _parse_xml_items(xml_text: str) -> list[HolidayItem]:
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as exc:
        raise HolidayClientError("holiday api returned malformed XML") from exc

    result_code = (root.findtext(".//header/resultCode") or "").strip()
    result_msg = (root.findtext(".//header/resultMsg") or "").strip()
    if result_code and result_code != "00":
        raise HolidayClientError(f"holiday api error code={result_code} msg={result_msg}")

    items: list[HolidayItem] = []
    for item in root.findall(".//item"):
        locdate = (item.findtext("locdate") or "").strip()
        date_name = (item.findtext("dateName") or "").strip()
        is_holiday = (item.findtext("isHoliday") or "").strip().upper()
        if len(locdate) != 8 or not locdate.isdigit():
            continue
        year = int(locdate[0:4])
        month = int(locdate[4:6])
        day = int(locdate[6:8])
        parsed_date = date(year, month, day)
        label = date_name or "특일"
        kind = "PUBLIC_HOLIDAY" if is_holiday == "Y" else "COMMEMORATIVE"
        items.append(HolidayItem(date=parsed_date, label=label, kind=kind))
    return items


async def fetch_holiday_items(
    *,
    start: date,
    end: date,
    service_key: str,
    base_url: str,
    timeout_seconds: float,
    max_concurrency: int,
) -> list[HolidayItem]:
    normalized_key = unquote(service_key.strip())
    if not normalized_key:
        raise HolidayClientError("holiday api key is empty")
    if start > end:
        raise HolidayClientError("start date must be before end date")

    months = _month_sequence(start, end)
    semaphore = asyncio.Semaphore(max(1, max_concurrency))

    async with httpx.AsyncClient(timeout=timeout_seconds) as client:
        async def fetch_one(year: int, month: int) -> list[HolidayItem]:
            async with semaphore:
                response = await client.get(
                    base_url,
                    params={
                        "serviceKey": normalized_key,
                        "solYear": f"{year:04d}",
                        "solMonth": f"{month:02d}",
                        "numOfRows": "100",
                        "pageNo": "1",
                    },
                )
                response.raise_for_status()
                return _parse_xml_items(response.text)

        tasks = [fetch_one(year, month) for year, month in months]
        try:
            gathered = await asyncio.gather(*tasks)
        except httpx.HTTPError as exc:
            raise HolidayClientError(f"holiday api request failed: {exc}") from exc

    by_key: dict[tuple[date, str], HolidayItem] = {}
    for month_items in gathered:
        for item in month_items:
            if item.date < start or item.date > end:
                continue
            by_key[(item.date, item.kind)] = item

    return sorted(by_key.values(), key=lambda item: (item.date, item.kind, item.label))
