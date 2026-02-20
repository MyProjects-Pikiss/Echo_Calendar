from __future__ import annotations

from typing import Any

from pydantic import ValidationError

from .models import InputInterpretResponse, RefineFieldResponse, SearchInterpretResponse
from .prompts import KNOWN_CATEGORY_IDS


class ContractValidationError(Exception):
    pass


def _trimmed_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def _normalize_string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def _normalize_time(value: str) -> str:
    text = value.strip()
    if not text:
        return ""
    if ":" not in text:
        return text
    hour, minute = text.split(":", 1)
    if hour.isdigit() and minute.isdigit():
        return f"{int(hour):02d}:{int(minute):02d}"
    return text


def ensure_input_response(raw: dict[str, Any], transcript: str, selected_date: str) -> InputInterpretResponse:
    candidate = dict(raw)
    candidate["mode"] = "input"
    candidate["date"] = _trimmed_text(candidate.get("date")) or selected_date
    candidate["summary"] = (_trimmed_text(candidate.get("summary")) or transcript[:25]).strip() or "일정"
    candidate["time"] = _normalize_time(_trimmed_text(candidate.get("time")))
    category_id = _trimmed_text(candidate.get("categoryId")) or "other"
    candidate["categoryId"] = category_id if category_id in KNOWN_CATEGORY_IDS else "other"
    candidate["placeText"] = _trimmed_text(candidate.get("placeText"))
    candidate["body"] = _trimmed_text(candidate.get("body")) or transcript
    candidate["labels"] = _normalize_string_list(candidate.get("labels"))
    candidate["missingRequired"] = _normalize_string_list(candidate.get("missingRequired"))
    candidate["intent"] = _trimmed_text(candidate.get("intent")).lower() or "create"
    try:
        return InputInterpretResponse.model_validate(candidate)
    except ValidationError as exc:
        raise ContractValidationError(str(exc)) from exc


def ensure_search_response(raw: dict[str, Any], transcript: str) -> SearchInterpretResponse:
    candidate = dict(raw)
    candidate["mode"] = "search"
    candidate["query"] = _trimmed_text(candidate.get("query")) or transcript
    candidate["dateFrom"] = _trimmed_text(candidate.get("dateFrom")) or None
    candidate["dateTo"] = _trimmed_text(candidate.get("dateTo")) or None
    candidate["categoryIds"] = _normalize_string_list(candidate.get("categoryIds"))
    candidate["categoryIds"] = [item for item in candidate["categoryIds"] if item in KNOWN_CATEGORY_IDS]
    try:
        return SearchInterpretResponse.model_validate(candidate)
    except ValidationError as exc:
        raise ContractValidationError(str(exc)) from exc


def ensure_refine_response(raw: dict[str, Any], field: str, current_value: str) -> RefineFieldResponse:
    candidate = dict(raw)
    candidate["mode"] = "refine"
    candidate["field"] = field
    candidate["value"] = _trimmed_text(candidate.get("value")) or current_value
    candidate["missingRequired"] = _normalize_string_list(candidate.get("missingRequired"))
    try:
        return RefineFieldResponse.model_validate(candidate)
    except ValidationError as exc:
        raise ContractValidationError(str(exc)) from exc
