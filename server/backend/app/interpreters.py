from __future__ import annotations

import re
from datetime import date, datetime, timedelta

from .models import (
    InputInterpretResponse,
    ModifyInterpretResponse,
    RefineFieldResponse,
    SearchStrategy,
    SearchInterpretResponse,
)

MAX_INFERRED_LABELS = 5
MAX_KEYWORD_INFERRED_LABELS = 3
LABEL_KEYWORDS: list[tuple[str, tuple[str, ...]]] = [
    ("병원", ("병원", "진료", "의원", "약국", "검사", "치과")),
    ("업무", ("회의", "미팅", "업무", "프로젝트", "회사", "출근", "보고")),
    ("금융", ("은행", "송금", "계좌", "카드", "결제", "청구서", "보험료")),
    ("지출", ("쇼핑", "구매", "결제", "지출", "영수증")),
    ("식사", ("점심", "저녁", "아침", "식사", "밥", "카페", "커피")),
    ("학습", ("공부", "강의", "수업", "시험", "과제", "독서")),
    ("약속", ("약속", "모임", "친구", "가족", "데이트", "회식")),
    ("취미", ("운동", "헬스", "러닝", "게임", "영화", "여행")),
    ("배달", ("배달", "택배", "배송", "도착")),
    ("기록", ("기록", "메모", "일기", "정리")),
]

SEARCH_CATEGORY_HINTS: list[tuple[str, tuple[str, ...]]] = [
    ("medical", ("병원", "진료", "의원", "치과", "약국")),
    ("work", ("업무", "회의", "미팅", "프로젝트", "회사")),
    ("dining", ("식사", "점심", "저녁", "카페", "커피")),
    ("delivery", ("택배", "배송", "배달")),
]


def _append_unique_label(target: list[str], candidate: str) -> None:
    normalized = candidate.strip().lstrip("#")
    if not normalized:
        return
    key = normalized.lower()
    exists = any(item.lower() == key for item in target)
    if not exists:
        target.append(normalized)


def _extract_explicit_labels(text: str) -> list[str]:
    labels: list[str] = []
    for matched in re.findall(r"#([\w\-가-힣]+)", text):
        _append_unique_label(labels, matched)
    inline_match = re.search(r"(?:라벨|태그)(?:은|:)?\s*([^\n]+)", text)
    if inline_match:
        for token in inline_match.group(1).split(","):
            _append_unique_label(labels, token)
    return labels


def infer_input_labels(transcript: str, summary: str) -> list[str]:
    labels: list[str] = _extract_explicit_labels(transcript)
    if labels:
        return labels[:MAX_INFERRED_LABELS]

    joined = f"{transcript} {summary}".lower()
    inferred_count = 0
    for label, keywords in LABEL_KEYWORDS:
        if any(keyword in joined for keyword in keywords):
            _append_unique_label(labels, label)
            inferred_count += 1
        if inferred_count >= MAX_KEYWORD_INFERRED_LABELS:
            break
        if len(labels) >= MAX_INFERRED_LABELS:
            break
    return labels[:MAX_INFERRED_LABELS]


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
    category_id = "other"
    return InputInterpretResponse(
        intent=_detect_intent(transcript),  # type: ignore[arg-type]
        date=_detect_date(transcript, selected_date),
        summary=summary or "일정",
        time=_normalize_time(transcript),
        repeatYearly=_detect_repeat_yearly(transcript),
        categoryId=category_id,
        placeText="",
        body=transcript.strip(),
        labels=infer_input_labels(transcript, summary or "일정"),
        missingRequired=[],
    )


