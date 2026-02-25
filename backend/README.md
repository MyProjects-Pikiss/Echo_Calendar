# Echo Calendar AI Backend (MVP)

This folder contains a minimal FastAPI gateway for Echo Calendar AI.

## Endpoints

- `POST /ai/input-interpret`
- `POST /ai/search-interpret`
- `POST /ai/refine-field`
- `GET /health`

## Run

### Quick start (recommended)

```bash
cd backend
cp .env.example ~/.echo_calendar_ai.env
# edit ~/.echo_calendar_ai.env and set OPENAI_API_KEY
OPENAI_ENV_FILE=~/.echo_calendar_ai.env ./scripts/run_server.sh
```

Env file loading order:
- `OPENAI_ENV_FILE` (if set)
- `~/.echo_calendar_ai.env`

System environment variables still take precedence over file values.

### Manual start

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8088 --reload
```

## Verify contract

In a second terminal from repository root:

```bash
python3 tools/check_ai_backend_contract.py --base-url http://127.0.0.1:8088
```

Or run backend tests:

```bash
cd backend
pytest -q
```

## Environment variables

- `OPENAI_API_KEY` (optional for local fallback-only mode)
- `OPENAI_MODEL` (default: `gpt-5-mini`)
- `LLM_TIMEOUT_SECONDS` (default: `12`)
- `LLM_MAX_RETRIES` (default: `1`)
- `RATE_LIMIT_PER_MINUTE` (default: `60`)
- `ENABLE_LOCAL_FALLBACK` (default: `true`)

## Notes

- If `OPENAI_API_KEY` is missing, endpoints still work via local fallback parser.
- LLM responses are validated against contract schema before returning.
- Error payload shape is stable:

```json
{
  "mode": "search",
  "errorCode": "MISSING_TRANSCRIPT",
  "message": "transcript is required"
}
```
