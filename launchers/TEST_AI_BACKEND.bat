@echo off
setlocal

REM Quick contract test runner for Echo Calendar AI backend (Windows)
REM Requires backend server running at http://127.0.0.1:8088

cd /d "%~dp0.."

if not exist "backend\.venv\Scripts\python.exe" (
  echo [ERROR] backend venv not found.
  echo [ACTION] Run RUN_AI_BACKEND.bat once first.
  exit /b 1
)

echo [INFO] Checking backend health at http://127.0.0.1:8088/health ...
powershell -NoProfile -Command ^
  "try { $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8088/health' -TimeoutSec 3; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300) { exit 0 } else { exit 1 } } catch { exit 1 }"

if errorlevel 1 (
  echo [ERROR] Backend is not reachable on 127.0.0.1:8088
  echo [ACTION] Start server first: RUN_AI_BACKEND.bat
  exit /b 1
)

echo [INFO] Running contract check...
call "backend\.venv\Scripts\python.exe" "tools\check_ai_backend_contract.py" --base-url http://127.0.0.1:8088
if errorlevel 1 (
  echo [FAIL] Contract check failed.
  exit /b 1
)

echo [PASS] Contract check passed.
exit /b 0
