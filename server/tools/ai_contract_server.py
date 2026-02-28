#!/usr/bin/env python3
"""Lightweight local AI contract server for Echo Calendar E2E wiring tests.

This is a deterministic contract stub, not a production model server.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


@dataclass(frozen=True)
class ErrorPayload:
    mode: str
    errorCode: str
    message: str

    def as_dict(self) -> dict[str, str]:
        return {
            "mode": self.mode,
            "errorCode": self.errorCode,
            "message": self.message,
        }


class Handler(BaseHTTPRequestHandler):
    server_version = "EchoAiContractStub/1.0"

    def do_POST(self) -> None:  # noqa: N802
        raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        payload = self._load_json(raw)
        if payload is None:
            return self._json_response(400, ErrorPayload("unknown", "INVALID_JSON", "Malformed JSON").as_dict())

        if self.path == "/ai/input-interpret":
            self._handle_input(payload)
            return
        if self.path == "/ai/search-interpret":
            self._handle_search(payload)
            return
        if self.path == "/ai/refine-field":
            self._handle_refine(payload)
            return

        self._json_response(404, ErrorPayload("unknown", "NOT_FOUND", f"Unsupported path: {self.path}").as_dict())

    def _handle_input(self, payload: dict[str, Any]) -> None:
        mode = str(payload.get("mode", ""))
        transcript = str(payload.get("transcript", "")).strip()
        selected_date = str(payload.get("selectedDate", "")).strip()

        if mode != "input":
            return self._json_response(422, ErrorPayload("input", "MODE_MISMATCH", "mode must be 'input'").as_dict())
        if not transcript:
            return self._json_response(422, ErrorPayload("input", "MISSING_TRANSCRIPT", "transcript is required").as_dict())
        if not _is_iso_date(selected_date):
            return self._json_response(422, ErrorPayload("input", "INVALID_DATE", "selectedDate must be yyyy-MM-dd").as_dict())

        time = _extract_time(transcript) or ""
        summary = transcript.replace("일정", "").replace("추가", "").strip()[:40]
        missing_required = []
        if not summary:
            missing_required.append("summary")
        if not time:
            missing_required.append("time")

        self._json_response(
            200,
            {
                "mode": "input",
                "date": selected_date,
                "summary": summary or "",
                "time": time,
                "categoryId": "other",
                "placeText": "",
                "body": transcript,
                "labels": [],
                "missingRequired": missing_required,
            },
        )

    def _handle_search(self, payload: dict[str, Any]) -> None:
        mode = str(payload.get("mode", ""))
        transcript = str(payload.get("transcript", "")).strip()

        if mode != "search":
            return self._json_response(422, ErrorPayload("search", "MODE_MISMATCH", "mode must be 'search'").as_dict())
        if not transcript:
            return self._json_response(422, ErrorPayload("search", "MISSING_TRANSCRIPT", "transcript is required").as_dict())

        labels = []
        for matched in _extract_hashtags(transcript):
            if matched not in labels:
                labels.append(matched)

        today = date.today()
        date_from = None
        date_to = None
        if "지난주" in transcript:
            end = today - timedelta(days=today.weekday() + 1)
            start = end - timedelta(days=6)
            date_from, date_to = start.isoformat(), end.isoformat()

        self._json_response(
            200,
            {
                "mode": "search",
                "query": transcript.replace("찾아줘", "").replace("검색", "").strip(),
                "dateFrom": date_from,
                "dateTo": date_to,
                "categoryIds": ["medical"] if "병원" in transcript else [],
                "labels": labels,
            },
        )

    def _handle_refine(self, payload: dict[str, Any]) -> None:
        mode = str(payload.get("mode", ""))
        transcript = str(payload.get("transcript", "")).strip()
        field = str(payload.get("field", "")).strip()
        current_value = str(payload.get("currentValue", "")).strip()

        if mode != "refine":
            return self._json_response(422, ErrorPayload("refine", "MODE_MISMATCH", "mode must be 'refine'").as_dict())
        if not transcript:
            return self._json_response(422, ErrorPayload("refine", "MISSING_TRANSCRIPT", "transcript is required").as_dict())
        if field not in {"summary", "time", "category", "place", "labels", "body"}:
            return self._json_response(422, ErrorPayload("refine", "INVALID_FIELD", "field is invalid").as_dict())

        refined = current_value
        if field == "time":
            refined = _extract_time(transcript) or current_value
        elif field in {"summary", "place", "body"}:
            refined = transcript
        elif field == "labels":
            refined = transcript.replace(" ", ",")
        elif field == "category" and transcript:
            refined = transcript

        self._json_response(
            200,
            {
                "mode": "refine",
                "field": field,
                "value": refined,
                "missingRequired": ["time"] if field == "time" and not refined else [],
            },
        )

    def _load_json(self, payload: bytes) -> dict[str, Any] | None:
        if not payload:
            return None
        try:
            loaded = json.loads(payload.decode("utf-8"))
        except json.JSONDecodeError:
            return None
        return loaded if isinstance(loaded, dict) else None

    def _json_response(self, status: int, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A003
        return


def _is_iso_date(value: str) -> bool:
    try:
        date.fromisoformat(value)
        return True
    except ValueError:
        return False


def _extract_time(transcript: str) -> str | None:
    for token in transcript.replace("시", ":").replace("분", "").split():
        try:
            parsed = datetime.strptime(token.strip(), "%H:%M")
        except ValueError:
            continue
        return parsed.strftime("%H:%M")
    return None


def _extract_hashtags(transcript: str) -> list[str]:
    return [match[1:] for match in transcript.split() if match.startswith("#") and len(match) > 1]


def main() -> None:
    host = "0.0.0.0"
    port = 8088
    server = ThreadingHTTPServer((host, port), Handler)
    print(f"Echo AI contract stub listening on http://{host}:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
