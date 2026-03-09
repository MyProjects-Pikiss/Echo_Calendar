from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass

import httpx

from .config import settings


class LlmClientError(Exception):
    pass


@dataclass(frozen=True)
class LlmUsage:
    input_tokens: int
    output_tokens: int
    total_tokens: int


@dataclass(frozen=True)
class LlmCompletionResult:
    payload: dict
    usage: LlmUsage | None


class OpenAILlmClient:
    def __init__(self) -> None:
        self._api_key = settings.openai_api_key
        self._model = settings.openai_model

    @property
    def enabled(self) -> bool:
        return bool(self._api_key)

    async def json_completion(self, system_prompt: str, user_prompt: str) -> LlmCompletionResult:
        if not self.enabled:
            raise LlmClientError("OPENAI_API_KEY is not configured")

        payload = {
            "model": self._model,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json",
        }

        last_error: Exception | None = None
        try:
            async with asyncio.timeout(settings.llm_deadline_seconds):
                for _ in range(settings.llm_max_retries + 1):
                    try:
                        async with httpx.AsyncClient(timeout=settings.llm_timeout_seconds) as client:
                            response = await client.post(
                                "https://api.openai.com/v1/chat/completions",
                                headers=headers,
                                json=payload,
                            )
                        response.raise_for_status()
                        body = response.json()
                        content = body["choices"][0]["message"]["content"]
                        parsed = json.loads(content)
                        if not isinstance(parsed, dict):
                            raise LlmClientError("LLM response is not a JSON object")
                        usage = _extract_usage(body.get("usage"))
                        return LlmCompletionResult(payload=parsed, usage=usage)
                    except httpx.HTTPStatusError as exc:
                        response_preview = exc.response.text.strip().replace("\n", " ")
                        if len(response_preview) > 600:
                            response_preview = response_preview[:600] + "...(truncated)"
                        last_error = LlmClientError(
                            f"HTTP {exc.response.status_code} from OpenAI: {response_preview}"
                        )
                        await asyncio.sleep(0.25)
                    except Exception as exc:  # noqa: BLE001
                        last_error = LlmClientError(_format_exception_message(exc))
                        await asyncio.sleep(0.25)
        except TimeoutError as exc:
            raise LlmClientError(
                f"LLM call exceeded deadline ({settings.llm_deadline_seconds}s)"
            ) from exc
        if last_error is None:
            raise LlmClientError("LLM call failed")
        if isinstance(last_error, LlmClientError):
            raise last_error
        raise LlmClientError(_format_exception_message(last_error))


def _format_exception_message(exc: Exception) -> str:
    detail = str(exc).strip()
    if not detail:
        detail = repr(exc)
    return f"{exc.__class__.__name__}: {detail}"


def _extract_usage(raw_usage: object) -> LlmUsage | None:
    if not isinstance(raw_usage, dict):
        return None
    prompt_tokens = raw_usage.get("prompt_tokens")
    completion_tokens = raw_usage.get("completion_tokens")
    total_tokens = raw_usage.get("total_tokens")
    input_tokens = raw_usage.get("input_tokens")
    output_tokens = raw_usage.get("output_tokens")

    in_tokens = _to_non_negative_int(input_tokens)
    if in_tokens is None:
        in_tokens = _to_non_negative_int(prompt_tokens)

    out_tokens = _to_non_negative_int(output_tokens)
    if out_tokens is None:
        out_tokens = _to_non_negative_int(completion_tokens)

    total = _to_non_negative_int(total_tokens)
    if total is None and in_tokens is not None and out_tokens is not None:
        total = in_tokens + out_tokens

    if in_tokens is None and out_tokens is None and total is None:
        return None

    return LlmUsage(
        input_tokens=in_tokens or 0,
        output_tokens=out_tokens or 0,
        total_tokens=total or 0,
    )


def _to_non_negative_int(value: object) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value if value >= 0 else None
    if isinstance(value, float):
        as_int = int(value)
        return as_int if as_int >= 0 else None
    if isinstance(value, str) and value.strip().isdigit():
        as_int = int(value.strip())
        return as_int if as_int >= 0 else None
    return None
