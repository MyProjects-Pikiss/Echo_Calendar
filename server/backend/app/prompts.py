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
        "labels are optional search-assist tags, return 0..5 items. "
        "Prefer concise reusable labels; avoid creating too many specific one-off labels. "
        "Example input: '내일 9시 회의'. "
        "Example output: {'mode':'input','intent':'create','date':'2026-02-11','summary':'회의','time':'09:00','repeatYearly':null,"
        "'categoryId':'work','placeText':'','body':'내일 9시 회의','labels':['팀'],'missingRequired':[]}."
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
        "Required keys: mode,query,dateFrom,dateTo,categoryIds,labels. "
        "mode must be 'search'. "
        "query may be empty only when at least one filter exists "
        "(dateFrom/dateTo/categoryIds/labels). "
        "dateFrom/dateTo should be YYYY-MM-DD or null. "
        "Example input: '지난주 병원 일정 찾아줘'. "
        "Example output: {'mode':'search','query':'병원 일정','dateFrom':'2026-02-01','dateTo':'2026-02-07',"
        "'categoryIds':['medical'],'labels':[]}."
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
        "Example output: {'mode':'refine','field':'time','value':'15:30','missingRequired':[]}."
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
        "1) transcript='내일 회의 3시 30분으로 바꿔줘' -> {'mode':'modify','summary':null,'time':'15:30','categoryId':'work','placeText':null,'body':null,'labels':null,'missingRequired':[]} "
        "2) transcript='장소를 강남역으로, 라벨은 #중요,#팀 으로 수정' -> {'mode':'modify','summary':null,'time':null,'categoryId':null,'placeText':'강남역','body':null,'labels':['중요','팀'],'missingRequired':[]} "
        "3) transcript='내용 지워줘' -> {'mode':'modify','summary':null,'time':null,'categoryId':null,'placeText':null,'body':'','labels':null,'missingRequired':[]}. "
        "4) transcript='병원 진료로 바꿔줘' -> {'mode':'modify','summary':null,'time':null,'categoryId':'medical','placeText':null,'body':null,'labels':null,'missingRequired':[]}."
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
