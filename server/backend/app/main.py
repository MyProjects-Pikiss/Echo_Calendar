from __future__ import annotations

import asyncio
import json
import logging
import sqlite3
import time
from datetime import date
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import ValidationError

from .config import settings
from .contract_guard import (
    ContractValidationError,
    ensure_input_response,
    ensure_modify_response,
    ensure_refine_response,
    ensure_search_response,
)
from .interpreters import (
    local_input_interpret,
    local_modify_interpret,
    local_refine_field,
    local_search_interpret,
)
from .holiday_store import init_holiday_db
from .holiday_sync import ensure_range_loaded
from .llm_client import LlmClientError, LlmUsage, OpenAILlmClient
from .logging_utils import logger, mask_text
from .models import (
    AuthLoginRequest,
    AuthSignupRequest,
    DraftField,
    InputInterpretRequest,
    ModifyInterpretRequest,
    RefineFieldRequest,
    SearchInterpretRequest,
)
from .prompts import input_prompts, modify_prompts, refine_prompts, search_prompts
from .rate_limit import SimpleRateLimiter
from .usage_store import (
    UserDeleteError,
    authenticate_user,
    create_session,
    create_user,
    delete_user,
    ensure_admin_user,
    get_user_by_session,
    init_usage_db,
    list_users,
    log_usage_event,
    usage_overview,
    usage_user_detail,
)


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(title="Echo Calendar AI Backend", version="0.1.0")
llm_client = OpenAILlmClient()
rate_limiter = SimpleRateLimiter(max_requests=settings.rate_limit_per_minute, window_seconds=60)
holiday_db_path = Path(settings.holiday_db_path)
usage_db_path = Path(settings.usage_db_path)
dashboard_html_path = Path(__file__).resolve().parent / "templates" / "usage_dashboard.html"
_usage_event_queue: asyncio.Queue[dict[str, Any] | None] = asyncio.Queue(maxsize=5000)
_usage_worker_task: asyncio.Task[None] | None = None


def _is_path_enabled(path: str) -> bool:
    # Allow docs/health in every mode for operability.
    if path in {"/", "/health", "/openapi.json", "/docs", "/redoc"}:
        return True
    mode = settings.server_mode.strip().lower()
    if mode in {"", "all"}:
        return True
    if mode == "ai":
        return (
            path.startswith("/ai/")
            or path.startswith("/auth/")
            or path.startswith("/usage/me")
        )
    if mode == "core":
        return (
            path.startswith("/auth/")
            or path.startswith("/usage/")
            or path.startswith("/holidays")
        )
    return True


@app.middleware("http")
async def _mode_gate_middleware(request: Request, call_next):
    if _is_path_enabled(request.url.path):
        return await call_next(request)
    return stable_error(
        "server",
        "NOT_ENABLED_IN_MODE",
        f"endpoint is disabled in server mode '{settings.server_mode}'",
        status=404,
    )


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


def _extract_bearer_token(request: Request) -> str | None:
    auth = request.headers.get("authorization", "").strip()
    if not auth:
        return None
    if not auth.lower().startswith("bearer "):
        return None
    token = auth[7:].strip()
    return token if token else None


def _auth_user_from_request(request: Request) -> dict[str, Any] | None:
    token = _extract_bearer_token(request)
    if not token:
        return None
    return get_user_by_session(usage_db_path, token)


def _resolve_usage_user_id(request: Request, payload_user_id: str | None) -> str | None:
    authenticated = _auth_user_from_request(request)
    if authenticated is not None:
        return str(authenticated["id"])
    header_user_id = request.headers.get("x-echo-user-id", "").strip()
    if header_user_id:
        return header_user_id
    if payload_user_id:
        value = payload_user_id.strip()
        return value if value else None
    return None


def _require_usage_admin(request: Request) -> tuple[dict[str, Any] | None, JSONResponse | None]:
    user = _auth_user_from_request(request)
    if user is None:
        return None, stable_error("usage", "UNAUTHORIZED", "valid bearer token is required", status=401)
    if str(user.get("role", "")).strip().lower() == "admin":
        return user, None
    owner = settings.usage_dashboard_owner_username.strip()
    if owner and str(user.get("username", "")) != owner:
        return None, stable_error("usage", "FORBIDDEN", "dashboard access denied", status=403)
    return user, None


