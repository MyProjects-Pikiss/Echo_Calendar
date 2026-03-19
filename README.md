# Echo Calendar

> 자연어 입력과 AI 해석을 중심으로 만든 Android 캘린더 프로젝트

[서비스 상태](https://echo-calendar.win/health) · [배포 링크](https://echo-calendar.win) · [apk 링크](https://echo-calendar.win/downloads/echo_calendar.apk)
[AI 토큰이나 보안등을 위해 회원가입, 서버는 항시 가동중이 아닙니다]

Echo Calendar는 일정을 항목별로 하나씩 입력하기보다, 텍스트나 음성으로 빠르게 기록하고 다시 쉽게 찾을 수 있도록 만든 캘린더 앱입니다.  
AI 연동을 기본 사용 흐름으로 두고 있으며, 일정 데이터는 Android 앱의 로컬 DB에 저장됩니다.  
이 저장소에는 Android 앱과 FastAPI 백엔드가 함께 들어 있습니다.

## 소개

앱에서는 월간 캘린더, 날짜별 일정 목록, 반복 일정, 알림, 로컬 검색을 제공합니다.  
여기에 AI 해석을 연결해 일정 생성, 수정, 삭제 요청과 자연어 검색을 더 간단하게 처리할 수 있도록 구성했습니다.

AI는 입력과 검색을 돕는 역할을 하며, 해석 결과는 바로 반영하지 않고 사용자 확인 이후에만 적용됩니다.  
인증, 공휴일 동기화, 사용량 처리, 앱 버전 확인 같은 온라인 기능은 FastAPI 백엔드가 담당합니다.

AI 없이도 기본 일정 관리와 검색이 가능합니다.

## 주요 기능

### 앱
- 월간 캘린더와 날짜별 일정 조회
- 일정 생성, 수정, 삭제
- 연간 반복 일정
- 로컬 알림
- Room FTS 기반 검색

### AI 연동
- 자연어 일정 입력 해석
- 자연어 일정 검색 해석
- 일정 수정/삭제 요청 해석
- 필드 보완 및 사용자 확인 기반 반영

### 서버
- 로그인 / 회원가입
- 공휴일 동기화와 로컬 캐시 반영
- 사용량 집계
- 앱 버전 확인 및 APK 다운로드

## 동작 방식

```text
텍스트 / 음성 입력
        ↓
Android App (Kotlin, Jetpack Compose, Room)
   ├─ 일정 저장 / 조회 / 알림
   ├─ 로컬 검색
   └─ AI 해석 요청
            ↓
      FastAPI Backend
      ├─ Auth
      ├─ AI Interpretation
      ├─ Holiday Sync
      ├─ Usage Tracking
      └─ App Version / APK Delivery
```

일정 데이터는 로컬 DB를 기준으로 관리합니다.  
AI는 생성·수정·삭제·검색 요청을 해석해 앱이 이해할 수 있는 형태로 전달하고, 실제 반영은 사용자가 확인한 뒤에만 진행됩니다.

## 저장소 구조

```text
.
├─ app/                         # Android 클라이언트
├─ server/                      # FastAPI 백엔드 및 Docker Compose 구성
├─ ECHO_CALENDAR_WIN_SETUP.md   # Windows 배포 체크리스트
├─ Echo Calendar_DB 스키마 명세서(v1.3).md
├─ Echo Calendar_통합 명세서(v1.6).md
└─ SERVER_QUICK_GUIDE.md
```

## 기술 스택

- Android: Kotlin, Jetpack Compose, Room(SQLite / FTS)
- Backend: Python, FastAPI, Uvicorn
- Infra: Docker Compose, Cloudflare Tunnel, Kafka
- External Services: OpenAI API, holiday API

## 실행 방법

### Android 앱
1. `app/APP_CLIENT_CONFIG.txt`에서 서버 주소와 앱 버전을 확인합니다.
2. Android Studio에서 `app/`를 열거나 Gradle로 빌드합니다.

```bash
cd app
./gradlew assembleDebug
```

### Backend
1. `server/SERVER_ENV_TEMPLATE.env`를 기준으로 외부 env 파일을 준비합니다.
2. `server/.env`에서 외부 env 파일 경로를 현재 환경에 맞게 수정합니다.
3. Docker Compose로 백엔드를 실행합니다.

```bash
cd server
docker compose -f docker-compose.yml -f docker-compose.external-env.yml up --build
```

Cloudflare Tunnel을 함께 사용할 경우:

```bash
cd server
docker compose \
  -f docker-compose.yml \
  -f docker-compose.external-env.yml \
  -f docker-compose.tunnel.yml \
  up --build -d
```

## 참고

- `/ai/*` 엔드포인트는 로그인 후 받은 인증 토큰이 필요합니다.
- Windows 환경 기준 배포 절차는 `ECHO_CALENDAR_WIN_SETUP.md`에 정리되어 있습니다.
- 서버 실행 및 운영 관련 내용은 `SERVER_QUICK_GUIDE.md`를 참고하시면 됩니다.

## 문서

- `Echo Calendar_통합 명세서(v1.6).md`
- `Echo Calendar_DB 스키마 명세서(v1.3).md`
- `SERVER_QUICK_GUIDE.md`
- `ECHO_CALENDAR_WIN_SETUP.md`
