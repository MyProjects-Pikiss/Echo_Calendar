# AI Backend Implementation Checklist

This repository does not contain the production backend. Use this checklist to implement and verify the three required endpoints.

## Required endpoints

- `POST /ai/input-interpret`
- `POST /ai/search-interpret`
- `POST /ai/refine-field`

See full schema examples in `docs/ai_api_contract.md`.

## Validation requirements

- Reject mode mismatch with stable JSON error payload.
- Reject missing transcript.
- Reject invalid field name for refine.
- Return `mode` in every success payload.

## Production requirements

- Store provider API keys only on the backend.
- Add per-user/IP rate limit.
- Add request logging with sensitive text masking.
- Add timeout/retry policy for upstream LLM calls.

## E2E verification flow

1. Launch backend locally.
2. Configure app `AI_API_BASE_URL_DEBUG`.
3. From microphone tab:
   - Input mode: speak intent and confirm suggestion dialog appears.
   - Search mode: speak query and verify filtered search results.
   - Refine mode: refine a field in edit dialog and verify only target field changes.
4. Force error response and confirm app falls back to local interpreter.
