# Echo Calendar 통합 명세서 (v1.4)

작성일: 2026-02-27  
문서 정책: 본 문서가 제품/기능/API/운영/검증의 단일 기준 문서(Single Source)다.  
DB 테이블/인덱스/제약은 별도 문서 `Echo Calendar_DB 스키마 명세서(v1.2).md`를 기준으로 한다.

---

## 1) 프로젝트 개요

Echo Calendar는 개인 일정을 안정적으로 기록하고 다시 찾기 위한 캘린더 앱이다.

- 오프라인 우선(offline-first)
- 온라인 AI는 보조 기능(online-optional)
- 데이터 변경은 항상 사용자 확인 기반

---

## 2) 동작 모드

### 2.1 오프라인 기본 모드(필수)

- 로컬 Room DB가 정본(Source of Truth)
- 월간 캘린더 조회
- 이벤트 CRUD
- 로컬 Full-Text Search(FTS)

### 2.2 온라인 AI 보조 모드(선택)

- 음성(STT)/텍스트 입력 해석
- AI 입력 제안, AI 검색 해석, 필드 보완
- AI는 DB를 직접 갱신하지 않음(제안만 생성)
- 최종 반영은 사용자 확인 후 수행

### 2.3 실패/복구 원칙

- 원격 AI 실패 시 로컬 규칙 fallback 수행
- 기존 로컬 데이터 무결성에 영향 없음

---

## 3) 핵심 기능 범위

### 3.1 캘린더/기록

- 월간 캘린더 화면
- 날짜별 이벤트 목록
- 텍스트 입력 및 수정/삭제
- 카테고리 기반 분류

### 3.2 검색

- 로컬 FTS 기반 검색
- 검색 결과 선택 시 해당 날짜로 이동
- AI 검색 시 해석 결과를 즉시 필터에 반영해 목록 조회까지 수행

### 3.3 알림

- 이벤트 기반 알림(EventAlarm)
- 로컬 스케줄링 처리

---

## 4) 데이터 변경 원칙

- 사용자/AI 기원과 무관하게 동일 규칙 적용
- 이벤트 생성/수정/삭제는 사용자 확인 후 반영
- AI는 변경 제안만 생성

---

## 5) 데이터 모델 규칙(운영)

DB 구조 자체는 `Echo Calendar_DB 스키마 명세서(v1.2).md` 기준.

### 5.1 Label 기본 규칙

- `Label.name` 기준 조회
- 존재하면 재사용, 없으면 신규 생성

### 5.2 Label 확장 정책

- 기존 라벨 재사용 우선
- 유사 라벨이면 기존 라벨로 대체 가능
  - 예: `병원검진` -> `병원 검진`
- 기존으로 대체하기 어려운 경우에만 신규 생성
- 라벨은 검색 재사용성 높은 범용 표현 지향
- 이벤트당 라벨 수: `0..5`

### 5.3 검색 라벨 매칭 정책

- 라벨 필터는 다중값 허용
- 현재 매칭 기준은 AND(입력 라벨 모두 포함)

---

## 6) AI API 계약

공통:

- Method: `POST`
- Content-Type: `application/json`
- 성공 응답은 `mode` 필수
- `mode` 불일치는 실패 처리

### 6.1 Input Interpretation

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

### 6.2 Search Interpretation

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
  "categoryIds": ["medical"],
  "labels": ["병원 검진"]
}
```

Search 응답 유효성 규칙:

- 아래 중 하나 이상 만족하면 유효
1. `query`가 비어있지 않음
2. `dateFrom/dateTo/categoryIds/labels` 중 하나 이상 존재

즉, filters-only(`query=""`) 응답을 허용한다.

### 6.3 Refine Field

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

### 6.4 에러 응답 권장 형식

```json
{
  "mode": "search",
  "errorCode": "INVALID_SCHEMA",
  "message": "invalid search payload"
}
```

---

## 7) 백엔드 구현 요구사항

- 필수 엔드포인트:
  - `POST /ai/input-interpret`
  - `POST /ai/search-interpret`
  - `POST /ai/refine-field`
- 입력 검증:
  - mode mismatch 거절
  - transcript 누락 거절
  - refine field 유효성 검증
- 운영 요구:
  - Provider API key는 서버만 보관
  - 사용자/IP 단위 rate limit
  - 민감 텍스트 마스킹 로그
  - upstream LLM timeout/retry 정책

---

## 8) 클라이언트/배포 설정

설정 위치:

- `~/.gradle/gradle.properties` 또는 프로젝트 `local.properties`(gitignored)

예시:

```properties
AI_API_BASE_URL=https://your-api.example.com
AI_API_KEY=your-secret
AI_API_BASE_URL_DEBUG=https://dev-api.example.com
AI_API_BASE_URL_RELEASE=https://prod-api.example.com
AI_API_KEY_DEBUG=
AI_API_KEY_RELEASE=
AI_API_TIMEOUT_MS=12000
AI_SEND_CLIENT_API_KEY_DEBUG=false
AI_SEND_CLIENT_API_KEY_RELEASE=false
AI_REQUIRE_HTTPS_DEBUG=false
AI_REQUIRE_HTTPS_RELEASE=true
```

보안 권장:

- Release는 백엔드 경유 + HTTPS
- `AI_SEND_CLIENT_API_KEY_RELEASE=false`

---

## 9) 로컬 검증 절차

### 9.1 로컬 계약 스텁 실행

```bash
python server/tools/ai_contract_server.py
```

### 9.2 계약 체크 실행

```bash
python server/tools/check_ai_backend_contract.py --base-url http://127.0.0.1:8088
```

### 9.3 E2E 스모크

- AI 입력: transcript -> suggestion popup
- AI 검색: transcript -> query/filters -> 결과 목록
- 필드 보완: target field only
- 원격 실패 시 fallback 메시지
- labels-only 검색 응답 동작 확인
- 라벨 유사 대체/최대 5개 제한 확인

---

## 10) 운영 관측

- 앱 로그(`AiAssistantService`) 확인:
  - `remote_success action=...`
  - `remote_failure_fallback action=... reason=...`
- input/search/refine 각각 최소 1회 원격 성공 로그 확인

---

## 11) 빌드 환경 주의

- Gradle/JDK는 17 또는 21 사용 권장
- JDK 25 환경에서는 일부 스택에서 호환 이슈가 발생할 수 있음
