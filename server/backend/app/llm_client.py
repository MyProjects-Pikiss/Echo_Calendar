from __future__ import annotations

import asyncio
import json

import httpx

from .config import settings


class LlmClientError(Exception):
    pass


class OpenAILlmClient:
    def __init__(self) -> None:
        self._api_key = settings.openai_api_key
        self._model = settings.openai_model

    @property
    def enabled(self) -> bool:
        return bool(self._api_key)

    async def json_completion(self, system_prompt: str, user_prompt: str) -> dict:
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
                        return parsed
                    except httpx.HTTPStatusError as exc:
                        response_preview = exc.response.text.strip().replace("\n", " ")
                        if len(response_preview) > 600:
                            response_preview = response_preview[:600] + "...(truncated)"
                        last_error = LlmClientError(
                            f"HTTP {exc.response.status_code} from OpenAI: {response_preview}"
                        )
                        await asyncio.sleep(0.25)
                    except Exception as exc:  # noqa: BLE001
                        last_error = exc
                        await asyncio.sleep(0.25)
        except TimeoutError as exc:
            raise LlmClientError(
                f"LLM call exceeded deadline ({settings.llm_deadline_seconds}s)"
            ) from exc
        raise LlmClientError(str(last_error) if last_error else "LLM call failed")
