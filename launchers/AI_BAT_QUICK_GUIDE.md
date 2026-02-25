# Echo Calendar AI 실행 가이드

실행 파일은 `launchers` 폴더에 모아뒀습니다.
아래 파일들로 AI 백엔드 실행/테스트를 빠르게 할 수 있습니다.

- `launchers\RUN_AI_BACKEND.bat`: 서버 실행
- `launchers\TEST_AI_BACKEND.bat`: 서버 상태 + API 컨트랙트 테스트
- `launchers\RUN_AND_TEST_AI_BACKEND.bat`: 서버 실행 + 테스트를 연속 수행
- `launchers\AI_ENV_PATH.txt`: 외부 env 파일 경로 설정 파일
- `launchers\AI_ENV_TEMPLATE.env`: 외부 env 파일 내용 템플릿

## 1) 사전 준비 (최초 1회)

아래 설정은 `RUN_AND_TEST_AI_BACKEND.bat` 포함 모든 실행 방식의 공통 선행 조건입니다.

1. `launchers\AI_ENV_PATH.txt`를 열고 `OPENAI_API_KEY_FILE_PATH=...` 경로를 정합니다.
   - 기본값: `%USERPROFILE%\.echo_calendar_ai.env`
2. 위 경로에 env 파일을 수동 생성합니다. (프로젝트 폴더 밖 권장)
3. `launchers\AI_ENV_TEMPLATE.env` 내용을 복사해 붙여넣습니다.
4. `OPENAI_API_KEY=sk-xxxx`를 실제 키로 바꿉니다.

보안 권장:
- 실제 키 파일(`.echo_calendar_ai.env`)은 프로젝트 폴더 밖에 유지
- Git에는 `launchers\AI_ENV_PATH.txt`(경로 정보)와 `launchers\AI_ENV_TEMPLATE.env`(키 없는 템플릿)만 포함

## 2) 가장 쉬운 방법 (권장)

`launchers\RUN_AND_TEST_AI_BACKEND.bat` 더블클릭

- 새 창에서 서버를 켭니다.
- 현재 창에서 7초 대기 후 테스트를 실행합니다.
- 성공 시 `Server launch + contract test succeeded.` 출력
- 서버 초기 설치가 오래 걸리면 첫 실행에서 테스트가 먼저 실패할 수 있습니다.
  - 이 경우 `RUN_AND_TEST_AI_BACKEND.bat`를 한 번 더 실행하거나,
  - 아래 3번의 분리 실행 방법으로 진행하세요.

## 3) 분리 실행 방법

### 2-1. 서버만 실행

`launchers\RUN_AI_BACKEND.bat` 더블클릭

사전 준비(1번) 완료 후 실행하면 됩니다.

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

- `Env file not found`:
  - `launchers\AI_ENV_PATH.txt`의 경로가 실제 파일 위치와 같은지 확인
  - 해당 경로 파일을 수동 생성하고 `launchers\AI_ENV_TEMPLATE.env`를 복사
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

## 7) 원격 AI 개념 (참고)

앱의 AI 해석은 2가지 경로로 동작합니다.

- 원격 AI(backend 경유):
  - 앱 -> `backend` 서버 -> OpenAI API -> 앱 순서로 응답
  - 입력/검색/필드보완 정확도를 높이는 실제 LLM 경로
- 로컬 fallback:
  - 서버 실패/미설정 시 앱 내부 규칙 해석기로 자동 대체
