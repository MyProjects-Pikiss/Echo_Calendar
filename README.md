# Echo Calendar

> 오프라인 우선 일정 관리와 AI 보조 해석을 결합한 Android 캘린더 프로젝트

Echo Calendar는 로컬 일정 관리가 항상 동작하도록 설계한 Android 캘린더 앱입니다.  
일정 저장, 조회, 검색, 알림은 기기 내 데이터 기준으로 처리하고, 온라인 상태에서는 AI 해석, 인증, 공휴일 동기화, 앱 버전 확인 같은 서버 기능을 추가로 사용합니다.

이 저장소에는 Android 앱과 FastAPI 백엔드가 함께 포함되어 있습니다.

## Overview

이 프로젝트는 캘린더 앱의 기본 동작을 네트워크에 의존하지 않도록 구성하는 데 초점을 두었습니다.

로컬 DB가 일정 데이터의 기준이며, AI는 입력과 검색을 돕는 보조 기능으로만 사용합니다.  
AI가 생성한 제안도 바로 반영하지 않고, 사용자 확인 이후에만 생성·수정·삭제가 적용됩니다.

## Design Principles

- **Offline-first**  
  일정 조회, CRUD, 검색, 알림은 로컬에서 우선 동작합니다.

- **AI as assistant**  
  AI는 입력 해석과 검색 보조를 담당하지만, 데이터의 기준이 되지는 않습니다.

- **User-confirmed changes**  
  데이터 변경은 항상 사용자 확인 이후에만 반영합니다.

- **Operational backend**  
  백엔드는 인증, 사용량, 공휴일 동기화, 버전 체크 같은 운영 기능을 담당합니다.

## Features

### App
- 월간 캘린더와 날짜별 일정 목록
- 일정 생성, 수정, 삭제
- 연간 반복 일정
- 로컬 알림
- 로컬 FTS 기반 검색

### Online features
- AI 입력 해석
- AI 검색 해석
- 로그인 / 회원가입
- 공휴일 동기화 및 로컬 캐시
- 앱 버전 체크 및 APK 다운로드

## Architecture

```text
User input
   ↓
Android App (Kotlin, Jetpack Compose, Room)
   ├─ Local CRUD
   ├─ Local Search
   ├─ Local Reminder Scheduling
   └─ Optional AI Requests
            ↓
       FastAPI Backend
       ├─ Auth
       ├─ AI Interpretation
       ├─ Holiday Sync
       └─ App Version / APK Delivery
```

## Repository Structure

```text
.
├─ app/                         # Android client
├─ server/                      # FastAPI backend and Docker Compose setup
├─ ECHO_CALENDAR_WIN_SETUP.md   # Windows deployment checklist
├─ Echo Calendar_DB 스키마 명세서(v1.3).md
├─ Echo Calendar_통합 명세서(v1.6).md
└─ SERVER_QUICK_GUIDE.md
```

## Tech Stack

- **Android**: Kotlin, Jetpack Compose, Room(SQLite/FTS)
- **Backend**: Python, FastAPI
- **Infra**: Docker Compose, Cloudflare Tunnel
- **Integrations**: OpenAI API, holiday API

## Getting Started

### Android app

1. `app/APP_CLIENT_CONFIG.txt`에서 서버 주소와 앱 버전을 확인합니다.
2. Android Studio에서 `app/`를 열거나 Gradle wrapper로 빌드합니다.

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

Cloudflare Tunnel까지 함께 사용할 경우:

```bash
cd server
docker compose \
  -f docker-compose.yml \
  -f docker-compose.external-env.yml \
  -f docker-compose.tunnel.yml \
  up --build -d
```

## Notes

- 로컬 데이터는 네트워크 기능 실패와 별개로 유지됩니다.
- `/ai/*` 엔드포인트는 로그인 후 받은 인증 토큰이 필요합니다.
- Windows 환경 기준 배포 절차는 `ECHO_CALENDAR_WIN_SETUP.md`를 참고하면 됩니다.
- 서버 실행과 운영 관련 상세 절차는 `SERVER_QUICK_GUIDE.md`에 정리되어 있습니다.

## Documentation

- `Echo Calendar_통합 명세서(v1.6).md`
- `Echo Calendar_DB 스키마 명세서(v1.3).md`
- `SERVER_QUICK_GUIDE.md`
- `ECHO_CALENDAR_WIN_SETUP.md`
