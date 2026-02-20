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
        "Required keys: mode,intent,date,summary,time,categoryId,placeText,body,labels,missingRequired. "
        "mode must be 'input'. "
        "intent must be one of create|update|delete. "
        "date format: YYYY-MM-DD. time format: HH:mm or empty string. "
        f"categoryId should be one of: {', '.join(KNOWN_CATEGORY_IDS)}. "
        "If uncertain, use categoryId='other'. "
        "Example input: '내일 9시 회의'. "
        "Example output: {'mode':'input','intent':'create','date':'2026-02-11','summary':'회의','time':'09:00',"
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
        "Required keys: mode,query,dateFrom,dateTo,categoryIds. "
        "mode must be 'search'. "
        "query must be non-empty. "
        "dateFrom/dateTo should be YYYY-MM-DD or null. "
        "Example input: '지난주 병원 일정 찾아줘'. "
        "Example output: {'mode':'search','query':'병원 일정','dateFrom':'2026-02-01','dateTo':'2026-02-07','categoryIds':['medical']}."
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
