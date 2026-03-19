from __future__ import annotations

import json


KNOWN_CATEGORY_IDS = [
    "medical",
    "finance",
    "spending",
    "administration",
    "work",
    "learning",
    "relationships",
    "gathering-event",
    "life",
    "repair-maintenance",
    "delivery",
    "hobby-leisure",
    "dining",
    "record",
    "other",
]


def input_prompts(transcript: str, selected_date: str) -> tuple[str, str]:
    system = (
        "You convert Korean calendar input into strict JSON for Echo Calendar. "
        "Return JSON object only, no markdown, no extra text. "
        "Required keys: mode,intent,date,summary,time,repeatYearly,categoryId,placeText,body,labels,missingRequired. "
        "mode must be 'input'. "
        "intent must be one of create|update|delete. "
        "date format: YYYY-MM-DD. time format: HH:mm or empty string. "
        "repeatYearly must be true/false/null. Use true for birthday or yearly recurring intent. "
        f"categoryId should be one of: {', '.join(KNOWN_CATEGORY_IDS)}. "
        "If uncertain, use categoryId='other'. "
        "summary must be a natural event title in Korean, not an over-compressed keyword. "
        "Keep the event identifiable at a glance. Preserve important participants, counterparties, or distinguishing context when they are central to the request. "
        "Do not collapse summary to a generic activity noun like '식사', '회의', '약속', '방문' unless the transcript itself is that generic. "
        "When date or time can be structured into the date/time fields, do not duplicate that information in summary. "
        "Remove standalone date/time expressions such as '내일', '다음 주', '3시', '15:30' from summary unless the user explicitly states them as part of the title itself. "
        "Remove only request boilerplate such as '기록해줘', '추가해줘', '등록해줘'. "
        "labels are optional search-assist tags, return 0..5 items. "
        "Prefer concise reusable labels; avoid creating too many specific one-off labels. "
        "Do not invent labels that are not grounded in the transcript. "
        "If the transcript does not clearly supply label text, return an empty labels array. "
        "Example input: '내일 9시 회의'. "
        'Example output: {"mode":"input","intent":"create","date":"2026-02-11","summary":"회의","time":"09:00","repeatYearly":null,'
        '"categoryId":"work","placeText":"","body":"내일 9시 회의","labels":[],"missingRequired":[]}.'
    )
    user = json.dumps(
        {
            "mode": "input",
            "transcript": transcript,
            "selectedDate": selected_date,
        },
        ensure_ascii=False,
    )
    return system, user


def search_prompts(transcript: str) -> tuple[str, str]:
    system = (
        "You convert Korean search requests into strict JSON for Echo Calendar. "
        "Return JSON object only. "
        "Required keys: mode,strategy,query,dateFrom,dateTo,sortOrder,categoryIds,labels. "
        "mode must be 'search'. "
        "strategy must be one of combined|all_events|date_range|category|label|keyword. "
        "query may be empty only when at least one filter exists "
        "(dateFrom/dateTo/categoryIds/labels). "
        "dateFrom/dateTo should be YYYY-MM-DD or null. "
        "sortOrder must be 'desc' (latest first) or 'asc' (oldest first). "
        "Default sortOrder is 'desc' unless user explicitly asks oldest/ascending order. "
        "Strategy rules: "
        "all_events means return query='*', keep dateFrom/dateTo null, and keep categoryIds/labels empty. "
        "date_range means use dateFrom/dateTo. "
        "category means use categoryIds. "
        "label means use labels. "
        "keyword means use query text. "
        "combined means two or more mechanisms are used together. "
        "For '여태까지/지금까지/전체/전부/모든 기록' style requests, choose strategy='all_events' so all saved events are included. "
        "For 'n일부터 m일까지' or explicit date range requests, fill dateFrom/dateTo exactly. "
        "Example input: '지난주 병원 일정 찾아줘'. "
        'Example output: {"mode":"search","strategy":"combined","query":"병원 일정","dateFrom":"2026-02-01","dateTo":"2026-02-07","sortOrder":"desc",'
        '"categoryIds":["medical"],"labels":[]}.'
    )
    user = json.dumps(
        {
            "mode": "search",
            "transcript": transcript,
        },
        ensure_ascii=False,
    )
    return system, user


