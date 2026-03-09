from __future__ import annotations

import re
from typing import Any

from pydantic import ValidationError

from .models import (
    InputInterpretResponse,
    ModifyInterpretResponse,
    RefineFieldResponse,
    SearchInterpretResponse,
)
from .prompts import KNOWN_CATEGORY_IDS


class ContractValidationError(Exception):
    pass


VALID_SEARCH_STRATEGIES = {"combined", "all_events", "date_range", "category", "label", "keyword"}
INPUT_CATEGORY_EXPLICIT_HINTS: dict[str, tuple[str, ...]] = {
    "medical": ("의료", "병원", "진료", "치과", "약국", "medical"),
    "finance": ("금융", "은행", "송금", "계좌", "finance"),
    "spending": ("소비", "쇼핑", "구매", "지출", "spending"),
    "administration": ("행정", "민원", "서류", "administration"),
    "work": ("업무", "회의", "회사", "work"),
    "learning": ("학습", "공부", "수업", "learning"),
    "relationships": ("개인 관계", "가족", "친구", "relationships"),
    "gathering-event": ("모임", "행사", "gathering-event"),
    "life": ("생활", "일상", "생활비", "life"),
    "repair-maintenance": ("수리", "정비", "점검", "repair-maintenance"),
    "delivery": ("배송", "택배", "배달", "delivery"),
    "hobby-leisure": ("취미", "여가", "운동", "hobby-leisure"),
    "dining": ("외식", "식사", "카페", "dining"),
    "record": ("기록", "메모", "일기", "record"),
    "other": ("기타", "other"),
}
INPUT_CATEGORY_SCORE_RULES: dict[str, tuple[tuple[str, int], ...]] = {
    "delivery": (("택배", 4), ("배송", 4), ("수령", 3), ("도착", 3), ("배달", 2)),
    "spending": (("이마트", 5), ("마트", 4), ("장보기", 4), ("쇼핑", 4), ("구매", 3), ("결제", 3), ("지출", 3), ("방문", 1)),
    "dining": (("식사", 4), ("밥", 3), ("점심", 3), ("저녁", 3), ("카페", 2), ("커피", 2)),
    "work": (("업무", 4), ("회의", 4), ("미팅", 3), ("회사", 3), ("출근", 3)),
    "medical": (("병원", 4), ("진료", 4), ("검진", 3), ("치과", 3), ("약국", 2)),
    "life": (("생활", 3), ("집안", 2), ("일상", 2)),
}


def _trimmed_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def _normalize_string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def _limit_labels(labels: list[str], max_count: int = 5) -> list[str]:
    if max_count <= 0:
        return []
    out: list[str] = []
    for raw in labels:
        token = raw.strip().lstrip("#")
        if not token:
            continue
        key = token.lower()
        if any(existing.lower() == key for existing in out):
            continue
        out.append(token)
        if len(out) >= max_count:
            break
    return out


def _normalize_time(value: str) -> str:
    text = value.strip()
    if not text:
        return ""
    if ":" not in text:
        return text
    hour, minute = text.split(":", 1)
    if hour.isdigit() and minute.isdigit():
        hour_num = int(hour)
        minute_num = int(minute)
        if 0 <= hour_num <= 23 and 0 <= minute_num <= 59:
            return f"{hour_num:02d}:{minute_num:02d}"
        return ""
    return text


