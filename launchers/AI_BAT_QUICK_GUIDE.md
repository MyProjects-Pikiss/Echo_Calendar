# Echo Calendar AI 실행 가이드

실행 파일은 `launchers` 폴더에 모아뒀습니다.
아래 3개 파일로 AI 백엔드 실행/테스트를 빠르게 할 수 있습니다.

- `launchers\RUN_AI_BACKEND.bat`: 서버 실행
- `launchers\TEST_AI_BACKEND.bat`: 서버 상태 + API 컨트랙트 테스트
- `launchers\RUN_AND_TEST_AI_BACKEND.bat`: 서버 실행 + 테스트를 연속 수행
- `launchers\AI_ENV_PATH.txt`: API 키 파일 경로 설정 파일

## 1) 원격 AI가 무엇인가요?

앱의 AI 해석은 2가지 경로로 동작합니다.

- 원격 AI(backend 경유):
  - 앱 -> `backend` 서버 -> OpenAI API -> 앱 순서로 응답
  - 입력/검색/필드보완 정확도를 높이는 실제 LLM 경로
- 로컬 fallback:
  - 서버 실패/미설정 시 앱 내부 규칙 해석기로 자동 대체

## 2) 가장 쉬운 방법 (권장)

`launchers\RUN_AND_TEST_AI_BACKEND.bat` 더블클릭

- 새 창에서 서버를 켭니다.
- 현재 창에서 테스트를 실행합니다.
- 성공 시 `Server launch + contract test succeeded.` 출력

## 3) 분리 실행 방법

### 2-1. 서버만 실행

`launchers\RUN_AI_BACKEND.bat` 더블클릭

처음 실행 시:
- `launchers\AI_ENV_PATH.txt`에서 키 파일 경로를 읽음
- 경로 파일이 없으면 자동 생성됨 (기본값: `%USERPROFILE%\.echo_calendar_ai.env`)
- 해당 경로에 키 파일이 없으면 자동 생성
- 메모장이 열리면 `OPENAI_API_KEY` 입력 후 저장
- 다시 `launchers\RUN_AI_BACKEND.bat` 실행

### 2-2. 테스트만 실행

서버가 켜진 상태에서 `launchers\TEST_AI_BACKEND.bat` 더블클릭

- `http://127.0.0.1:8088/health` 확인
- `tools/check_ai_backend_contract.py` 실행
- 성공 시 `[PASS] Contract check passed.`

## 4) 앱 연결 기준

- Android 에뮬레이터 debug 실행 시 기본 주소는 `http://10.0.2.2:8088`
- 서버 창은 테스트 중 닫지 말고 유지

## 5) 원격 AI 동작 확인

앱 로그(`AiAssistantService`)에서 아래를 확인하세요.

- 원격 성공: `remote_success action=input|search|refine.*`
- 원격 실패 후 대체: `remote_failure_fallback action=... reason=...`

## 6) 자주 발생하는 문제

- `OPENAI_API_KEY` 비어 있음:
  - `launchers\AI_ENV_PATH.txt`에 적힌 키 파일을 수정 후 재실행
- 키 파일 경로를 바꾸고 싶음:
  - `launchers\AI_ENV_PATH.txt`에서 `OPENAI_API_KEY_FILE_PATH=원하는경로`로 변경
- `Backend is not reachable`:
  - 서버 미실행 또는 포트 충돌
  - `launchers\RUN_AI_BACKEND.bat`부터 실행
- 테스트 실패:
  - 서버 창 로그 확인
  - 필요 시 `launchers\AI_ENV_PATH.txt`에 지정한 키 파일의 값 확인
