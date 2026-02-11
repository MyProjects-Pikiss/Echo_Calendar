# Echo Calendar AI API Contract (v1)

This document defines the server contract required by the Android app for microphone AI features.

## Common Rules

- Method: `POST`
- Content-Type: `application/json`
- Authorization: optional `Bearer <token>` (sent when `AI_API_KEY` is configured)
- Every success response must include `mode`.
- `mode` mismatch is treated as failure in the client.

## 1) Input interpretation

- Path: `/ai/input-interpret`
- Request:

```json
{
  "mode": "input",
  "transcript": "내일 9시 회의",
  "selectedDate": "2026-02-11"
}
```

- Response:

```json
{
  "mode": "input",
  "date": "2026-02-11",
  "summary": "회의",
  "time": "09:00",
  "categoryId": "work",
  "placeText": "",
  "body": "내일 9시 회의",
  "labels": ["팀"],
  "missingRequired": []
}
```

## 2) Search interpretation

- Path: `/ai/search-interpret`
- Request:

```json
{
  "mode": "search",
  "transcript": "지난주 병원 일정 찾아줘"
}
```

- Response:

```json
{
  "mode": "search",
  "query": "병원 일정",
  "dateFrom": "2026-02-01",
  "dateTo": "2026-02-07",
  "categoryIds": ["health"]
}
```

## 3) Field refinement

- Path: `/ai/refine-field`
- Request:

```json
{
  "mode": "refine",
  "transcript": "3시 30분으로 바꿔줘",
  "field": "time",
  "currentValue": "09:00",
  "selectedDate": "2026-02-11"
}
```

- Response:

```json
{
  "mode": "refine",
  "field": "time",
  "value": "15:30",
  "missingRequired": []
}
```

## Error Response Recommendation

Use a stable structure so app logs can distinguish failures:

```json
{
  "mode": "search",
  "errorCode": "INVALID_SCHEMA",
  "message": "query is required"
}
```

> Client behavior: if server call fails, app falls back to local rule-based parsing.