def ensure_input_response(raw: dict[str, Any], transcript: str, selected_date: str) -> InputInterpretResponse:
    candidate = dict(raw)
    candidate["mode"] = "input"
    candidate["date"] = _trimmed_text(candidate.get("date")) or selected_date
    candidate["summary"] = (_trimmed_text(candidate.get("summary")) or transcript[:25]).strip() or "일정"
    candidate["time"] = _normalize_time(_trimmed_text(candidate.get("time")))
    repeat_yearly_raw = candidate.get("repeatYearly")
    candidate["repeatYearly"] = repeat_yearly_raw if isinstance(repeat_yearly_raw, bool) else None
    category_id = _trimmed_text(candidate.get("categoryId")) or "other"
    candidate["categoryId"] = _resolve_input_category_id(
        transcript=transcript,
        summary=candidate["summary"],
        body=_trimmed_text(candidate.get("body")) or transcript,
        model_category_id=category_id,
    )
    candidate["placeText"] = _trimmed_text(candidate.get("placeText"))
    candidate["body"] = _trimmed_text(candidate.get("body")) or transcript
    candidate["labels"] = _limit_labels(_normalize_string_list(candidate.get("labels")))
    candidate["missingRequired"] = _normalize_string_list(candidate.get("missingRequired"))
    candidate["intent"] = _trimmed_text(candidate.get("intent")).lower() or "create"
    try:
        return InputInterpretResponse.model_validate(candidate)
    except ValidationError as exc:
        raise ContractValidationError(str(exc)) from exc


def ensure_search_response(raw: dict[str, Any], transcript: str) -> SearchInterpretResponse:
    candidate = dict(raw)
    candidate["mode"] = "search"
    strategy = _trimmed_text(candidate.get("strategy")).lower()
    candidate["strategy"] = strategy if strategy in VALID_SEARCH_STRATEGIES else "combined"
    candidate["query"] = _trimmed_text(candidate.get("query"))
    candidate["dateFrom"] = _trimmed_text(candidate.get("dateFrom")) or None
    candidate["dateTo"] = _trimmed_text(candidate.get("dateTo")) or None
    sort_order = _trimmed_text(candidate.get("sortOrder")).lower()
    candidate["sortOrder"] = "asc" if sort_order == "asc" else "desc"
    candidate["categoryIds"] = _normalize_string_list(candidate.get("categoryIds"))
    candidate["categoryIds"] = [item for item in candidate["categoryIds"] if item in KNOWN_CATEGORY_IDS]
    candidate["labels"] = _limit_labels(_normalize_string_list(candidate.get("labels")))
    if (
        not candidate["query"]
        and not candidate["dateFrom"]
        and not candidate["dateTo"]
        and not candidate["categoryIds"]
        and not candidate["labels"]
    ):
        if _contains_all_records_intent(transcript):
            candidate["strategy"] = "all_events"
            candidate["query"] = "*"
        else:
            candidate["query"] = transcript.strip()
            if candidate["strategy"] == "combined":
                candidate["strategy"] = "keyword"

    if candidate["strategy"] == "all_events":
        candidate["query"] = "*"
        candidate["dateFrom"] = None
        candidate["dateTo"] = None
        candidate["categoryIds"] = []
        candidate["labels"] = []
    elif candidate["query"] == "*" and not candidate["dateFrom"] and not candidate["dateTo"] and not candidate["categoryIds"] and not candidate["labels"]:
        candidate["strategy"] = "all_events"

    if candidate["strategy"] == "combined":
        candidate["strategy"] = _infer_strategy_from_fields(
            query=candidate["query"],
            date_from=candidate["dateFrom"],
            date_to=candidate["dateTo"],
            category_ids=candidate["categoryIds"],
            labels=candidate["labels"],
        )
    try:
        return SearchInterpretResponse.model_validate(candidate)
    except ValidationError as exc:
        raise ContractValidationError(str(exc)) from exc


def ensure_refine_response(raw: dict[str, Any], field: str, current_value: str) -> RefineFieldResponse:
    candidate = dict(raw)
    candidate["mode"] = "refine"
    candidate["field"] = field
    value = _trimmed_text(candidate.get("value")) or current_value
    if field == "time":
        value = _normalize_time(value)
        if not value:
            value = _normalize_time(current_value)
    candidate["value"] = value
    candidate["missingRequired"] = _normalize_string_list(candidate.get("missingRequired"))
    try:
        return RefineFieldResponse.model_validate(candidate)
    except ValidationError as exc:
        raise ContractValidationError(str(exc)) from exc