def _record_ai_usage(
    *,
    endpoint: str,
    user_id: str | None,
    transcript: str,
    success: bool,
    usage: LlmUsage | None = None,
    started_at: float | None = None,
    error_code: str | None = None,
    error_message: str | None = None,
) -> None:
    latency_ms = 0
    if started_at is not None:
        latency_ms = int((time.perf_counter() - started_at) * 1000)
    event = {
        "user_id": user_id,
        "endpoint": endpoint,
        "model": settings.openai_model,
        "transcript": transcript,
        "success": success,
        "input_tokens": usage.input_tokens if usage else 0,
        "output_tokens": usage.output_tokens if usage else 0,
        "total_tokens": usage.total_tokens if usage else 0,
        "latency_ms": latency_ms,
        "error_code": error_code,
        "error_message": error_message,
    }
    try:
        _usage_event_queue.put_nowait(event)
    except asyncio.QueueFull:
        logger.warning("usage_queue_full dropped endpoint=%s", endpoint)


async def _usage_worker() -> None:
    while True:
        event = await _usage_event_queue.get()
        try:
            if event is None:
                break
            log_usage_event(usage_db_path, **event)
        except Exception as exc:  # noqa: BLE001
            logger.warning("usage_log_failure reason=%s", str(exc))
        finally:
            _usage_event_queue.task_done()


@app.on_event("startup")
async def on_startup() -> None:
    global _usage_worker_task
    init_holiday_db(holiday_db_path)
    init_usage_db(usage_db_path)
    if settings.usage_admin_username and settings.usage_admin_password:
        ensure_admin_user(
            usage_db_path,
            username=settings.usage_admin_username,
            password=settings.usage_admin_password,
        )
    _usage_worker_task = asyncio.create_task(_usage_worker())


@app.on_event("shutdown")
async def on_shutdown() -> None:
    global _usage_worker_task
    if _usage_worker_task is None:
        return
    try:
        _usage_event_queue.put_nowait(None)
    except asyncio.QueueFull:
        logger.warning("usage_queue_full shutdown_drain_skipped")
    await _usage_worker_task
    _usage_worker_task = None


@app.get("/health")
async def health() -> dict[str, Any]:
    return {"status": "ok", "llmEnabled": llm_client.enabled, "model": settings.openai_model}


@app.post("/auth/signup")
async def auth_signup(request: Request) -> JSONResponse:
    payload, parse_error = _parse_json_payload(await request.body(), "auth")
    if parse_error:
        return parse_error
    assert payload is not None
    try:
        req = AuthSignupRequest.model_validate(payload)
    except ValidationError:
        return stable_error("auth", "INVALID_SCHEMA", "signup schema is invalid")

    username = req.username.strip()
    password = req.password.strip()
    if not username or not password:
        return stable_error("auth", "INVALID_INPUT", "username/password is required")

    try:
        user = create_user(usage_db_path, username=username, password=password)
    except sqlite3.IntegrityError:
        return stable_error("auth", "USERNAME_EXISTS", "username already exists", status=409)
    token = create_session(usage_db_path, user["id"])
    return JSONResponse(
        status_code=200,
        content={
            "user": user,
            "accessToken": token,
        },
    )


@app.post("/auth/login")
async def auth_login(request: Request) -> JSONResponse:
    payload, parse_error = _parse_json_payload(await request.body(), "auth")
    if parse_error:
        return parse_error
    assert payload is not None
    try:
        req = AuthLoginRequest.model_validate(payload)
    except ValidationError:
        return stable_error("auth", "INVALID_SCHEMA", "login schema is invalid")

    user = authenticate_user(
        usage_db_path,
        username=req.username.strip(),
        password=req.password.strip(),
    )
    if user is None:
        return stable_error("auth", "AUTH_FAILED", "invalid username or password", status=401)
    token = create_session(usage_db_path, user["id"])
    return JSONResponse(
        status_code=200,
        content={
            "user": user,
            "accessToken": token,
        },
    )


