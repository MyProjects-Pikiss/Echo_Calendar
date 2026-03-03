from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _parse_env_file(env_file: Path, original_env_keys: set[str]) -> None:
    if not env_file.exists():
        return
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


def _load_local_env_files() -> None:
    """Load user-local env files without overriding existing OS environment variables."""
    original_env_keys = set(os.environ.keys())
    external_env = os.getenv("OPENAI_ENV_FILE", "").strip()

    env_files: list[Path] = []
    if external_env:
        env_files.append(Path(external_env).expanduser())
    env_files.append(Path.home() / ".echo_calendar_ai.env")

    for env_file in env_files:
        _parse_env_file(env_file, original_env_keys)


_load_local_env_files()


@dataclass(frozen=True)
class Settings:
    server_mode: str = os.getenv("SERVER_MODE", "all").strip().lower()
    host: str = os.getenv("HOST", "0.0.0.0")
    port: int = int(os.getenv("PORT", "8088"))
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "").strip()
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-5-nano").strip()
    llm_timeout_seconds: float = float(os.getenv("LLM_TIMEOUT_SECONDS", "12"))
    llm_max_retries: int = int(os.getenv("LLM_MAX_RETRIES", "1"))
    llm_deadline_seconds: float = float(os.getenv("LLM_DEADLINE_SECONDS", "15"))
    rate_limit_per_minute: int = int(os.getenv("RATE_LIMIT_PER_MINUTE", "60"))
    enable_local_fallback: bool = os.getenv("ENABLE_LOCAL_FALLBACK", "true").lower() in {
        "1",
        "true",
        "yes",
        "on",
    }
    holiday_api_key: str = os.getenv("KOREA_HOLIDAY_API_KEY", "").strip()
    holiday_api_base_url: str = os.getenv(
        "KOREA_HOLIDAY_API_BASE_URL",
        "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo",
    ).strip()
    holiday_api_timeout_seconds: float = float(os.getenv("KOREA_HOLIDAY_API_TIMEOUT_SECONDS", "8"))
    holiday_api_max_concurrency: int = int(os.getenv("KOREA_HOLIDAY_API_MAX_CONCURRENCY", "6"))
    holiday_db_path: str = os.getenv("HOLIDAY_DB_PATH", "data/holiday_cache.db").strip()
    usage_db_path: str = os.getenv("USAGE_DB_PATH", "data/usage.db").strip()
    usage_dashboard_owner_username: str = os.getenv("USAGE_DASHBOARD_OWNER_USERNAME", "").strip()
    usage_dashboard_access_key: str = os.getenv("USAGE_DASHBOARD_ACCESS_KEY", "").strip()
    usage_admin_username: str = os.getenv("USAGE_ADMIN_USERNAME", "").strip()
    usage_admin_password: str = os.getenv("USAGE_ADMIN_PASSWORD", "").strip()
    holiday_sync_enabled: bool = os.getenv("HOLIDAY_SYNC_ENABLED", "true").lower() in {
        "1",
        "true",
        "yes",
        "on",
    }
    holiday_bootstrap_start_date: str = os.getenv("HOLIDAY_BOOTSTRAP_START_DATE", "1970-01-01").strip()
    holiday_bootstrap_forward_years: float = float(os.getenv("HOLIDAY_BOOTSTRAP_FORWARD_YEARS", "5"))
    holiday_daily_window_years: float = float(os.getenv("HOLIDAY_DAILY_WINDOW_YEARS", "1"))


settings = Settings()
