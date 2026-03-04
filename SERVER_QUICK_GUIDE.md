# Echo Calendar Server 실행 가이드

실행 파일은 `server` 폴더에 모아뒀습니다.
아래 파일들로 서버 실행/동기화 준비를 빠르게 할 수 있습니다.

- `server\RUN_ALL_SERVERS.bat`: 통합 서버 실행 (권장)
- `server\RUN_BACKEND_COMMON.bat`: 공통 사전 준비만 실행 (내부용)
- `server\SYNC_HOLIDAYS_NOW.bat`: 휴일 데이터 즉시 동기화(수동 실행)
- `server\SYNC_HOLIDAYS_WINDOW_5Y.bat`: 오늘 기준 앞/뒤 5년 범위만 갱신
- `server\SERVER_ENV_PATH.txt`: 외부 env 파일 경로 설정 파일
- `server\SERVER_ENV_TEMPLATE.env`: 외부 env 파일 내용 템플릿

## 1) 사전 준비 (최초 1회)

아래 설정은 모든 실행 방식의 공통 선행 조건입니다.

1. `server\SERVER_ENV_PATH.txt`를 열고 `OPENAI_API_KEY_FILE_PATH=...` 경로를 정합니다.
   - 기본값: `%USERPROFILE%\SERVER_ENV_TEMPLATE.env`
2. `server\SERVER_ENV_TEMPLATE.env` 파일을 복제(복사-붙여넣기)해서 위 경로에 둡니다. (프로젝트 폴더 밖 권장)
3. AI 기능까지 쓰려면 `OPENAI_API_KEY=sk-xxxx`를 실제 키로 바꿉니다.
4. 휴일 동기화를 쓰려면 같은 파일에 `KOREA_HOLIDAY_API_KEY=`를 공공데이터포털 키로 채웁니다.
5. `server\RUN_BACKEND_COMMON.bat` 실행 (세팅/의존성 준비)
6. `server\RUN_ALL_SERVERS.bat` 실행 (통합 서버 시작)

보안 권장:
- 실제 키 파일(`SERVER_ENV_TEMPLATE.env`)은 프로젝트 폴더 밖에 유지
- Git에는 `server\SERVER_ENV_PATH.txt`(경로 정보)와 `server\SERVER_ENV_TEMPLATE.env`(키 없는 템플릿)만 포함

## 2) 서버 실행 방법

사전 준비(1번) 완료 후 아래 실행 파일을 사용합니다.

- 동시 실행(권장): `server\RUN_ALL_SERVERS.bat`
  - 통합 서버(`8088`)를 새 창으로 실행
- 통합 서버 엔드포인트: `/ai/*`, `/auth/*`, `/usage/*`, `/holidays`, `/health`, `/downloads/*`
- 회원가입 차단(임시 운영): env에 `ALLOW_SIGNUP=false` 설정 시 `/auth/signup`이 403으로 차단됩니다.
## 3) 앱 연결 기준

- 앱 기본 설정 파일: `app\APP_CLIENT_CONFIG.txt`
  - `SERVER_BASE_URL`, `APP_VERSION_CODE`, `APP_VERSION_NAME` 값을 앱 빌드에서 직접 읽습니다.
- Android 에뮬레이터 debug 실행 기본 주소는 `http://10.0.2.2:8088`
- 서버 창은 테스트 중 닫지 말고 유지
- 앱 버전 체크 API: `GET /app/version?currentVersionCode=<정수>`
  - `APP_LATEST_VERSION_CODE`, `APP_LATEST_VERSION_NAME`, `APP_MIN_SUPPORTED_VERSION_CODE`, `APP_APK_DOWNLOAD_URL` 값을 `SERVER_ENV_TEMPLATE.env`(실사용 env 파일)에서 설정하면 앱이 구버전에서 업데이트 안내를 표시합니다.

## 3-2) APK 서버 배포(서버 폴더 방식)

아래는 **도메인 + 서버 폴더**만으로 APK 업데이트 링크를 배포하는 기본 절차입니다.

1. 앱 릴리즈 APK를 빌드합니다.
   - 예: `app\app\build\outputs\apk\release\app-release.apk`
2. 서버 폴더의 `server\downloads\`에 APK를 복사합니다.
   - 권장 파일명: `echo-calendar-latest.apk`
3. 서버 env 파일(`SERVER_ENV_PATH.txt`로 지정한 실사용 env)에 아래 값을 맞춥니다.
   - `APP_DOWNLOADS_DIR=downloads`
   - `APP_APK_FILENAME=echo-calendar-latest.apk`
   - `APP_APK_DOWNLOAD_URL=` (비워두면 서버가 자동 URL 생성)
   - `APP_LATEST_VERSION_CODE=<새 버전 코드>`
   - `APP_LATEST_VERSION_NAME=<새 버전 이름>`
   - `APP_MIN_SUPPORTED_VERSION_CODE=<최소 지원 코드>`
4. `server\RUN_ALL_SERVERS.bat`로 서버를 재시작합니다.
5. 브라우저에서 APK 링크를 직접 확인합니다.
   - `https://<도메인>/downloads/echo-calendar-latest.apk`

