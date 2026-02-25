@echo off
setlocal
set "NO_PAUSE="

:parse_args
if "%~1"=="" goto :args_done
if /i "%~1"=="--no-pause" set "NO_PAUSE=1"
shift
goto :parse_args

:args_done

REM Quick contract test runner for Echo Calendar AI backend (Windows)
REM Requires backend server running at http://127.0.0.1:8088

cd /d "%~dp0.."

if not exist "backend\.venv\Scripts\python.exe" (
  echo [ERROR] backend venv not found.
  echo [ACTION] Run RUN_AI_BACKEND.bat once first.
  set "RESULT=1"
  goto :end
)

echo [INFO] Checking backend health at http://127.0.0.1:8088/health ...
call "backend\.venv\Scripts\python.exe" -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8088/health', timeout=3).read(1)"

if errorlevel 1 (
  echo [ERROR] Backend is not reachable on 127.0.0.1:8088
  echo [ACTION] Start server first: RUN_AI_BACKEND.bat
  set "RESULT=1"
  goto :end
)

echo [INFO] Running contract check...
set "CHECK_ARGS=--base-url http://127.0.0.1:8088 --timeout 20 --verbose --print-response"
call "backend\.venv\Scripts\python.exe" "tools\check_ai_backend_contract.py" %CHECK_ARGS%
if errorlevel 1 (
  echo [FAIL] Contract check failed.
  set "RESULT=1"
  goto :end
)

echo [PASS] Contract check passed.
set "RESULT=0"

:end
if not defined NO_PAUSE pause
exit /b %RESULT%
