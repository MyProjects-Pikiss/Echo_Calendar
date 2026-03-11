# Echo Calendar Server 실행 가이드

이 프로젝트의 백엔드 운영 기준은 `server/` 폴더의 Docker Compose 구성입니다.
백엔드 서버, 외부 env 파일, Cloudflare Tunnel까지 `server/` 폴더 기준으로 실행합니다.

## 1) 준비 파일

- `server/.env`: 외부 env 파일 경로와 Cloudflare Tunnel 폴더 경로
- `server/docker.defaults.env`: Docker 기본 env 값
- `server/docker-compose.yml`: 백엔드 기본 실행
- `server/docker-compose.external-env.yml`: 외부 env 파일 마운트
- `server/docker-compose.tunnel.yml`: Cloudflare Tunnel 실행

## 2) 사전 준비

1. 외부 env 파일을 준비합니다.
   - 예: `C:\Users\wnstk\Echo_Calendar\SERVER_ENV_TEMPLATE.env`
2. `server/.env`의 경로를 현재 PC에 맞게 수정합니다.
```env
BACKEND_EXTERNAL_ENV_PATH=C:\Users\wnstk\Echo_Calendar\SERVER_ENV_TEMPLATE.env
CLOUDFLARED_DIR=C:\Users\wnstk\.cloudflared
```

3. 외부 env 파일에 실제 키와 운영 값을 채웁니다.
   - `OPENAI_API_KEY`
   - `KOREA_HOLIDAY_API_KEY`
   - `USAGE_ADMIN_USERNAME`
   - `USAGE_ADMIN_PASSWORD`
   - `APP_APK_DOWNLOAD_URL`
4. Cloudflare Tunnel의 `config.yml`이 Docker 기준인지 확인합니다.

```yaml
tunnel: echo-calendar
credentials-file: /etc/cloudflared/<tunnel-credentials>.json

ingress:
  - hostname: echo-calendar.win
    service: http://backend:8088
  - service: http_status:404
```

## 3) 실행 방법

### 백엔드 + 외부 env

```powershell
cd server
docker compose -f docker-compose.yml -f docker-compose.external-env.yml up --build
```

### 백엔드 + 외부 env + Cloudflare Tunnel

```powershell
cd server
docker compose \
  -f docker-compose.yml \
  -f docker-compose.external-env.yml \
  -f docker-compose.tunnel.yml \
  up --build -d
```

### 개발 모드

```powershell
cd server
docker compose \
  -f docker-compose.yml \
  -f docker-compose.external-env.yml \
  -f docker-compose.dev.yml \
  up --build
```

## 4) 앱 연결 기준

- 앱 설정 파일: `app/APP_CLIENT_CONFIG.txt`
- 운영 도메인 기준:
  - `SERVER_BASE_URL=https://echo-calendar.win`
- 앱 버전 체크 API:
  - `GET /app/version?currentVersionCode=<정수>`

## 5) APK 배포

1. 릴리즈 APK 빌드
2. `server/downloads/echo_calendar.apk`에 복사
3. 앱 버전은 `app/APP_CLIENT_CONFIG.txt`의 `APP_VERSION_CODE`, `APP_VERSION_NAME`만 수정
4. 버전 정보만 갱신할 때는 `server/downloads/SYNC_APP_VERSION.bat` 실행
   - 이 BAT는 `server/tools/sync_app_version.py`를 호출해 `server/downloads/app_version.env`를 갱신함
   - 이미 최신 서버 코드가 떠 있다면 이 단계만으로 `/app/version` 응답이 즉시 반영됨
5. 최소 지원 버전만 필요할 때 외부 env 파일의 `APP_MIN_SUPPORTED_VERSION_CODE`를 별도로 조정
6. 백엔드 코드가 바뀐 경우에는 Docker Desktop의 단순 Restart가 아니라 재빌드가 필요
   - `server/RUN_DOCKER_BACKEND.bat`
   - 또는 `docker compose ... up --build -d`

```powershell
cd server
downloads\SYNC_APP_VERSION.bat
```

백엔드 코드 변경까지 포함된 배포의 경우:

```powershell
cd server
RUN_DOCKER_BACKEND.bat
```

## 6) 확인 주소

- 서버 헬스: `https://echo-calendar.win/health`
- 사용량 대시보드: `https://echo-calendar.win/usage/dashboard`
- 버전 API: `https://echo-calendar.win/app/version?currentVersionCode=1`
- APK 다운로드: `https://echo-calendar.win/app/download-apk`

## 7) 다른 PC로 옮길 때

1. 새 PC에 Docker Desktop을 설치합니다.
2. 이 저장소를 새 PC에 복사하거나 다시 클론합니다.
3. 외부 env 파일도 새 PC로 옮깁니다.
   - 예: `C:\Users\<사용자명>\Echo_Calendar\SERVER_ENV_TEMPLATE.env`
4. Cloudflare Tunnel을 계속 쓸 경우 `.cloudflared` 폴더도 새 PC에 준비합니다.
   - 기존 폴더를 복사하거나
   - 새 PC에서 `cloudflared tunnel login` 후 다시 설정합니다.
5. `server/.env`의 경로를 새 PC 기준으로 수정합니다.

```env
BACKEND_EXTERNAL_ENV_PATH=C:\Users\<사용자명>\Echo_Calendar\SERVER_ENV_TEMPLATE.env
CLOUDFLARED_DIR=C:\Users\<사용자명>\.cloudflared
```

6. 아래 BAT 또는 PowerShell 명령으로 실행합니다.
   - `server/RUN_DOCKER_BACKEND.bat`
   - 또는 `cd server && docker compose -f docker-compose.yml -f docker-compose.external-env.yml -f docker-compose.tunnel.yml up --build -d`
7. `https://echo-calendar.win/health` 로 최종 확인합니다.

옮길 때 꼭 필요한 것은 3가지입니다.
- 저장소 파일
- 외부 env 파일
- Cloudflare Tunnel 설정 폴더(`.cloudflared`)

## 8) 자주 발생하는 문제

- `Env file not found`
  - `server/.env`의 `BACKEND_EXTERNAL_ENV_PATH`가 실제 파일을 가리키는지 확인
- `apk file not found`
  - `server/downloads/echo_calendar.apk` 파일이 실제로 존재하는지 확인
  - Docker Desktop에서 단순 Restart만 한 경우 코드 변경이 반영되지 않을 수 있으므로 `RUN_DOCKER_BACKEND.bat` 또는 `docker compose ... up --build -d`로 재빌드
- `cloudflared`가 backend에 연결하지 못함
  - `.cloudflared/config.yml`의 서비스 주소가 `http://backend:8088`인지 확인
- `OPENAI_API_KEY` 비어 있음
  - 외부 env 파일을 열어 키를 입력하고 Compose를 재시작
- `https://echo-calendar.win/health` 접속 실패
  - Docker Compose가 떠 있는지 확인
  - Cloudflare Tunnel 설정과 DNS 연결 상태 확인
  - Docker Desktop 또는 Windows PowerShell에서 실행했는지 확인
