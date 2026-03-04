# echo-calendar.win 배포 체크리스트

이 문서는 `echo-calendar.win` 도메인으로 Echo Calendar 서버/앱을 연결하는 최소 절차입니다.

## 1) Cloudflare에서 할 일

### 옵션 A: 서버 공인 IP가 있는 경우 (간단)
1. DNS > Records > `A` 레코드 추가
   - Name: `@`
   - IPv4 address: `<서버 공인 IP>`
   - Proxy status: Proxied(주황 구름)
2. SSL/TLS 모드: `Full` 또는 `Full (strict)`
3. 브라우저 확인:
   - `https://echo-calendar.win/health` 가 열리면 성공

### 옵션 B: 공인 IP 없이 로컬/사설망에서 올릴 경우 (Cloudflare Tunnel)
1. 서버 PC에 `cloudflared` 설치
2. 터널 생성/로그인 후 라우팅 설정
   - hostname: `echo-calendar.win`
   - service: `http://localhost:8088`
3. DNS에 터널 CNAME이 자동 연결되는지 확인
4. 브라우저 확인:
   - `https://echo-calendar.win/health`

## 2) 서버 설정 파일(env) 값

`server\\SERVER_ENV_PATH.txt` 가 가리키는 **실사용 env 파일**에 아래 값 입력:

```env
HOST=0.0.0.0
PORT=8088

ALLOW_SIGNUP=false

APP_LATEST_VERSION_CODE=3
APP_LATEST_VERSION_NAME=0.9.1
APP_MIN_SUPPORTED_VERSION_CODE=3

APP_DOWNLOADS_DIR=downloads
APP_APK_FILENAME=echo-calendar-latest.apk
APP_APK_DOWNLOAD_URL=https://echo-calendar.win/downloads/echo-calendar-latest.apk
```

설명:
- `APP_APK_DOWNLOAD_URL`를 명시하면 프록시/스킴 이슈 없이 항상 고정 URL을 내려줍니다.
- 강제 업데이트를 원하면 `APP_MIN_SUPPORTED_VERSION_CODE`를 최신과 같게 유지하세요.

## 3) APK 파일 배치

1. 릴리즈 APK 빌드
2. 파일을 아래 위치에 복사
   - `server/downloads/echo-calendar-latest.apk`
3. 서버 재시작 (`server\\RUN_ALL_SERVERS.bat`)
4. 링크 확인
   - `https://echo-calendar.win/downloads/echo-calendar-latest.apk`

## 4) 앱 빌드 설정

`app/APP_CLIENT_CONFIG.txt`:

```txt
SERVER_BASE_URL=https://echo-calendar.win
APP_VERSION_CODE=3
APP_VERSION_NAME=0.9.1
```

그 다음 앱을 다시 빌드/설치하세요.

## 5) 최종 확인 URL

- 서버 헬스: `https://echo-calendar.win/health`
- 버전 API: `https://echo-calendar.win/app/version?currentVersionCode=1`
- APK 다운로드: `https://echo-calendar.win/downloads/echo-calendar-latest.apk`

버전 API 응답에서 `apkDownloadUrl`이 위 도메인으로 내려오면 정상입니다.
