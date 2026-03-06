from app.interpreters import local_search_interpret


def test_local_search_interpret_supports_all_history_filter():
    result = local_search_interpret("여태까지 한 모든 기록 찾아줘")

    assert result.mode == "search"
    assert result.strategy == "all_events"
    assert result.query == "*"
    assert result.dateFrom is None
    assert result.dateTo is None
    assert result.sortOrder == "desc"


def test_local_search_interpret_supports_date_range_and_sort():
    result = local_search_interpret("2026-01-10부터 2026-01-20까지 병원 기록 오래된순으로 찾아줘")

    assert result.mode == "search"
    assert result.strategy == "combined"
    assert result.query == "병원 기록"
    assert result.dateFrom == "2026-01-10"
    assert result.dateTo == "2026-01-20"
    assert result.sortOrder == "asc"