@app.get("/auth/me")
async def auth_me(request: Request) -> JSONResponse:
    user = _auth_user_from_request(request)
    if user is None:
        return stable_error("auth", "UNAUTHORIZED", "valid bearer token is required", status=401)
    return JSONResponse(status_code=200, content={"user": user})


@app.get("/usage/overview")
async def usage_overview_api(request: Request) -> JSONResponse:
    _, auth_error = _require_usage_admin(request)
    if auth_error is not None:
        return auth_error
    return JSONResponse(status_code=200, content=usage_overview(usage_db_path))


@app.get("/usage/user-detail")
async def usage_user_detail_api(request: Request, userId: str | None = None, limit: int = 100) -> JSONResponse:
    _, auth_error = _require_usage_admin(request)
    if auth_error is not None:
        return auth_error
    safe_limit = max(1, min(limit, 500))
    return JSONResponse(status_code=200, content=usage_user_detail(usage_db_path, user_id=userId, limit=safe_limit))


@app.get("/usage/me")
async def usage_me_api(request: Request, limit: int = 100) -> JSONResponse:
    user = _auth_user_from_request(request)
    if user is None:
        return stable_error("usage", "UNAUTHORIZED", "valid bearer token is required", status=401)
    safe_limit = max(1, min(limit, 500))
    return JSONResponse(
        status_code=200,
        content=usage_user_detail(usage_db_path, user_id=str(user["id"]), limit=safe_limit),
    )


@app.get("/usage/users")
async def usage_users_api(request: Request) -> JSONResponse:
    _, auth_error = _require_usage_admin(request)
    if auth_error is not None:
        return auth_error
    return JSONResponse(status_code=200, content={"users": list_users(usage_db_path)})


@app.post("/usage/users")
async def usage_users_create_api(request: Request) -> JSONResponse:
    _, auth_error = _require_usage_admin(request)
    if auth_error is not None:
        return auth_error
    payload, parse_error = _parse_json_payload(await request.body(), "usage")
    if parse_error:
        return parse_error
    assert payload is not None
    try:
        req = AuthSignupRequest.model_validate(payload)
    except ValidationError:
        return stable_error("usage", "INVALID_SCHEMA", "user schema is invalid")
    role = str(payload.get("role", "user")).strip().lower()
    if role not in {"user", "admin"}:
        return stable_error("usage", "INVALID_INPUT", "role must be 'user' or 'admin'")
    username = req.username.strip()
    password = req.password.strip()
    try:
        user = create_user(usage_db_path, username=username, password=password, role=role)
    except sqlite3.IntegrityError:
        return stable_error("usage", "USERNAME_EXISTS", "username already exists", status=409)
    return JSONResponse(status_code=200, content={"user": user})


@app.delete("/usage/users/{user_id}")
async def usage_users_delete_api(request: Request, user_id: str) -> JSONResponse:
    auth_user, auth_error = _require_usage_admin(request)
    if auth_error is not None:
        return auth_error
    assert auth_user is not None
    target_user_id = user_id.strip()
    if not target_user_id:
        return stable_error("usage", "INVALID_INPUT", "user id is required")
    try:
        deleted = delete_user(
            usage_db_path,
            user_id=target_user_id,
            protected_user_id=str(auth_user["id"]),
        )
    except UserDeleteError as exc:
        code = str(exc)
        if code == "USER_NOT_FOUND":
            return stable_error("usage", "NOT_FOUND", "user not found", status=404)
        if code == "CANNOT_DELETE_SELF":
            return stable_error("usage", "FORBIDDEN", "cannot delete current account", status=403)
        if code == "LAST_ADMIN":
            return stable_error("usage", "FORBIDDEN", "cannot delete the last admin account", status=403)
        return stable_error("usage", "INVALID_INPUT", code, status=400)
    return JSONResponse(status_code=200, content={"deleted": deleted})


