# Echo Calendar Windows 운영 가이드

이 문서는 사람이 직접 서버를 실행, 배포, 이전할 때 보는 운영 가이드입니다. AI가 외부 폴더 구조를 이해해야 할 때는 [../ai/EXTERNAL_RUNTIME_FOLDER.md](../ai/EXTERNAL_RUNTIME_FOLDER.md)를 먼저 봅니다.

## 운영 구조

- 백엔드는 `server/docker-compose.yml` 기준으로 실행합니다.
- `backend`, `usage-consumer`, `kafka`, `cloudflared`가 Docker Compose로 함께 올라갑니다.
- 운영 데이터와 비밀값은 repo 밖 `%USERPROFILE%\Echo_Calendar`에 둡니다.
- repo 안의 `server/backend/data`, `server/downloads`, `server/logs`는 런타임 원본으로 쓰지 않습니다.

## 준비 파일

- `config/echo-calendar.config.env`: 사람이 수정하는 원본 설정
- `server/.env`: `config/scripts/sync_config.py`가 생성하는 Docker Compose용 경로 파일
- `server/docker.defaults.env`: 안전한 Docker 기본값
- `server/SERVER_ENV_TEMPLATE.env`: 외부 `secrets/backend.env` 작성용 템플릿
- `app/APP_SIGNING_PATH.txt`: Android Gradle이 읽는 signing env 경로

## 외부 폴더

기본 위치:

```text
%USERPROFILE%\Echo_Calendar
```

필수 구조:

```text
%USERPROFILE%\Echo_Calendar\
  secrets\
    backend.env
    app-signing.env
    release-key.jks
  cloudflared\
    config.yml
    <tunnel-id>.json
  data\
    usage.db
    usage.db-shm
    usage.db-wal
    holiday_cache.db
  downloads\
    echo_calendar.apk
    app_version.env
  logs\
```

`config/echo-calendar.config.env`의 경로는 보통 아래처럼 둡니다.

```env
BACKEND_EXTERNAL_ENV_PATH=%USERPROFILE%\Echo_Calendar\secrets\backend.env
CLOUDFLARED_DIR=%USERPROFILE%\Echo_Calendar\cloudflared
APP_SIGNING_CONFIG_PATH=%USERPROFILE%\Echo_Calendar\secrets\app-signing.env
BACKEND_DATA_DIR=%USERPROFILE%\Echo_Calendar\data
BACKEND_DOWNLOADS_DIR=%USERPROFILE%\Echo_Calendar\downloads
BACKEND_LOGS_DIR=%USERPROFILE%\Echo_Calendar\logs
```

## 실행

설정 생성:

```powershell
config\SYNC_CONFIG.bat
```

서버 실행:

```powershell
config\RUN_DOCKER.bat
```

서버 중지:

```powershell
config\STOP_DOCKER.bat
```

로그 확인:

```powershell
config\LOGS_DOCKER.bat
```

실행 후 확인 주소:

- `https://echo-calendar.win/health`
- `https://echo-calendar.win/app/version?currentVersionCode=1`
- `https://echo-calendar.win/app/download-apk`
- `https://echo-calendar.win/usage/dashboard`

## Cloudflare Tunnel

Cloudflare Tunnel은 사용자가 직접 `cloudflared`를 실행하지 않고 Docker Compose의 `cloudflared` 컨테이너로 실행합니다.

`%USERPROFILE%\Echo_Calendar\cloudflared\config.yml`의 credentials 경로는 Docker 내부 경로여야 합니다.

```yaml
tunnel: echo-calendar
credentials-file: /etc/cloudflared/<tunnel-id>.json

ingress:
  - hostname: echo-calendar.win
    service: http://backend:8088
  - service: http_status:404
```

## APK 배포

1. 릴리즈 APK를 빌드합니다.
2. APK를 외부 downloads 폴더에 둡니다.

```text
%USERPROFILE%\Echo_Calendar\downloads\echo_calendar.apk
```

3. `config/echo-calendar.config.env`의 `APP_VERSION_CODE`, `APP_VERSION_NAME`을 수정합니다.
4. `config\SYNC_CONFIG.bat`를 실행합니다.

`config/scripts/sync_config.py`는 `app_version.env`를 외부 downloads 폴더에 생성합니다.

## 공휴일 동기화

외부 `secrets/backend.env`에 `KOREA_HOLIDAY_API_KEY`가 있어야 합니다.

```powershell
config\SYNC_HOLIDAYS.bat
```

실행하면 전체 범위, 오늘 기준 범위, 직접 기간 지정 중 하나를 고릅니다.

## 다른 PC로 이전

1. 새 PC에 Docker Desktop을 설치합니다.
2. 이 저장소를 복사하거나 다시 클론합니다.
3. 외부 `%USERPROFILE%\Echo_Calendar` 폴더를 새 PC에 복사합니다.
4. 필요하면 `config/echo-calendar.config.env`의 경로를 새 PC 기준으로 수정합니다.
5. `config\SYNC_CONFIG.bat`를 실행합니다.
6. `config\RUN_DOCKER.bat`를 실행합니다.

이전 시 핵심 보존 대상은 저장소 파일과 외부 `%USERPROFILE%\Echo_Calendar` 폴더입니다.

## 자주 보는 문제

- `Env file not found`: `BACKEND_EXTERNAL_ENV_PATH`가 실제 `secrets/backend.env`를 가리키는지 확인 후 `config\SYNC_CONFIG.bat` 실행
- `apk file not found`: `%USERPROFILE%\Echo_Calendar\downloads\echo_calendar.apk` 존재 여부 확인
- AI 호출 `401 Unauthorized`: 앱 로그인 후 발급된 Bearer 토큰 필요
- `cloudflared` 연결 실패: `config.yml`의 `service: http://backend:8088`와 credentials 파일 경로 확인
- `OPENAI_API_KEY` 비어 있음: 외부 `secrets/backend.env`에 키 입력 후 Compose 재시작
- Docker pipe/engine 오류: Docker Desktop 실행 상태와 Linux containers 모드 확인