def local_search_interpret(transcript: str) -> SearchInterpretResponse:
    wants_all_records = _contains_all_records_intent(transcript)
    labels = _extract_explicit_labels(transcript)
    category_source = re.sub(r"(?:라벨|태그)(?:은|:)?\s*[^\n]+", " ", transcript)
    category_source = re.sub(r"#([\w\-가-힣]+)", " ", category_source)
    category_ids = _extract_search_category_ids(category_source)
    inline_match = re.search(r"(?:라벨|태그)(?:은|:)?\s*([^\n]+)", transcript)
    if inline_match:
        for token in inline_match.group(1).split(","):
            normalized = token.strip()
            if normalized and normalized not in labels:
                labels.append(normalized)

    date_from, date_to = _extract_search_date_range(transcript)
    sort_order = _extract_search_sort_order(transcript)

    query = re.sub(r"(검색|찾아줘|찾기|보여줘)", "", transcript)
    query = re.sub(r"(?:여태까지|지금까지|전체|전부|모든)\s*(?:기록|일정)?", "", query)
    query = re.sub(r"\d{4}[./-]\d{1,2}[./-]\d{1,2}\s*(?:부터|에서)\s*\d{4}[./-]\d{1,2}[./-]\d{1,2}\s*까지", "", query)
    query = re.sub(r"\d{1,2}[./-]\d{1,2}\s*(?:부터|에서)\s*\d{1,2}[./-]\d{1,2}\s*까지", "", query)
    query = re.sub(r"\d{1,2}일\s*(?:부터|에서)\s*\d{1,2}일\s*까지", "", query)
    query = re.sub(r"(최신순|최근순|오래된순|예전순|오름차순|내림차순)(?:으로|로)?", "", query)
    if inline_match:
        query = query.replace(inline_match.group(0), "")
    query = re.sub(r"\s+", " ", query).strip()
    resolved_query = "*" if wants_all_records else query
    if not resolved_query and date_from is None and date_to is None and not labels:
        resolved_query = transcript.strip()
    strategy = _infer_search_strategy(
        forced_all=wants_all_records,
        query=resolved_query,
        date_from=date_from,
        date_to=date_to,
        category_ids=category_ids,
        labels=labels,
    )

    return SearchInterpretResponse(
        strategy=strategy,
        query=resolved_query,
        dateFrom=date_from,
        dateTo=date_to,
        sortOrder=sort_order,
        categoryIds=[] if strategy == "all_events" else category_ids,
        labels=[] if strategy == "all_events" else labels,
    )


def _extract_search_sort_order(text: str) -> str:
    lowered = text.lower()
    if any(token in lowered for token in ("오래된순", "예전순", "오름차순")):
        return "asc"
    return "desc"


def _extract_search_date_range(text: str) -> tuple[str | None, str | None]:
    today = date.today()
    if _contains_all_records_intent(text):
        return None, None

    explicit = re.search(
        r"(\d{4}[./-]\d{1,2}[./-]\d{1,2})\s*(?:부터|에서)\s*(\d{4}[./-]\d{1,2}[./-]\d{1,2})\s*까지",
        text,
    )
    if explicit:
        parsed = _parse_search_date_token(explicit.group(1), today), _parse_search_date_token(explicit.group(2), today)
        if parsed[0] and parsed[1]:
            return _normalize_date_range(parsed[0], parsed[1])

    month_day = re.search(r"(\d{1,2}[./-]\d{1,2})\s*(?:부터|에서)\s*(\d{1,2}[./-]\d{1,2})\s*까지", text)
    if month_day:
        parsed = _parse_search_date_token(month_day.group(1), today), _parse_search_date_token(month_day.group(2), today)
        if parsed[0] and parsed[1]:
            return _normalize_date_range(parsed[0], parsed[1])

    day_only = re.search(r"(\d{1,2})일\s*(?:부터|에서)\s*(\d{1,2})일\s*까지", text)
    if day_only:
        start_day = int(day_only.group(1))
        end_day = int(day_only.group(2))
        if 1 <= start_day <= 31 and 1 <= end_day <= 31:
            start = date(today.year, today.month, min(start_day, _days_in_month(today.year, today.month)))
            end = date(today.year, today.month, min(end_day, _days_in_month(today.year, today.month)))
            return _normalize_date_range(start, end)
    return None, None


def _parse_search_date_token(token: str, today: date) -> date | None:
    normalized = token.strip().replace(".", "-").replace("/", "-")
    parts = [part for part in normalized.split("-") if part]
    if len(parts) == 3 and all(part.isdigit() for part in parts):
        year, month, day = map(int, parts)
        try:
            return date(year, month, day)
        except ValueError:
            return None
    if len(parts) == 2 and all(part.isdigit() for part in parts):
        month, day = map(int, parts)
        try:
            return date(today.year, month, day)
        except ValueError:
            return None
    return None


def _normalize_date_range(start: date, end: date) -> tuple[str, str]:
    if start <= end:
        return start.isoformat(), end.isoformat()
    return end.isoformat(), start.isoformat()


