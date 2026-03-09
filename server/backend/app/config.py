from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _expand_env_path(raw: str) -> str:
    value = raw.strip().strip("\"").strip("'")
    userprofile = os.environ.get("USERPROFILE", "")
    homedrive = os.environ.get("HOMEDRIVE", "")
    homepath = os.environ.get("HOMEPATH", "")
    if userprofile:
        value = value.replace("%USERPROFILE%", userprofile)
    if homedrive:
        value = value.replace("%HOMEDRIVE%", homedrive)
    if homepath:
        value = value.replace("%HOMEPATH%", homepath)
    return os.path.expandvars(value)


def _parse_env_file(env_file: Path, original_env_keys: set[str]) -> None:
    if not env_file.exists():
        return
    for raw_line in env_file.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip().lstrip("\ufeff")
        if not key:
            continue
        if key in original_env_keys:
            existing_value = os.getenv(key, "").strip()
            # Keep explicit non-empty OS env values, but allow file values to fill empty ones.
            if existing_value:
                continue
        value = value.strip()
        if value and len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", "\""}:
            value = value[1:-1]
        os.environ[key] = value


def _load_local_env_files() -> None:
    """Load optional env files for the Docker-based server runtime."""
    original_env_keys = set(os.environ.keys())

    env_files: list[Path] = []

    external_env = os.getenv("OPENAI_ENV_FILE", "").strip()
    if external_env:
        env_files.append(Path(_expand_env_path(external_env)).expanduser())

    seen: set[str] = set()
    for env_file in env_files:
        normalized = str(env_file).strip()
        if not normalized:
            continue
        normalized_key = normalized.lower() if os.name == "nt" else normalized
        if normalized_key in seen:
            continue
        seen.add(normalized_key)
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
    llm_max_output_tokens: int = int(os.getenv("LLM_MAX_OUTPUT_TOKENS", "256"))
    rate_limit_per_minute: int = int(os.getenv("RATE_LIMIT_PER_MINUTE", "60"))
    block_probe_requests: bool = os.getenv("BLOCK_PROBE_REQUESTS", "true").lower() in {
        "1",
        "true",
        "yes",
        "on",
    }
    enable_local_fallback: bool = False
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
    kafka_enabled: bool = os.getenv("KAFKA_ENABLED", "false").lower() in {
        "1",
        "true",
        "yes",
        "on",
    }
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092").strip()
    kafka_usage_topic: str = os.getenv("KAFKA_USAGE_TOPIC", "usage-events").strip()
    kafka_usage_group_id: str = os.getenv("KAFKA_USAGE_GROUP_ID", "echo-calendar-usage-consumer").strip()
    kafka_client_id: str = os.getenv("KAFKA_CLIENT_ID", "echo-calendar-backend").strip()
    holiday_sync_enabled: bool = os.getenv("HOLIDAY_SYNC_ENABLED", "true").lower() in {
        "1",
        "true",
        "yes",
        "on",
    }
    holiday_bootstrap_start_date: str = os.getenv("HOLIDAY_BOOTSTRAP_START_DATE", "1970-01-01").strip()
    holiday_bootstrap_forward_years: float = float(os.getenv("HOLIDAY_BOOTSTRAP_FORWARD_YEARS", "5"))
    holiday_daily_window_years: float = float(os.getenv("HOLIDAY_DAILY_WINDOW_YEARS", "1"))
    app_latest_version_code: int = int(os.getenv("APP_LATEST_VERSION_CODE", "1"))
    app_latest_version_name: str = os.getenv("APP_LATEST_VERSION_NAME", "1.0").strip()
    app_min_supported_version_code: int = int(os.getenv("APP_MIN_SUPPORTED_VERSION_CODE", "1"))
    app_downloads_dir: str = os.getenv("APP_DOWNLOADS_DIR", "downloads").strip()
    app_apk_filename: str = os.getenv("APP_APK_FILENAME", "echo-calendar-latest.apk").strip()
    app_apk_download_url: str = os.getenv("APP_APK_DOWNLOAD_URL", "").strip()
    allow_signup: bool = os.getenv("ALLOW_SIGNUP", "false").lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


settings = Settings()
