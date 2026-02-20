from __future__ import annotations

import json
import logging
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from .config import settings
from .contract_guard import (
    ContractValidationError,
    ensure_input_response,
    ensure_refine_response,
    ensure_search_response,
)
from .interpreters import local_input_interpret, local_refine_field, local_search_interpret
from .llm_client import LlmClientError, OpenAILlmClient
from .logging_utils import logger, mask_text
from .models import DraftField, InputInterpretRequest, RefineFieldRequest, SearchInterpretRequest
from .prompts import input_prompts, refine_prompts, search_prompts
from .rate_limit import SimpleRateLimiter


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(title="Echo Calendar AI Backend", version="0.1.0")
llm_client = OpenAILlmClient()
rate_limiter = SimpleRateLimiter(max_requests=settings.rate_limit_per_minute, window_seconds=60)


def stable_error(mode: str, error_code: str, message: str, status: int = 400) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={"mode": mode, "errorCode": error_code, "message": message},
    )


def _get_client_key(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "").split(",")[0].strip()
    if forwarded:
        return forwarded
    return request.client.host if request.client else "unknown"


def _require_mode(payload: dict[str, Any], expected: str) -> JSONResponse | None:
    mode = str(payload.get("mode", "")).strip().lower()
    if mode != expected:
        return stable_error(expected, "MODE_MISMATCH", f"mode must be '{expected}'")
    return None


def _require_transcript(payload: dict[str, Any], mode: str) -> JSONResponse | None:
    transcript = str(payload.get("transcript", "")).strip()
    if not transcript:
        return stable_error(mode, "MISSING_TRANSCRIPT", "transcript is required")
    return None


def _parse_json_payload(raw: bytes, mode: str) -> tuple[dict[str, Any] | None, JSONResponse | None]:
    try:
        parsed = json.loads(raw.decode("utf-8"))
    except Exception:
        return None, stable_error(mode, "INVALID_JSON", "request body must be valid JSON")
    if not isinstance(parsed, dict):
        return None, stable_error(mode, "INVALID_SCHEMA", "request body must be a JSON object")
    return parsed, None


@app.get("/health")
async def health() -> dict[str, Any]:
    return {"status": "ok", "llmEnabled": llm_client.enabled, "model": settings.openai_model}


@app.post("/ai/input-interpret")
async def input_interpret(request: Request) -> JSONResponse:
    client_key = _get_client_key(request)
    if not rate_limiter.allow(client_key):
        return stable_error("input", "RATE_LIMITED", "rate limit exceeded", status=429)

    payload, parse_error = _parse_json_payload(await request.body(), "input")
    if parse_error:
        return parse_error
    assert payload is not None
    if error := _require_mode(payload, "input"):
        return error
    if error := _require_transcript(payload, "input"):
        return error

    try:
        req = InputInterpretRequest.model_validate(payload)
    except ValidationError:
        return stable_error("input", "INVALID_SCHEMA", "input schema is invalid")

    transcript = req.transcript.strip()
    selected_date = req.selectedDate.strip()
    if not selected_date:
        return stable_error("input", "INVALID_SCHEMA", "selectedDate is required")
    logger.info("input_interpret request transcript=%s", mask_text(transcript))

    if llm_client.enabled:
        try:
            system_prompt, user_prompt = input_prompts(transcript, selected_date)
            parsed = await llm_client.json_completion(system_prompt, user_prompt)
            guarded = ensure_input_response(parsed, transcript, selected_date)
            return JSONResponse(status_code=200, content=guarded.model_dump())
        except LlmClientError as exc:
            logger.warning("input_interpret llm_failure reason=%s", str(exc))
            if not settings.enable_local_fallback:
                return stable_error("input", "UPSTREAM_FAILURE", "llm request failed", status=502)
        except ContractValidationError as exc:
            logger.warning("input_interpret invalid_contract reason=%s", str(exc))
            if not settings.enable_local_fallback:
                return stable_error("input", "INVALID_SCHEMA", "llm response schema invalid", status=502)

    fallback = local_input_interpret(transcript, selected_date or "1970-01-01")
    return JSONResponse(status_code=200, content=fallback.model_dump())


