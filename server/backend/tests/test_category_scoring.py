from app.contract_guard import ensure_input_response


def test_input_category_scoring_prefers_spending_over_delivery_context():
    result = ensure_input_response(
        raw={
            "mode": "input",
            "summary": "이마트 방문",
            "time": "",
            "repeatYearly": None,
            "categoryId": "delivery",
            "placeText": "",
            "body": "배달일 끝나고 이마트 방문",
            "labels": [],
            "missingRequired": [],
            "intent": "create",
            "date": "2026-03-08",
        },
        transcript="배달일 끝나고 이마트 방문",
        selected_date="2026-03-08",
    )
    assert result.categoryId == "spending"


def test_input_category_scoring_detects_delivery_when_delivery_intent_is_clear():
    result = ensure_input_response(
        raw={
            "mode": "input",
            "summary": "택배 도착 확인",
            "time": "",
            "repeatYearly": None,
            "categoryId": "other",
            "placeText": "",
            "body": "택배 도착 확인",
            "labels": [],
            "missingRequired": [],
            "intent": "create",
            "date": "2026-03-08",
        },
        transcript="택배 도착 확인",
        selected_date="2026-03-08",
    )
    assert result.categoryId == "delivery"