def _days_in_month(year: int, month: int) -> int:
    if month == 12:
        return 31
    return (date(year, month + 1, 1) - timedelta(days=1)).day


def _contains_all_records_intent(text: str) -> bool:
    compact = re.sub(r"\s+", "", text)
    has_all_token = any(token in compact for token in ("여태까지", "지금까지", "전체", "전부", "모든"))
    has_record_token = any(token in compact for token in ("기록", "일정", "이벤트"))
    return has_all_token and has_record_token


def _extract_search_category_ids(text: str) -> list[str]:
    lowered = text.lower()
    out: list[str] = []
    for category_id, keywords in SEARCH_CATEGORY_HINTS:
        if category_id in lowered or any(keyword in text for keyword in keywords):
            if category_id not in out:
                out.append(category_id)
    return out


def _infer_search_strategy(
    *,
    forced_all: bool,
    query: str,
    date_from: str | None,
    date_to: str | None,
    category_ids: list[str],
    labels: list[str],
) -> SearchStrategy:
    if forced_all:
        return "all_events"
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


def local_refine_field(field: str, transcript: str, current_value: str) -> RefineFieldResponse:
    value = transcript.strip()
    if field == "time":
        value = _normalize_time(transcript) or current_value
    return RefineFieldResponse(
        field=field,  # type: ignore[arg-type]
        value=value,
        missingRequired=[],
    )


def _detect_repeat_yearly(text: str) -> bool | None:
    lowered = text.lower()
    tokens = ["생일", "매년", "해마다", "매 해", "birthday", "anniversary"]
    return True if any(token in lowered for token in tokens) else None


def local_modify_interpret(
    transcript: str,
    current_summary: str,
    current_time: str,
    current_category_id: str,
    current_place_text: str,
    current_body: str,
    current_labels: list[str],
) -> ModifyInterpretResponse:
    lowered = transcript.lower()
    wants_summary = any(token in lowered for token in ["제목", "이름", "명칭"])
    wants_time = "시간" in lowered or "시각" in lowered or bool(_normalize_time(transcript))
    wants_category = "카테고리" in lowered or "분류" in lowered
    wants_place = any(token in lowered for token in ["장소", "위치", "어디"])
    wants_body = any(token in lowered for token in ["내용", "메모", "설명", "본문", "상세"])
    wants_labels = "라벨" in lowered or "태그" in lowered or "#" in transcript

    summary = None
    if wants_summary:
        summary = transcript.strip()
        summary = re.sub(r"(제목|이름|명칭)(을|은|는|:)?", "", summary).strip()
        summary = re.sub(r"(수정|변경|바꿔|고쳐)(해줘|줘)?", "", summary).strip()
        if not summary:
            summary = current_summary
        summary = summary[:40]

    time = _normalize_time(transcript) if wants_time else ""
    time = time or None

    category_id = None
    if wants_category:
        normalized = transcript.lower()
        if "medical" in normalized or "병원" in transcript:
            category_id = "medical"
        elif "work" in normalized or "업무" in transcript:
            category_id = "work"
        elif "other" in normalized:
            category_id = "other"
        else:
            category_id = current_category_id or None

    place_text = None
    if wants_place:
        place_match = re.search(r"(?:장소|위치)(?:는|:)?\s*([^,\n]+)", transcript)
        place_text = place_match.group(1).strip() if place_match else current_place_text
        place_text = place_text or None

    body = None
    if wants_body:
        body_match = re.search(r"(?:내용|메모|설명|본문|상세)(?:은|는|:)?\s*(.+)", transcript)
        body = body_match.group(1).strip() if body_match else transcript.strip()
        body = body or current_body or None

    labels = None
    if wants_labels:
        labels = []
        for matched in re.findall(r"#([\w\-가-힣]+)", transcript):
            token = matched.strip()
            if token and token not in labels:
                labels.append(token)
        inline_match = re.search(r"(?:라벨|태그)(?:은|:)?\s*([^\n]+)", transcript)
        if inline_match:
            for token in inline_match.group(1).split(","):
                normalized = token.strip()
                if normalized and normalized not in labels:
                    labels.append(normalized)
        if not labels:
            labels = current_labels

    return ModifyInterpretResponse(
        summary=summary,
        time=time,
        categoryId=category_id,
        placeText=place_text,
        body=body,
        labels=labels,
        missingRequired=[],
    )
