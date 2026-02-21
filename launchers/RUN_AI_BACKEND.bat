@echo off
setlocal enabledelayedexpansion

REM One-click backend launcher for Echo Calendar AI (Windows)
REM - checks backend/.env
REM - creates venv if missing
REM - installs requirements
REM - starts uvicorn on :8088

cd /d "%~dp0.."
if not exist "backend" (
  echo [ERROR] backend folder not found.
  exit /b 1
)

cd backend

if not exist ".env" (
  echo [INFO] backend\.env not found. Creating from .env.example ...
  if not exist ".env.example" (
    echo [ERROR] backend\.env.example not found.
    exit /b 1
  )
  copy ".env.example" ".env" >nul
  echo [ACTION] Opened backend\.env. Set OPENAI_API_KEY then run this file again.
  start "" notepad ".env"
  exit /b 1
)

findstr /b "OPENAI_API_KEY=" ".env" >nul
if errorlevel 1 (
  echo [ERROR] OPENAI_API_KEY is missing in backend\.env
  start "" notepad ".env"
  exit /b 1
)

set "OPENAI_LINE="
for /f "usebackq tokens=* delims=" %%L in (`findstr /b "OPENAI_API_KEY=" ".env"`) do (
  set "OPENAI_LINE=%%L"
)

if /i "!OPENAI_LINE!"=="OPENAI_API_KEY=" (
  echo [ERROR] OPENAI_API_KEY is empty in backend\.env
  start "" notepad ".env"
  exit /b 1
)

if /i "!OPENAI_LINE!"=="OPENAI_API_KEY=sk-xxxx" (
  echo [ERROR] OPENAI_API_KEY is still placeholder ^(sk-xxxx^).
  start "" notepad ".env"
  exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
  echo [INFO] Creating virtual environment...
  py -3 -m venv .venv
  if errorlevel 1 (
    echo [ERROR] Failed to create venv. Install Python 3 first.
    exit /b 1
  )
)

echo [INFO] Installing/updating backend dependencies...
call ".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
  echo [ERROR] pip install failed.
  exit /b 1
)

echo.
echo [READY] Starting Echo Calendar AI backend on http://0.0.0.0:8088
echo [TIP] Keep this window open while testing the Android app.
echo.

call ".venv\Scripts\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8088 --reload
exit /b %errorlevel%
