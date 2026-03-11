from __future__ import annotations

import argparse
from pathlib import Path
import re


SYNC_KEYS = ("APP_LATEST_VERSION_CODE", "APP_LATEST_VERSION_NAME")
RUNTIME_VERSION_FILE = "app_version.env"


def parse_key_values(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        return values

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def update_env_file(path: Path, updates: dict[str, str]) -> bool:
    if not path.exists():
        raise FileNotFoundError(path)

    lines = path.read_text(encoding="utf-8").splitlines()
    pending = dict(updates)
    changed = False
    output: list[str] = []

    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            output.append(line)
            continue

        key, current = line.split("=", 1)
        env_key = key.strip()
        if env_key not in pending:
            output.append(line)
            continue

        next_value = pending.pop(env_key)
        current_value = current.strip()
        output.append(f"{env_key}={next_value}")
        if current_value != next_value:
            changed = True

    if pending:
        if output and output[-1].strip():
            output.append("")
        for env_key, env_value in pending.items():
            output.append(f"{env_key}={env_value}")
            changed = True

    if changed:
        path.write_text("\n".join(output) + "\n", encoding="utf-8")
    return changed


def resolve_external_env_path(server_dir: Path) -> Path | None:
    env_path = server_dir / ".env"
    values = parse_key_values(env_path)
    raw = values.get("BACKEND_EXTERNAL_ENV_PATH", "").strip().strip('"').strip("'")
    if not raw:
        return None
    win_match = re.match(r"^([A-Za-z]):[\\/](.*)$", raw)
    if win_match:
        drive = win_match.group(1).lower()
        suffix = win_match.group(2).replace("\\", "/")
        return Path(f"/mnt/{drive}/{suffix}")
    return Path(raw).expanduser()


def write_runtime_version_file(path: Path, values: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"{key}={value}" for key, value in values.items()]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Sync backend version env values from app/APP_CLIENT_CONFIG.txt."
    )
    parser.add_argument(
        "--sync-min-supported",
        action="store_true",
        help="Also set APP_MIN_SUPPORTED_VERSION_CODE to APP_VERSION_CODE.",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    app_config_path = repo_root / "app" / "APP_CLIENT_CONFIG.txt"
    server_dir = repo_root / "server"

    app_config = parse_key_values(app_config_path)
    version_code = app_config.get("APP_VERSION_CODE", "").strip()
    version_name = app_config.get("APP_VERSION_NAME", "").strip()

    if not version_code.isdigit() or int(version_code) <= 0:
        raise SystemExit(f"Invalid APP_VERSION_CODE in {app_config_path}")
    if not version_name:
        raise SystemExit(f"Missing APP_VERSION_NAME in {app_config_path}")

    updates = {
        "APP_LATEST_VERSION_CODE": version_code,
        "APP_LATEST_VERSION_NAME": version_name,
    }
    if args.sync_min_supported:
        updates["APP_MIN_SUPPORTED_VERSION_CODE"] = version_code

    target_paths = [server_dir / "docker.defaults.env"]
    external_env_path = resolve_external_env_path(server_dir)
    if external_env_path is not None:
        target_paths.append(external_env_path)

    runtime_source = external_env_path if external_env_path is not None else target_paths[0]
    runtime_values = parse_key_values(runtime_source)
    min_supported = runtime_values.get("APP_MIN_SUPPORTED_VERSION_CODE", version_code).strip()
    if not min_supported.isdigit() or int(min_supported) <= 0:
        min_supported = version_code

    for target_path in target_paths:
        try:
            changed = update_env_file(target_path, updates)
            status = "updated" if changed else "already-synced"
            print(f"[sync-app-version] {status}: {target_path}")
        except FileNotFoundError:
            print(f"[sync-app-version] skipped-missing: {target_path}")

    runtime_file_path = server_dir / "downloads" / RUNTIME_VERSION_FILE
    runtime_payload = {
        "APP_LATEST_VERSION_CODE": version_code,
        "APP_LATEST_VERSION_NAME": version_name,
        "APP_MIN_SUPPORTED_VERSION_CODE": min_supported,
    }
    write_runtime_version_file(runtime_file_path, runtime_payload)
    print(f"[sync-app-version] runtime-version: {runtime_file_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
