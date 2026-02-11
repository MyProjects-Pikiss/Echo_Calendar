# Required Tasks (Minimum to ship AI flow)

This checklist focuses only on blocking items so work can finish within limited usage.

## A. Build/CI unblock (must)

1. Use JDK 21 in CI (already configured in `.github/workflows/android-ci.yml`).
2. Locally, run Gradle with JDK 17/21 (not JDK 25).

## B. Backend contract verification (must)

1. Start backend or local stub.
2. Run contract check script:

```bash
python tools/check_ai_backend_contract.py --base-url http://127.0.0.1:8088
```

3. If failed, fix endpoint schema to match `docs/ai_api_contract.md`.

## C. Release configuration (must)

Set CI secrets/properties:

- `AI_API_BASE_URL_RELEASE`
- `AI_API_TIMEOUT_MS`
- optional: `AI_API_KEY_RELEASE`
- keep release policy: no client key forwarding, HTTPS only.

## D. Final app smoke (must)

- AI 입력: transcript -> suggestion popup
- AI 검색: transcript -> query+filters
- 필드 보완: transcript -> target field only
- 원격 실패시 로컬 fallback 메시지 확인


## E. Remote usage observability (must)

- Check app logs for `AiAssistantService` events:
  - `remote_success action=...`
  - `remote_failure_fallback action=... reason=...`
- During final smoke test, confirm at least one remote success for input/search/refine.