참고:
- `APP_APK_DOWNLOAD_URL`를 직접 넣으면 그 링크를 우선 사용합니다.
- 비워두면 서버가 현재 요청 도메인 기준으로 `/downloads/<APP_APK_FILENAME>` URL을 자동 생성합니다.
- 새 배포 때는 같은 파일명을 덮어쓰기하면 링크를 바꾸지 않아도 됩니다.

## 3-1) 실기기 연결 기준

- 같은 Wi-Fi 개발 환경에서는 PC IP 사용:
  - 예: `http://192.168.0.12:8088`

## 4) 원격 AI 동작 확인

앱 로그(`AiAssistantService`)에서 아래를 확인하세요.

- 원격 성공: `remote_success action=input|search|refine.*`
- 원격 실패: `remote_failure action=... reason=...`

## 4-1) 휴일 API 동작 확인

- 앱은 backend의 `GET /holidays?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`를 호출합니다.
- backend는 서버 내부 DB(`HOLIDAY_DB_PATH`)를 조회만 합니다. (런타임 외부 호출 없음)
- DB 갱신은 `server\SYNC_HOLIDAYS_NOW.bat`으로 별도 수행합니다.
- 서버 로그에서 아래를 확인하세요.
  - 조회 실패: `holiday_fetch_failure reason=...`

## 4-2) 서버 동기화 정책

- 서버 실행(`RUN_ALL_SERVERS.bat`)은 휴일 동기화를 자동 시작하지 않습니다.
- 수동 즉시 동기화: `server\SYNC_HOLIDAYS_NOW.bat`
- 오늘 기준 앞/뒤 5년 갱신: `server\SYNC_HOLIDAYS_WINDOW_5Y.bat`

## 4-3) 사용량 웹 대시보드 접속

- 대시보드는 **앱 화면이 아니라 브라우저 페이지**입니다.
- 서버 실행 후 브라우저에서 아래 주소로 접속:
  - 같은 PC: `http://127.0.0.1:8088/usage/dashboard`
  - 같은 Wi-Fi 다른 기기: `http://<PC_IP>:8088/usage/dashboard`
- 대시보드 페이지 보호 키를 설정한 경우:
  - `USAGE_DASHBOARD_ACCESS_KEY=...`
  - 접속 URL: `http://127.0.0.1:8088/usage/dashboard?key=<설정값>`
- 상단 로그인은 대시보드/통계 조회 권한이 있는 계정으로 진행:
  - 권장: `USAGE_ADMIN_USERNAME`, `USAGE_ADMIN_PASSWORD` 설정
  - 값 변경 후에는 `server\RUN_ALL_SERVERS.bat` 재실행 필요
- 참고:
  - 전체 사용자 통계 API(`/usage/overview`, `/usage/user-detail`)는 관리자 권한이 필요합니다.
  - 일반 사용자는 앱 내 `내 사용량`(`/usage/me`)만 조회합니다.

## 5) 자주 발생하는 문제

- `Env file not found`:
  - `server\SERVER_ENV_PATH.txt`의 경로가 실제 파일 위치와 같은지 확인
  - `server\SERVER_ENV_TEMPLATE.env`를 복제(복사-붙여넣기)해 해당 경로에 배치
- `OPENAI_API_KEY` 비어 있음:
  - `server\SERVER_ENV_PATH.txt`에 적힌 키 파일을 수정 후 재실행
- 키 파일 경로를 바꾸고 싶음:
  - `server\SERVER_ENV_PATH.txt`에서 `OPENAI_API_KEY_FILE_PATH=원하는경로`로 변경
- `Backend is not reachable`:
  - 서버 미실행 또는 포트 충돌
  - 빠른 확인: `server\RUN_ALL_SERVERS.bat` 실행
  - 서버 창이 떠 있고 `8088` 포트가 열렸는지 확인
## 6) 원격 AI 개념 (참고)

앱의 AI 해석은 원격 AI(backend 경유)만 사용합니다.

- 원격 AI:
  - 앱 -> `backend` 서버 -> OpenAI API -> 앱 순서로 응답
  - 입력/검색/필드보완 정확도를 높이는 실제 LLM 경로
  - 키 미설정/업스트림 실패 시 오류를 반환합니다. (로컬 fallback 비활성)
