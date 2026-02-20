from __future__ import annotations

import re
from datetime import date, datetime, timedelta

from .models import (
    InputInterpretResponse,
    RefineFieldResponse,
    SearchInterpretResponse,
)


def _normalize_time(text: str) -> str:
    match = re.search(r"(\d{1,2})\s*시(?:\s*(\d{1,2})\s*분?)?", text)
    if not match:
        match = re.search(r"\b(\d{1,2}):(\d{2})\b", text)
    if not match:
        return ""
    hour = int(match.group(1))
    minute = int(match.group(2) or 0)
    return f"{hour:02d}:{minute:02d}"


def _detect_intent(text: str) -> str:
    lowered = text.lower()
    if any(token in lowered for token in ["삭제", "지워", "취소"]):
        return "delete"
    if any(token in lowered for token in ["수정", "변경", "바꿔"]):
        return "update"
    return "create"


def _detect_date(text: str, selected_date: str) -> str:
    try:
        base = date.fromisoformat(selected_date)
    except ValueError:
        return selected_date
    if "내일" in text:
        return (base + timedelta(days=1)).isoformat()
    if "모레" in text:
        return (base + timedelta(days=2)).isoformat()
    if "오늘" in text:
        return base.isoformat()
    return base.isoformat()


def local_input_interpret(transcript: str, selected_date: str) -> InputInterpretResponse:
    summary = transcript.strip()
    summary = re.sub(r"(추가해줘|추가|기록해줘|기록|일정|검색해줘|찾아줘)$", "", summary).strip()
    if len(summary) > 25:
        summary = summary[:25]
    return InputInterpretResponse(
        intent=_detect_intent(transcript),  # type: ignore[arg-type]
        date=_detect_date(transcript, selected_date),
        summary=summary or "일정",
        time=_normalize_time(transcript),
        categoryId="other",
        placeText="",
        body=transcript.strip(),
        labels=[],
        missingRequired=[],
    )


def local_search_interpret(transcript: str) -> SearchInterpretResponse:
    query = re.sub(r"(검색|찾아줘|찾기|보여줘)", "", transcript).strip()
    return SearchInterpretResponse(
        query=query or transcript.strip(),
        categoryIds=[],
    )


def local_refine_field(field: str, transcript: str, current_value: str) -> RefineFieldResponse:
    value = transcript.strip()
    if field == "time":
        value = _normalize_time(transcript) or current_value
    return RefineFieldResponse(
        field=field,  # type: ignore[arg-type]
        value=value,
        missingRequired=[],
    )
