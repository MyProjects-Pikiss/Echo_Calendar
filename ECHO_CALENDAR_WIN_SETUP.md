# echo-calendar.win 배포 체크리스트

이 문서는 `echo-calendar.win` 도메인으로 Echo Calendar 백엔드를 Docker Desktop 기준으로 운영하는 최소 절차입니다.

## 1) Cloudflare에서 할 일

### 옵션 A: 서버 공인 IP가 있는 경우

1. DNS > Records > `A` 레코드 추가
   - Name: `@`
   - IPv4 address: `<서버 공인 IP>`
   - Proxy status: Proxied(주황 구름)
2. SSL/TLS 모드: `Full` 또는 `Full (strict)`
3. 브라우저 확인
   - `https://echo-calendar.win/health`

### 옵션 B: Cloudflare Tunnel을 쓰는 경우

1. Cloudflare에서 터널을 생성합니다.
2. 서버 PC의 `.cloudflared` 폴더에 터널 설정 파일을 준비합니다.
3. `config.yml`의 서비스 주소를 Docker 기준으로 맞춥니다.
   - `service: http://backend:8088`
4. 브라우저 확인
   - `https://echo-calendar.win/health`

## 2) Docker 경로 설정

`server/.env` 파일에서 아래 경로를 현재 PC에 맞게 설정합니다.

```env
BACKEND_EXTERNAL_ENV_PATH=C:\Users\wnstk\Echo_Calendar\SERVER_ENV_TEMPLATE.env
CLOUDFLARED_DIR=C:\Users\wnstk\.cloudflared
```

설명:
- `BACKEND_EXTERNAL_ENV_PATH`: 백엔드가 읽을 외부 env 파일 경로
- `CLOUDFLARED_DIR`: Cloudflare Tunnel 설정 폴더 경로

## 3) 백엔드 env 파일 값

`BACKEND_EXTERNAL_ENV_PATH`가 가리키는 실사용 env 파일에 아래 값을 채웁니다.

```env
HOST=0.0.0.0
PORT=8088

OPENAI_API_KEY=sk-xxxx

ALLOW_SIGNUP=false

APP_LATEST_VERSION_CODE=3
APP_LATEST_VERSION_NAME=0.9.1
APP_MIN_SUPPORTED_VERSION_CODE=3

APP_DOWNLOADS_DIR=downloads
APP_APK_FILENAME=echo_calendar.apk
APP_APK_DOWNLOAD_URL=https://echo-calendar.win/app/download-apk
```

설명:
- `APP_APK_DOWNLOAD_URL`를 명시하면 프록시/스킴 이슈 없이 항상 고정 URL을 내려줍니다.
- 강제 업데이트를 원하면 `APP_MIN_SUPPORTED_VERSION_CODE`를 최신과 같게 유지하세요.

## 4) APK 파일 배치

1. 릴리즈 APK 빌드
2. 파일을 아래 위치에 복사
   - `server/downloads/echo_calendar.apk`
3. 버전만 바뀐 경우 `server/downloads/SYNC_APP_VERSION.bat` 실행
4. 백엔드 코드까지 바뀐 경우 `docker compose ... up --build -d` 또는 `server/RUN_DOCKER_BACKEND.bat` 실행
5. 링크 확인
   - `https://echo-calendar.win/app/download-apk`

## 5) 실행

```powershell
cd server
docker compose \
  -f docker-compose.yml \
  -f docker-compose.external-env.yml \
  -f docker-compose.tunnel.yml \
  up --build -d
```

## 6) 앱 빌드 설정

`app/APP_CLIENT_CONFIG.txt`:

```txt
SERVER_BASE_URL=https://echo-calendar.win
APP_VERSION_CODE=3
APP_VERSION_NAME=0.9.1
```

그 다음 앱을 다시 빌드/설치하세요.

## 7) 최종 확인 URL

- 서버 헬스: `https://echo-calendar.win/health`
- 버전 API: `https://echo-calendar.win/app/version?currentVersionCode=1`
- APK 다운로드: `https://echo-calendar.win/app/download-apk`

버전 API 응답에서 `apkDownloadUrl`이 위 도메인으로 내려오면 정상입니다.

## 8) 다른 PC로 옮길 때

1. 새 PC에 Docker Desktop 설치
2. 프로젝트 폴더 복사
3. 외부 env 파일 복사
   - 예: `C:\Users\<사용자명>\Echo_Calendar\SERVER_ENV_TEMPLATE.env`
4. `.cloudflared` 폴더 복사 또는 새 PC에서 Tunnel 재로그인
5. `server/.env` 경로를 새 PC 기준으로 수정

```env
BACKEND_EXTERNAL_ENV_PATH=C:\Users\<사용자명>\Echo_Calendar\SERVER_ENV_TEMPLATE.env
CLOUDFLARED_DIR=C:\Users\<사용자명>\.cloudflared
```

6. 실행

```powershell
cd server
docker compose \
  -f docker-compose.yml \
  -f docker-compose.external-env.yml \
  -f docker-compose.tunnel.yml \
  up --build -d
```

7. 확인
   - `https://echo-calendar.win/health`

핵심은 새 PC에서 아래 3가지만 다시 맞추면 된다는 점입니다.
- 외부 env 파일 위치
- `.cloudflared` 폴더 위치
- `server/.env` 경로