@app.get("/usage/dashboard")
async def usage_dashboard(request: Request):
    required_key = settings.usage_dashboard_access_key.strip()
    if required_key:
        access_key = request.query_params.get("key", "").strip()
        if access_key != required_key:
            return stable_error("usage", "FORBIDDEN", "invalid dashboard access key", status=403)
    if not dashboard_html_path.exists():
        return stable_error("usage", "NOT_FOUND", "dashboard template missing", status=500)
    html = dashboard_html_path.read_text(encoding="utf-8")
    return HTMLResponse(content=html, status_code=200)


@app.get("/holidays")
async def holidays(startDate: str, endDate: str) -> JSONResponse:
    try:
        start = date.fromisoformat(startDate.strip())
        end = date.fromisoformat(endDate.strip())
    except ValueError:
        return stable_error("holiday", "INVALID_DATE", "startDate/endDate must be YYYY-MM-DD")

    if start > end:
        return stable_error("holiday", "INVALID_RANGE", "startDate must be <= endDate")
    try:
        items = await ensure_range_loaded(holiday_db_path, start, end)
    except Exception as exc:  # noqa: BLE001
        logger.warning("holiday_fetch_failure reason=%s", str(exc))
        return stable_error("holiday", "INTERNAL_ERROR", "failed to read holiday data", status=500)

    payload = {
        "holidays": [
            {
                "date": item.date.isoformat(),
                "label": item.label,
                "kind": item.kind,
            }
            for item in items
        ]
    }
    return JSONResponse(status_code=200, content=payload)


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
    user_id = _resolve_usage_user_id(request, req.userId)
    started_at = time.perf_counter()
    if not selected_date:
        return stable_error("input", "INVALID_SCHEMA", "selectedDate is required")
    logger.info("input_interpret request transcript=%s", mask_text(transcript))

    if llm_client.enabled:
        try:
            system_prompt, user_prompt = input_prompts(transcript, selected_date)
            completion = await llm_client.json_completion(system_prompt, user_prompt)
            guarded = ensure_input_response(completion.payload, transcript, selected_date)
            _record_ai_usage(
                endpoint="/ai/input-interpret",
                user_id=user_id,
                transcript=transcript,
                success=True,
                usage=completion.usage,
                started_at=started_at,
            )
            return JSONResponse(status_code=200, content=guarded.model_dump())
        except LlmClientError as exc:
            logger.warning("input_interpret llm_failure reason=%s", str(exc))
            _record_ai_usage(
                endpoint="/ai/input-interpret",
                user_id=user_id,
                transcript=transcript,
                success=False,
                started_at=started_at,
                error_code="UPSTREAM_FAILURE",
                error_message=str(exc),
            )
            if not settings.enable_local_fallback:
                return stable_error("input", "UPSTREAM_FAILURE", "llm request failed", status=502)
        except ContractValidationError as exc:
            logger.warning("input_interpret invalid_contract reason=%s", str(exc))
            _record_ai_usage(
                endpoint="/ai/input-interpret",
                user_id=user_id,
                transcript=transcript,
                success=False,
                started_at=started_at,
                error_code="INVALID_SCHEMA",
                error_message=str(exc),
            )
            if not settings.enable_local_fallback:
                return stable_error("input", "INVALID_SCHEMA", "llm response schema invalid", status=502)

    if not settings.enable_local_fallback:
        _record_ai_usage(
            endpoint="/ai/input-interpret",
            user_id=user_id,
            transcript=transcript,
            success=False,
            started_at=started_at,
            error_code="UPSTREAM_UNAVAILABLE",
            error_message="llm client is disabled and local fallback is disabled",
        )
        return stable_error("input", "UPSTREAM_FAILURE", "llm is unavailable", status=502)

    fallback = local_input_interpret(transcript, selected_date or "1970-01-01")
    _record_ai_usage(
        endpoint="/ai/input-interpret",
        user_id=user_id,
        transcript=transcript,
        success=True,
        started_at=started_at,
    )
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
    user_id = _resolve_usage_user_id(request, req.userId)
    started_at = time.perf_counter()
    logger.info("search_interpret request transcript=%s", mask_text(transcript))

    if llm_client.enabled:
        try:
            system_prompt, user_prompt = search_prompts(transcript)
            completion = await llm_client.json_completion(system_prompt, user_prompt)
            guarded = ensure_search_response(completion.payload, transcript)
            _record_ai_usage(
                endpoint="/ai/search-interpret",
                user_id=user_id,
                transcript=transcript,
                success=True,
                usage=completion.usage,
                started_at=started_at,
            )
            return JSONResponse(status_code=200, content=guarded.model_dump())
        except LlmClientError as exc:
            logger.warning("search_interpret llm_failure reason=%s", str(exc))
            _record_ai_usage(
                endpoint="/ai/search-interpret",
                user_id=user_id,
                transcript=transcript,
                success=False,
                started_at=started_at,
                error_code="UPSTREAM_FAILURE",
                error_message=str(exc),
            )
            if not settings.enable_local_fallback:
                return stable_error("search", "UPSTREAM_FAILURE", "llm request failed", status=502)
        except ContractValidationError as exc:
            logger.warning("search_interpret invalid_contract reason=%s", str(exc))
            _record_ai_usage(
                endpoint="/ai/search-interpret",
                user_id=user_id,
                transcript=transcript,
                success=False,
                started_at=started_at,
                error_code="INVALID_SCHEMA",
                error_message=str(exc),
            )
            if not settings.enable_local_fallback:
                return stable_error("search", "INVALID_SCHEMA", "llm response schema invalid", status=502)

    if not settings.enable_local_fallback:
        _record_ai_usage(
            endpoint="/ai/search-interpret",
            user_id=user_id,
            transcript=transcript,
            success=False,
            started_at=started_at,
            error_code="UPSTREAM_UNAVAILABLE",
            error_message="llm client is disabled and local fallback is disabled",
        )
        return stable_error("search", "UPSTREAM_FAILURE", "llm is unavailable", status=502)

    fallback = local_search_interpret(transcript)
    _record_ai_usage(
        endpoint="/ai/search-interpret",
        user_id=user_id,
        transcript=transcript,
        success=True,
        started_at=started_at,
    )
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
    user_id = _resolve_usage_user_id(request, req.userId)
    started_at = time.perf_counter()
    if not selected_date:
        return stable_error("refine", "INVALID_SCHEMA", "selectedDate is required")
    logger.info("refine_field request field=%s transcript=%s", field, mask_text(transcript))

    if llm_client.enabled:
        try:
            system_prompt, user_prompt = refine_prompts(field, transcript, current_value, selected_date)
            completion = await llm_client.json_completion(system_prompt, user_prompt)
            guarded = ensure_refine_response(completion.payload, field, current_value)
            _record_ai_usage(
                endpoint="/ai/refine-field",
                user_id=user_id,
                transcript=transcript,
                success=True,
                usage=completion.usage,
                started_at=started_at,
            )
            return JSONResponse(status_code=200, content=guarded.model_dump())
        except LlmClientError as exc:
            logger.warning("refine_field llm_failure reason=%s", str(exc))
            _record_ai_usage(
                endpoint="/ai/refine-field",
                user_id=user_id,
                transcript=transcript,
                success=False,
                started_at=started_at,
                error_code="UPSTREAM_FAILURE",
                error_message=str(exc),
            )
            if not settings.enable_local_fallback:
                return stable_error("refine", "UPSTREAM_FAILURE", "llm request failed", status=502)
        except ContractValidationError as exc:
            logger.warning("refine_field invalid_contract reason=%s", str(exc))
            _record_ai_usage(
                endpoint="/ai/refine-field",
                user_id=user_id,
                transcript=transcript,
                success=False,
                started_at=started_at,
                error_code="INVALID_SCHEMA",
                error_message=str(exc),
            )
            if not settings.enable_local_fallback:
                return stable_error("refine", "INVALID_SCHEMA", "llm response schema invalid", status=502)

    if not settings.enable_local_fallback:
        _record_ai_usage(
            endpoint="/ai/refine-field",
            user_id=user_id,
            transcript=transcript,
            success=False,
            started_at=started_at,
            error_code="UPSTREAM_UNAVAILABLE",
            error_message="llm client is disabled and local fallback is disabled",
        )
        return stable_error("refine", "UPSTREAM_FAILURE", "llm is unavailable", status=502)

    fallback = local_refine_field(field, transcript, current_value)
    _record_ai_usage(
        endpoint="/ai/refine-field",
        user_id=user_id,
        transcript=transcript,
        success=True,
        started_at=started_at,
    )
    return JSONResponse(status_code=200, content=fallback.model_dump())


