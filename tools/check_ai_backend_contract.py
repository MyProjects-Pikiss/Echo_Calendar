#!/usr/bin/env python3
"""Validate Echo Calendar AI backend contract endpoints.

Usage:
  python tools/check_ai_backend_contract.py --base-url http://127.0.0.1:8088
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request


def post_json(base_url: str, path: str, payload: dict) -> tuple[int, dict]:
    req = urllib.request.Request(
        url=base_url.rstrip("/") + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=8) as response:
            status = response.getcode()
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        status = exc.code
        body = exc.read().decode("utf-8")
    return status, json.loads(body)


def expect_keys(name: str, payload: dict, keys: list[str]) -> list[str]:
    missing = [k for k in keys if k not in payload]
    return [f"[{name}] missing key: {k}" for k in missing]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    args = parser.parse_args()

    failures: list[str] = []

    status, data = post_json(
        args.base_url,
        "/ai/input-interpret",
        {"mode": "input", "transcript": "내일 9시 회의", "selectedDate": "2026-02-11"},
    )
    if status != 200:
        failures.append(f"[input] expected 200, got {status}: {data}")
    else:
        failures.extend(expect_keys("input", data, ["mode", "summary", "time", "categoryId", "body", "missingRequired"]))
        if data.get("mode") != "input":
            failures.append(f"[input] mode mismatch: {data.get('mode')}")

    status, data = post_json(
        args.base_url,
        "/ai/search-interpret",
        {"mode": "search", "transcript": "지난주 병원 일정 찾아줘"},
    )
    if status != 200:
        failures.append(f"[search] expected 200, got {status}: {data}")
    else:
        failures.extend(expect_keys("search", data, ["mode", "query", "categoryIds"]))
        if data.get("mode") != "search":
            failures.append(f"[search] mode mismatch: {data.get('mode')}")

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
    )
    if status != 200:
        failures.append(f"[refine] expected 200, got {status}: {data}")
    else:
        failures.extend(expect_keys("refine", data, ["mode", "field", "value", "missingRequired"]))
        if data.get("mode") != "refine":
            failures.append(f"[refine] mode mismatch: {data.get('mode')}")

    if failures:
        print("AI backend contract check failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("AI backend contract check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
