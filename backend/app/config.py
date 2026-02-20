from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    host: str = os.getenv("HOST", "0.0.0.0")
    port: int = int(os.getenv("PORT", "8088"))
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "").strip()
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-4.1-mini").strip()
    llm_timeout_seconds: float = float(os.getenv("LLM_TIMEOUT_SECONDS", "12"))
    llm_max_retries: int = int(os.getenv("LLM_MAX_RETRIES", "1"))
    rate_limit_per_minute: int = int(os.getenv("RATE_LIMIT_PER_MINUTE", "60"))
    enable_local_fallback: bool = os.getenv("ENABLE_LOCAL_FALLBACK", "true").lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


settings = Settings()