@app.post("/ai/modify-interpret")
async def modify_interpret(request: Request) -> JSONResponse:
    client_key = _get_client_key(request)
    if not rate_limiter.allow(client_key):
        return stable_error("modify", "RATE_LIMITED", "rate limit exceeded", status=429)

    payload, parse_error = _parse_json_payload(await request.body(), "modify")
    if parse_error:
        return parse_error
    assert payload is not None
    if error := _require_mode(payload, "modify"):
        return error
    if error := _require_transcript(payload, "modify"):
        return error

    try:
        req = ModifyInterpretRequest.model_validate(payload)
    except ValidationError:
        return stable_error("modify", "INVALID_SCHEMA", "modify schema is invalid")

    transcript = req.transcript.strip()
    selected_date = req.selectedDate.strip()
    user_id = _resolve_usage_user_id(request, req.userId)
    started_at = time.perf_counter()
    if not selected_date:
        return stable_error("modify", "INVALID_SCHEMA", "selectedDate is required")
    logger.info("modify_interpret request transcript=%s", mask_text(transcript))

    if llm_client.enabled:
        try:
            system_prompt, user_prompt = modify_prompts(
                transcript=transcript,
                selected_date=selected_date,
                current_summary=req.currentSummary,
                current_time=req.currentTime,
                current_category_id=req.currentCategoryId,
                current_place_text=req.currentPlaceText,
                current_body=req.currentBody,
                current_labels=req.currentLabels,
            )
            completion = await llm_client.json_completion(system_prompt, user_prompt)
            guarded = ensure_modify_response(completion.payload, transcript)
            _record_ai_usage(
                endpoint="/ai/modify-interpret",
                user_id=user_id,
                transcript=transcript,
                success=True,
                usage=completion.usage,
                started_at=started_at,
            )
            return JSONResponse(status_code=200, content=guarded.model_dump())
        except LlmClientError as exc:
            logger.warning("modify_interpret llm_failure reason=%s", str(exc))
            _record_ai_usage(
                endpoint="/ai/modify-interpret",
                user_id=user_id,
                transcript=transcript,
                success=False,
                started_at=started_at,
                error_code="UPSTREAM_FAILURE",
                error_message=str(exc),
            )
            if not settings.enable_local_fallback:
                return stable_error("modify", "UPSTREAM_FAILURE", "llm request failed", status=502)
        except ContractValidationError as exc:
            logger.warning("modify_interpret invalid_contract reason=%s", str(exc))
            _record_ai_usage(
                endpoint="/ai/modify-interpret",
                user_id=user_id,
                transcript=transcript,
                success=False,
                started_at=started_at,
                error_code="INVALID_SCHEMA",
                error_message=str(exc),
            )
            if not settings.enable_local_fallback:
                return stable_error("modify", "INVALID_SCHEMA", "llm response schema invalid", status=502)

    if not settings.enable_local_fallback:
        _record_ai_usage(
            endpoint="/ai/modify-interpret",
            user_id=user_id,
            transcript=transcript,
            success=False,
            started_at=started_at,
            error_code="UPSTREAM_UNAVAILABLE",
            error_message="llm client is disabled and local fallback is disabled",
        )
        return stable_error("modify", "UPSTREAM_FAILURE", "llm is unavailable", status=502)

    fallback = local_modify_interpret(
        transcript=transcript,
        current_summary=req.currentSummary,
        current_time=req.currentTime,
        current_category_id=req.currentCategoryId,
        current_place_text=req.currentPlaceText,
        current_body=req.currentBody,
        current_labels=req.currentLabels,
    )
    _record_ai_usage(
        endpoint="/ai/modify-interpret",
        user_id=user_id,
        transcript=transcript,
        success=True,
        started_at=started_at,
    )
    return JSONResponse(status_code=200, content=fallback.model_dump())