def refine_prompts(field: str, transcript: str, current_value: str, selected_date: str) -> tuple[str, str]:
    system = (
        "You refine one field for Echo Calendar edit form. "
        "Return JSON object only. "
        "Required keys: mode,field,value,missingRequired. "
        "mode must be 'refine'. "
        "field must be exactly the requested field. "
        "value must be non-empty. "
        "For time field, output HH:mm. "
        "Example input: field='time', transcript='3시 30분으로 바꿔줘'. "
        'Example output: {"mode":"refine","field":"time","value":"15:30","missingRequired":[]}.'
    )
    user = json.dumps(
        {
            "mode": "refine",
            "field": field,
            "transcript": transcript,
            "currentValue": current_value,
            "selectedDate": selected_date,
        },
        ensure_ascii=False,
    )
    return system, user


def modify_prompts(
    transcript: str,
    selected_date: str,
    current_summary: str,
    current_time: str,
    current_category_id: str,
    current_place_text: str,
    current_body: str,
    current_labels: list[str],
) -> tuple[str, str]:
    system = (
        "You convert a Korean event-modification request into one JSON patch for Echo Calendar. "
        "Return JSON object only, with no markdown and no extra text. "
        "Required keys: mode,summary,time,categoryId,placeText,body,labels,missingRequired. "
        "mode must be 'modify'. "
        "Goal: in one response, fill fields that are clearly requested, and use only bounded grounded inference. "
        "Never modify unrelated fields. If evidence is weak, return null for that field. "
        "Return null for unchanged fields. "
        "If user asks to clear a field, return empty string for text fields and [] for labels. "
        "High-priority clear rules: "
        "if transcript says '내용 지워/삭제/없애/비워' then body must be ''. "
        "if transcript says '제목 지워/삭제/없애/비워' then summary must be ''. "
        "if transcript says '장소 지워/삭제/없애/비워' then placeText must be ''. "
        "if transcript says '라벨 지워/삭제/없애/비워' then labels must be []. "
        "time must be HH:mm when provided, otherwise null. "
        f"categoryId must be one of: {', '.join(KNOWN_CATEGORY_IDS)} or null. "
        "labels must be an array or null. "
        "For labels, include concise normalized tags without '#'. "
        "When selecting categoryId, infer from meaning (e.g., 병원/진료->medical, 회의/업무->work, 식사/밥->dining) if reasonable. "
        "Inference boundary: category inference is allowed without explicit keyword, "
        "but do NOT infer summary/place/body/labels unless the transcript explicitly asks for those fields. "
        "Do not copy all current values; output only fields that should actually change. "
        "If transcript requests multiple changes, include all supported changes in one patch. "
        "Examples: "
        '1) transcript="내일 회의 3시 30분으로 바꿔줘" -> {"mode":"modify","summary":null,"time":"15:30","categoryId":"work","placeText":null,"body":null,"labels":null,"missingRequired":[]} '
        '2) transcript="장소를 강남역으로, 라벨은 #중요,#팀 으로 수정" -> {"mode":"modify","summary":null,"time":null,"categoryId":null,"placeText":"강남역","body":null,"labels":["중요","팀"],"missingRequired":[]} '
        '3) transcript="내용 지워줘" -> {"mode":"modify","summary":null,"time":null,"categoryId":null,"placeText":null,"body":"","labels":null,"missingRequired":[]}. '
        '4) transcript="병원 진료로 바꿔줘" -> {"mode":"modify","summary":null,"time":null,"categoryId":"medical","placeText":null,"body":null,"labels":null,"missingRequired":[]}.'
    )
    user = json.dumps(
        {
            "mode": "modify",
            "transcript": transcript,
            "selectedDate": selected_date,
            "currentSummary": current_summary,
            "currentTime": current_time,
            "currentCategoryId": current_category_id,
            "currentPlaceText": current_place_text,
            "currentBody": current_body,
            "currentLabels": current_labels,
        },
        ensure_ascii=False,
    )
    return system, user
