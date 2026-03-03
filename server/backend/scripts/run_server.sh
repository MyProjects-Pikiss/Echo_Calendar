#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

normalize_env_path() {
  local raw_path="$1"
  local win_userprofile

  raw_path="${raw_path%\"}"
  raw_path="${raw_path#\"}"
  raw_path="${raw_path//$'\r'/}"

  win_userprofile="$(cmd.exe /C "echo %USERPROFILE%" 2>/dev/null | tr -d '\r' | tail -n1 || true)"
  if [ -n "$win_userprofile" ]; then
    raw_path="${raw_path//%USERPROFILE%/$win_userprofile}"
  fi
  raw_path="${raw_path//%HOMEDRIVE%/${HOMEDRIVE:-}}"
  raw_path="${raw_path//%HOMEPATH%/${HOMEPATH:-}}"

  if [[ "$raw_path" =~ ^[A-Za-z]:\\ ]]; then
    if command -v wslpath >/dev/null 2>&1; then
      raw_path="$(wslpath "$raw_path")"
    else
      local drive rest
      drive="$(printf '%s' "$raw_path" | cut -c1 | tr '[:upper:]' '[:lower:]')"
      rest="${raw_path:2}"
      rest="${rest//\\//}"
      raw_path="/mnt/${drive}${rest}"
    fi
  fi

  printf '%s' "$raw_path"
}

load_openai_env_from_path_config() {
  local path_config="../SERVER_ENV_PATH.txt"
  [ -f "$path_config" ] || return 1
  local raw_path
  raw_path="$(
    awk '
      {
        line=$0
        sub(/\r$/, "", line)
        if (line ~ /^[[:space:]]*#/ || line ~ /^[[:space:]]*$/) next
        if (index(line, "=") > 0) {
          key=line
          sub(/=.*/, "", key)
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", key)
          val=line
          sub(/^[^=]*=/, "", val)
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", val)
          if (key == "OPENAI_API_KEY_FILE_PATH" || key == "API_KEY_FILE_PATH" || key == "OPENAI_ENV_FILE") {
            print val
            exit
          }
        } else {
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
          print line
          exit
        }
      }
    ' "$path_config"
  )"
  [ -n "$raw_path" ] || return 1
  export OPENAI_ENV_FILE
  OPENAI_ENV_FILE="$(normalize_env_path "$raw_path")"
  echo "[INFO] OPENAI_ENV_FILE loaded from $path_config -> $OPENAI_ENV_FILE"
}

if [ -z "${OPENAI_ENV_FILE:-}" ]; then
  load_openai_env_from_path_config || true
fi

if [ ! -d ".venv" ]; then
  python3 -m venv .venv
fi

source .venv/bin/activate
pip install -r requirements.txt

exec uvicorn app.main:app --host "${HOST:-0.0.0.0}" --port "${PORT:-8088}" --reload