@app.post("/ai/search-interpret")
async def search_interpret(request: Request) -> JSONResponse:
    client_key = _get_client_key(request)
    if not rate_limiter.allow(client_key):
        return stable_error("search", "RATE_LIMITED", "rate limit exceeded", status=429)

    payload, parse_error = _parse_json_payload(await request.body(), "search")
    if parse_error:
        return parse_error
    assert payload is not None
    if error := _require_mode(payload, "search"):
        return error
    if error := _require_transcript(payload, "search"):
        return error

    try:
        req = SearchInterpretRequest.model_validate(payload)
    except ValidationError:
        return stable_error("search", "INVALID_SCHEMA", "search schema is invalid")
    transcript = req.transcript.strip()
    logger.info("search_interpret request transcript=%s", mask_text(transcript))

    if llm_client.enabled:
        try:
            system_prompt, user_prompt = search_prompts(transcript)
            parsed = await llm_client.json_completion(system_prompt, user_prompt)
            guarded = ensure_search_response(parsed, transcript)
            return JSONResponse(status_code=200, content=guarded.model_dump())
        except LlmClientError as exc:
            logger.warning("search_interpret llm_failure reason=%s", str(exc))
            if not settings.enable_local_fallback:
                return stable_error("search", "UPSTREAM_FAILURE", "llm request failed", status=502)
        except ContractValidationError as exc:
            logger.warning("search_interpret invalid_contract reason=%s", str(exc))
            if not settings.enable_local_fallback:
                return stable_error("search", "INVALID_SCHEMA", "llm response schema invalid", status=502)

    fallback = local_search_interpret(transcript)
    return JSONResponse(status_code=200, content=fallback.model_dump())


@app.post("/ai/refine-field")
async def refine_field(request: Request) -> JSONResponse:
    client_key = _get_client_key(request)
    if not rate_limiter.allow(client_key):
        return stable_error("refine", "RATE_LIMITED", "rate limit exceeded", status=429)

    payload, parse_error = _parse_json_payload(await request.body(), "refine")
    if parse_error:
        return parse_error
    assert payload is not None
    if error := _require_mode(payload, "refine"):
        return error
    if error := _require_transcript(payload, "refine"):
        return error

    field = str(payload.get("field", "")).strip().lower()
    valid_fields = {value for value in DraftField.__args__}
    if field not in valid_fields:
        return stable_error("refine", "INVALID_FIELD", f"field must be one of {sorted(valid_fields)}")

    try:
        req = RefineFieldRequest.model_validate(
            {
                **payload,
                "field": field,
            }
        )
    except ValidationError:
        return stable_error("refine", "INVALID_SCHEMA", "refine schema is invalid")

    transcript = req.transcript.strip()
    current_value = req.currentValue.strip()
    selected_date = req.selectedDate.strip()
    if not selected_date:
        return stable_error("refine", "INVALID_SCHEMA", "selectedDate is required")
    logger.info("refine_field request field=%s transcript=%s", field, mask_text(transcript))

    if llm_client.enabled:
        try:
            system_prompt, user_prompt = refine_prompts(field, transcript, current_value, selected_date)
            parsed = await llm_client.json_completion(system_prompt, user_prompt)
            guarded = ensure_refine_response(parsed, field, current_value)
            return JSONResponse(status_code=200, content=guarded.model_dump())
        except LlmClientError as exc:
            logger.warning("refine_field llm_failure reason=%s", str(exc))
            if not settings.enable_local_fallback:
                return stable_error("refine", "UPSTREAM_FAILURE", "llm request failed", status=502)
        except ContractValidationError as exc:
            logger.warning("refine_field invalid_contract reason=%s", str(exc))
            if not settings.enable_local_fallback:
                return stable_error("refine", "INVALID_SCHEMA", "llm response schema invalid", status=502)

    fallback = local_refine_field(field, transcript, current_value)
    return JSONResponse(status_code=200, content=fallback.model_dump())
