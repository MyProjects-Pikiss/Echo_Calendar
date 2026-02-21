from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _load_local_env_files() -> None:
    """Load backend/.env and backend/.env.local without overriding OS env vars."""
    original_env_keys = set(os.environ.keys())
    backend_root = Path(__file__).resolve().parents[1]
    env_files = (backend_root / ".env", backend_root / ".env.local")

    for env_file in env_files:
        if not env_file.exists():
            continue
        for raw_line in env_file.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            if not key or key in original_env_keys:
                continue
            value = value.strip()
            if value and len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", "\""}:
                value = value[1:-1]
            os.environ[key] = value


_load_local_env_files()


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
