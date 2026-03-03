@echo off
setlocal
set "SCRIPT_DIR=%~dp0"

set "RESOLVED_ENV_FILE=%SCRIPT_DIR%RUN_ENV_PATH.resolved.txt"
if not exist "%RESOLVED_ENV_FILE%" (
  echo [ERROR] Resolved env path file not found: %RESOLVED_ENV_FILE%
  echo [ACTION] Run server\RUN_BACKEND_COMMON.bat first.
  exit /b 1
)

set "OPENAI_ENV_FILE_VALUE="
for /f "usebackq tokens=1* delims==" %%A in ("%RESOLVED_ENV_FILE%") do (
  if /i "%%~A"=="OPENAI_ENV_FILE" set "OPENAI_ENV_FILE_VALUE=%%~B"
)
if "%OPENAI_ENV_FILE_VALUE%"=="" (
  echo [ERROR] OPENAI_ENV_FILE is missing in %RESOLVED_ENV_FILE%
  exit /b 1
)

echo [INFO] Starting AI server window...
start "Echo Server :8088" cmd /k "cd /d ""%SCRIPT_DIR%backend"" && set ""SERVER_MODE=all"" && set ""OPENAI_ENV_FILE=%OPENAI_ENV_FILE_VALUE%"" && .venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8088 --reload"

echo [READY] Server window launched.
echo [INFO] URL: http://127.0.0.1:8088/health
exit /b 0
