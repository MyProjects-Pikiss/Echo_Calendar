#!/usr/bin/env python3
"""Validate Echo Calendar AI backend contract endpoints.

Usage:
  python tools/check_ai_backend_contract.py --base-url http://127.0.0.1:8088
"""

from __future__ import annotations

import argparse
import json
import sys
import socket
import urllib.error
import urllib.request


def post_json(base_url: str, path: str, payload: dict, timeout: float) -> tuple[int, dict]:
    req = urllib.request.Request(
        url=base_url.rstrip("/") + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status = response.getcode()
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        status = exc.code
        body = exc.read().decode("utf-8")
    except (urllib.error.URLError, TimeoutError, socket.timeout) as exc:
        return 0, {"error": str(exc), "path": path}

    try:
        return status, json.loads(body)
    except json.JSONDecodeError:
        return status, {"raw": body}


def expect_keys(name: str, payload: dict, keys: list[str]) -> list[str]:
    missing = [k for k in keys if k not in payload]
    return [f"[{name}] missing key: {k}" for k in missing]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--timeout", type=float, default=20.0)
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--print-response", action="store_true")
    args = parser.parse_args()

    failures: list[str] = []
    show_response = args.verbose or args.print_response

    status, data = post_json(
        args.base_url,
        "/ai/input-interpret",
        {"mode": "input", "transcript": "내일 9시 회의", "selectedDate": "2026-02-11"},
        args.timeout,
    )
    if status != 200:
        failures.append(f"[input] expected 200, got {status}: {data}")
    else:
        failures.extend(expect_keys("input", data, ["mode", "intent", "summary", "time", "repeatYearly", "categoryId", "body", "missingRequired"]))
        if data.get("mode") != "input":
            failures.append(f"[input] mode mismatch: {data.get('mode')}")
    if show_response:
        print("[response][input]")
        print(json.dumps(data, ensure_ascii=False, indent=2))

    status, data = post_json(
        args.base_url,
        "/ai/search-interpret",
        {"mode": "search", "transcript": "지난주 병원 일정 찾아줘"},
        args.timeout,
    )
    if status != 200:
        failures.append(f"[search] expected 200, got {status}: {data}")
    else:
        failures.extend(expect_keys("search", data, ["mode", "query", "categoryIds", "labels"]))
        if data.get("mode") != "search":
            failures.append(f"[search] mode mismatch: {data.get('mode')}")
    if show_response:
        print("[response][search]")
        print(json.dumps(data, ensure_ascii=False, indent=2))

    status, data = post_json(
        args.base_url,
        "/ai/refine-field",
        {
            "mode": "refine",
            "transcript": "3시 30분",
            "field": "time",
            "currentValue": "09:00",
            "selectedDate": "2026-02-11",
        },
        args.timeout,
    )
    if status != 200:
        failures.append(f"[refine] expected 200, got {status}: {data}")
    else:
        failures.extend(expect_keys("refine", data, ["mode", "field", "value", "missingRequired"]))
        if data.get("mode") != "refine":
            failures.append(f"[refine] mode mismatch: {data.get('mode')}")
    if show_response:
        print("[response][refine]")
        print(json.dumps(data, ensure_ascii=False, indent=2))

    status, data = post_json(
        args.base_url,
        "/ai/modify-interpret",
        {
            "mode": "modify",
            "transcript": "시간을 3시 30분으로 바꿔줘",
            "selectedDate": "2026-02-11",
            "currentSummary": "팀 회의",
            "currentTime": "09:00",
            "currentCategoryId": "work",
            "currentPlaceText": "",
            "currentBody": "",
            "currentLabels": [],
        },
        args.timeout,
    )
    if status != 200:
        failures.append(f"[modify] expected 200, got {status}: {data}")
    else:
        failures.extend(expect_keys("modify", data, ["mode", "summary", "time", "categoryId", "placeText", "body", "labels", "missingRequired"]))
        if data.get("mode") != "modify":
            failures.append(f"[modify] mode mismatch: {data.get('mode')}")
    if show_response:
        print("[response][modify]")
        print(json.dumps(data, ensure_ascii=False, indent=2))

    if failures:
        print("AI backend contract check failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("AI backend contract check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
