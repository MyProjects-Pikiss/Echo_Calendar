#!/usr/bin/env python3
from __future__ import annotations

from app.contract_guard import ensure_input_response, ensure_refine_response, ensure_search_response
from app.interpreters import local_input_interpret, local_refine_field, local_search_interpret


def main() -> int:
    selected_date = "2026-02-11"
    input_result = local_input_interpret("내일 9시 회의", selected_date)
    ensure_input_response(input_result.model_dump(), "내일 9시 회의", selected_date)

    search_result = local_search_interpret("지난주 병원 일정 찾아줘")
    ensure_search_response(search_result.model_dump(), "지난주 병원 일정 찾아줘")

    refine_result = local_refine_field("time", "3시 30분으로 바꿔줘", "09:00")
    ensure_refine_response(refine_result.model_dump(), "time", "09:00")

    print("backend self-check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
