# Echo Calendar Server 실행 가이드

실행 파일은 `server` 폴더에 모아뒀습니다.
아래 파일들로 서버 실행/테스트/배포 준비를 빠르게 할 수 있습니다.

- `server\RUN_SERVER.bat`: 서버 실행
- `server\TEST_SERVER.bat`: 서버 상태 + API 컨트랙트 테스트
- `server\RUN_AND_TEST_SERVER.bat`: 서버 실행 + 테스트를 연속 수행
- `server\SYNC_HOLIDAYS_NOW.bat`: 휴일 데이터 즉시 동기화(수동 실행)
- `server\SERVER_ENV_PATH.txt`: 외부 env 파일 경로 설정 파일
- `server\SERVER_ENV_TEMPLATE.env`: 외부 env 파일 내용 템플릿
- `CLOUD_DEPLOY_GUIDE.md`: Render 기준 클라우드 배포 가이드

## 1) 사전 준비 (최초 1회)

아래 설정은 `RUN_AND_TEST_SERVER.bat` 포함 모든 실행 방식의 공통 선행 조건입니다.

1. `server\SERVER_ENV_PATH.txt`를 열고 `OPENAI_API_KEY_FILE_PATH=...` 경로를 정합니다.
   - 기본값: `%USERPROFILE%\SERVER_ENV_TEMPLATE.env`
2. `server\SERVER_ENV_TEMPLATE.env` 파일을 복제(복사-붙여넣기)해서 위 경로에 둡니다. (프로젝트 폴더 밖 권장)
3. AI 기능까지 쓰려면 `OPENAI_API_KEY=sk-xxxx`를 실제 키로 바꿉니다.
4. 휴일 동기화를 쓰려면 같은 파일에 `KOREA_HOLIDAY_API_KEY=`를 공공데이터포털 키로 채웁니다.
5. 다시 `server\RUN_SERVER.bat` 또는 `RUN_AND_TEST_SERVER.bat` 실행

보안 권장:
- 실제 키 파일(`SERVER_ENV_TEMPLATE.env`)은 프로젝트 폴더 밖에 유지
- Git에는 `server\SERVER_ENV_PATH.txt`(경로 정보)와 `server\SERVER_ENV_TEMPLATE.env`(키 없는 템플릿)만 포함

## 2) 가장 쉬운 방법 (권장)

`server\RUN_AND_TEST_SERVER.bat` 더블클릭

- 새 창에서 서버를 켭니다.
- 현재 창에서 7초 대기 후 테스트를 실행합니다.
- 성공 시 `Server launch + contract test succeeded.` 출력
- 서버 초기 설치가 오래 걸리면 첫 실행에서 테스트가 먼저 실패할 수 있습니다.
  - 이 경우 `RUN_AND_TEST_SERVER.bat`를 한 번 더 실행하거나,
  - 아래 3번의 분리 실행 방법으로 진행하세요.

## 3) 분리 실행 방법

### 2-1. 서버만 실행

`server\RUN_SERVER.bat` 더블클릭

사전 준비(1번) 완료 후 실행하면 됩니다.

### 2-2. 테스트만 실행

서버가 켜진 상태에서 `server\TEST_SERVER.bat` 더블클릭

- `http://127.0.0.1:8088/health` 확인
- `server\tools\check_ai_backend_contract.py` 실행
- 성공 시 `[PASS] Contract check passed.`

## 4) 앱 연결 기준

- Android 에뮬레이터 debug 실행 시 기본 주소는 `http://10.0.2.2:8088`
- 서버 창은 테스트 중 닫지 말고 유지

## 4-1) 실기기 연결 기준

- 같은 Wi-Fi 개발 환경에서는 PC IP 사용:
  - 예: `http://192.168.0.12:8088`
- 실서비스(모바일 배포)에서는 HTTPS 서버 주소 사용:
  - 예: `https://<your-render-domain>`

## 5) 원격 AI 동작 확인

앱 로그(`AiAssistantService`)에서 아래를 확인하세요.

- 원격 성공: `remote_success action=input|search|refine.*`
- 원격 실패 후 대체: `remote_failure_fallback action=... reason=...`

## 5-1) 휴일 API 동작 확인

- 앱은 backend의 `GET /holidays?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`를 호출합니다.
- backend는 서버 내부 DB(`HOLIDAY_DB_PATH`)를 먼저 조회하고, 비어 있으면 동기화를 수행합니다.
- backend는 env의 `KOREA_HOLIDAY_API_KEY`로 공공데이터포털 특일 API를 조회해 DB를 갱신합니다.
- 서버 로그에서 아래를 확인하세요.
  - 성공: `holiday_startup_sync_success ...`, `holiday_daily_sync_success ...`
  - 실패: `holiday_fetch_failure reason=...`

## 5-2) 서버 동기화 정책

- 최초 실행(bootstrap): `HOLIDAY_BOOTSTRAP_START_DATE` ~ `오늘 + HOLIDAY_BOOTSTRAP_FORWARD_YEARS`
- 이후 일일 동기화: `오늘 ± HOLIDAY_DAILY_WINDOW_YEARS`
- 수동 즉시 동기화: `server\SYNC_HOLIDAYS_NOW.bat`

## 6) 자주 발생하는 문제

- `Env file not found`:
  - `server\SERVER_ENV_PATH.txt`의 경로가 실제 파일 위치와 같은지 확인
  - `server\SERVER_ENV_TEMPLATE.env`를 복제(복사-붙여넣기)해 해당 경로에 배치
- `OPENAI_API_KEY` 비어 있음:
  - `server\SERVER_ENV_PATH.txt`에 적힌 키 파일을 수정 후 재실행
- 키 파일 경로를 바꾸고 싶음:
  - `server\SERVER_ENV_PATH.txt`에서 `OPENAI_API_KEY_FILE_PATH=원하는경로`로 변경
- `Backend is not reachable`:
  - 서버 미실행 또는 포트 충돌
  - `server\RUN_SERVER.bat`부터 실행
- 테스트 실패:
  - 서버 창 로그 확인
  - 필요 시 `server\SERVER_ENV_PATH.txt`에 지정한 키 파일의 값 확인

## 7) 원격 AI 개념 (참고)

앱의 AI 해석은 2가지 경로로 동작합니다.

- 원격 AI(backend 경유):
  - 앱 -> `backend` 서버 -> OpenAI API -> 앱 순서로 응답
  - 입력/검색/필드보완 정확도를 높이는 실제 LLM 경로
- 로컬 fallback:
  - 서버 실패/미설정 시 앱 내부 규칙 해석기로 자동 대체

## 8) 클라우드 구축 요약 (Render)

상세는 `CLOUD_DEPLOY_GUIDE.md`를 따르세요.

빠른 요약:
1. GitHub에 현재 저장소 push
2. Render에서 Web Service 생성 (`backend` 디렉토리 배포)
3. 환경변수 설정
   - 필수: `OPENAI_API_KEY`, `KOREA_HOLIDAY_API_KEY`
4. 배포 후 확인
   - `/health`
   - `/holidays?startDate=...&endDate=...`
5. 앱의 서버 URL을 Render HTTPS 주소로 변경