def ensure_modify_response(raw: dict[str, Any], transcript: str) -> ModifyInterpretResponse:
    candidate = dict(raw)
    candidate["mode"] = "modify"

    summary = _trimmed_text(candidate.get("summary"))
    candidate["summary"] = summary or None

    time = _normalize_time(_trimmed_text(candidate.get("time")))
    candidate["time"] = time or None

    category_id = _trimmed_text(candidate.get("categoryId"))
    candidate["categoryId"] = category_id if category_id in KNOWN_CATEGORY_IDS else None

    place_text = _trimmed_text(candidate.get("placeText"))
    candidate["placeText"] = place_text or None

    body = _trimmed_text(candidate.get("body"))
    candidate["body"] = body or None

    labels_raw = candidate.get("labels")
    labels = _limit_labels(_normalize_string_list(labels_raw))
    if labels_raw is None:
        candidate["labels"] = None
    else:
        candidate["labels"] = labels
    candidate["missingRequired"] = _normalize_string_list(candidate.get("missingRequired"))

    try:
        return ModifyInterpretResponse.model_validate(candidate)
    except ValidationError as exc:
        raise ContractValidationError(str(exc)) from exc


def _contains_all_records_intent(text: str) -> bool:
    compact = re.sub(r"\s+", "", str(text))
    has_all_token = any(token in compact for token in ("여태까지", "지금까지", "전체", "전부", "모든"))
    has_record_token = any(token in compact for token in ("기록", "일정", "이벤트"))
    return has_all_token and has_record_token


def _resolve_input_category_id(
    *,
    transcript: str,
    summary: str,
    body: str,
    model_category_id: str,
) -> str:
    text = f"{transcript} {summary} {body}".strip()
    explicit = _extract_explicit_input_category(text)
    if explicit is not None:
        return explicit

    scored = _infer_scored_input_category(text)
    if scored is not None:
        return scored

    normalized = model_category_id.strip().lower()
    return normalized if normalized in KNOWN_CATEGORY_IDS else "other"


def _extract_explicit_input_category(text: str) -> str | None:
    lowered = text.lower()
    for category_id, hints in INPUT_CATEGORY_EXPLICIT_HINTS.items():
        if any(hint.lower() in lowered for hint in hints):
            if category_id in KNOWN_CATEGORY_IDS:
                return category_id
    return None


def _infer_scored_input_category(text: str) -> str | None:
    lowered = text.lower()
    scores: dict[str, int] = {}
    for category_id, rules in INPUT_CATEGORY_SCORE_RULES.items():
        score = 0
        for keyword, weight in rules:
            if keyword.lower() in lowered:
                score += weight
        scores[category_id] = score

    # "배달일 끝나고 ... 이마트 방문" 류 오분류 방지: delivery 과대평가 감쇠
    if "배달일" in lowered or "배달 일" in lowered:
        scores["delivery"] = max(0, scores.get("delivery", 0) - 3)
    if any(token in lowered for token in ("끝나고", "끝난", "후", "다음")) and scores.get("spending", 0) > 0:
        scores["delivery"] = max(0, scores.get("delivery", 0) - 2)

    ranked = sorted(scores.items(), key=lambda item: item[1], reverse=True)
    if not ranked:
        return None
    best_id, best_score = ranked[0]
    second_score = ranked[1][1] if len(ranked) > 1 else 0
    if best_score < 3:
        return None
    if best_score - second_score < 1:
        return None
    return best_id if best_id in KNOWN_CATEGORY_IDS else None


def _infer_strategy_from_fields(
    *,
    query: str,
    date_from: str | None,
    date_to: str | None,
    category_ids: list[str],
    labels: list[str],
) -> str:
    has_date = bool(date_from or date_to)
    has_category = bool(category_ids)
    has_label = bool(labels)
    has_query = bool(query and query != "*" and not _is_generic_search_query(query))
    active = [has_date, has_category, has_label, has_query]
    if sum(1 for item in active if item) >= 2:
        return "combined"
    if has_date:
        return "date_range"
    if has_category:
        return "category"
    if has_label:
        return "label"
    if has_query:
        return "keyword"
    return "combined"


def _is_generic_search_query(query: str) -> bool:
    return query.strip() in {"기록", "일정", "이벤트", "기록들"}
