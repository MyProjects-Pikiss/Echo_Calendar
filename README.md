# Echo Calendar

자연어 입력과 AI 해석을 중심으로 만든 Android 캘린더 프로젝트입니다.

[서비스 상태](https://echo-calendar.win/health) · [배포 링크](https://echo-calendar.win) · [APK 링크](https://echo-calendar.win/app/download-apk)

Echo Calendar는 텍스트나 음성으로 일정을 빠르게 기록하고, 로컬 검색과 AI 해석으로 다시 쉽게 찾을 수 있게 만든 캘린더 앱입니다. 일정 데이터는 Android 앱의 로컬 Room DB에 저장되며, 로그인/AI 해석/공휴일 동기화/사용량 집계/APK 배포는 FastAPI 백엔드가 담당합니다.

## 주요 기능

- Android 앱: 월간 캘린더, 일정 생성/수정/삭제, 라벨 관리, 연간 반복 일정, 로컬 알림, Room FTS 검색
- AI 연동: 자연어 일정 입력/검색/수정/삭제 해석, 기존 라벨 목록 기반 라벨 추천, 사용자 확인 후 반영
- 서버: 인증, OpenAI 연동, 공휴일 캐시, 사용량 집계, 앱 버전 확인, APK 다운로드
- 운영: Docker Compose, Cloudflare Tunnel, Kafka usage consumer

## 저장소 구조

```text
app/                      Android 클라이언트
server/                   FastAPI 백엔드와 Docker Compose 구성
config/                   통합 설정, 실행 BAT, 설정 동기화 스크립트
docs/human/               사람용 한글 운영 문서
docs/ai/                  AI용 영어 컨텍스트 문서
docs/specs/               앱/서버/DB 상세 명세
```

## 빠른 실행

Windows PowerShell 기준:

```powershell
config\SYNC_CONFIG.bat
config\RUN_DOCKER.bat
```

주요 BAT:

- `config\SYNC_CONFIG.bat`: `config/echo-calendar.config.env` 기준으로 생성 설정 동기화
- `config\RUN_DOCKER.bat`: Docker Compose로 backend/kafka/cloudflared 실행
- `config\STOP_DOCKER.bat`: Docker Compose 중지
- `config\LOGS_DOCKER.bat`: Docker 로그 확인
- `config\SYNC_HOLIDAYS.bat`: 공휴일 캐시 동기화

## Android 빌드

```powershell
config\SYNC_CONFIG.bat
cd app
.\gradlew assembleDebug
```

릴리즈 서명 정보는 repo 안에 두지 않습니다. `app/APP_SIGNING_PATH.txt`가 외부 signing env 파일을 가리킵니다.

## 운영 데이터와 비밀값

운영 DB, APK, 로그, API 키, Android signing key, Cloudflare tunnel credentials는 repo 밖 외부 폴더에 둡니다.

```text
%USERPROFILE%\Echo_Calendar
```

이 폴더의 구조와 역할은 [docs/ai/EXTERNAL_RUNTIME_FOLDER.md](docs/ai/EXTERNAL_RUNTIME_FOLDER.md)에 정리되어 있습니다. 새 AI 세션이 외부 폴더를 직접 읽지 못할 수 있으므로, 외부 폴더 관련 판단은 해당 문서를 기준으로 합니다.

## 문서

- [docs/human/SERVER_QUICK_GUIDE.md](docs/human/SERVER_QUICK_GUIDE.md): 사람용 Windows/Docker 운영 가이드
- [docs/ai/EXTERNAL_RUNTIME_FOLDER.md](docs/ai/EXTERNAL_RUNTIME_FOLDER.md): AI용 외부 런타임 폴더 계약
- [docs/specs/Echo Calendar_통합 명세서(v1.6).md](<docs/specs/Echo Calendar_통합 명세서(v1.6).md>): 앱/서버 통합 명세
- [docs/specs/Echo Calendar_DB 스키마 명세서(v1.3).md](<docs/specs/Echo Calendar_DB 스키마 명세서(v1.3).md>): Android 로컬 DB 스키마 명세
