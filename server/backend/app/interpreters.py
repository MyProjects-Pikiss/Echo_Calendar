from __future__ import annotations

import re
from datetime import date, datetime, timedelta

from .models import (
    InputInterpretResponse,
    ModifyInterpretResponse,
    RefineFieldResponse,
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
    labels = _extract_explicit_labels(transcript)
    inline_match = re.search(r"(?:라벨|태그)(?:은|:)?\s*([^\n]+)", transcript)
    if inline_match:
        for token in inline_match.group(1).split(","):
            normalized = token.strip()
            if normalized and normalized not in labels:
                labels.append(normalized)

    query = re.sub(r"(검색|찾아줘|찾기|보여줘)", "", transcript).strip()
    if inline_match:
        query = query.replace(inline_match.group(0), "").strip()
    return SearchInterpretResponse(
        query=query or transcript.strip(),
        categoryIds=[],
        labels=labels,
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
