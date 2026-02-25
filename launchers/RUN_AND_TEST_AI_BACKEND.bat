@echo off
setlocal

REM One-click helper:
REM 1) opens backend server in a new terminal window
REM 2) waits briefly
REM 3) runs contract test in this window

cd /d "%~dp0"

echo [INFO] Starting backend in a new window...
start "Echo Calendar AI Backend" cmd /d /k ""%~dp0RUN_AI_BACKEND.bat""

echo [INFO] Waiting for backend startup...
timeout /t 7 >nul

call TEST_AI_BACKEND.bat
set RESULT=%errorlevel%

if %RESULT% neq 0 (
  echo [DONE] Backend window is still open. Check logs there.
  pause
  exit /b %RESULT%
)

echo [DONE] Server launch + contract test succeeded.
pause
exit /b 0
