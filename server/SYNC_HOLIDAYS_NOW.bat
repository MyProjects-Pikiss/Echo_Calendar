@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%") do set "SERVER_ROOT=%%~fI"
set "LOG_DIR=%SERVER_ROOT%logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set "LOG_FILE=%LOG_DIR%\holiday_sync_latest.log"

if not exist "%SERVER_ROOT%\backend\" (
  echo [ERROR] backend folder not found.
  set "SYNC_EXIT=1"
  goto :end
)

cd /d "%SERVER_ROOT%\backend"

if not exist ".venv\Scripts\python.exe" (
  echo [ERROR] Python venv not found. Run server\RUN_ALL_SERVERS.bat once first.
  set "SYNC_EXIT=1"
  goto :end
)

set "PATH_CONFIG_FILE=%~dp0SERVER_ENV_PATH.txt"
set "RAW_ENV_PATH="
for /f "usebackq tokens=* delims=" %%L in ("%PATH_CONFIG_FILE%") do (
  set "LINE=%%L"
  if defined LINE (
    if not "!LINE:~0,1!"=="#" (
      for /f "tokens=1* delims==" %%A in ("!LINE!") do (
        if /i "%%~A"=="OPENAI_API_KEY_FILE_PATH" set "RAW_ENV_PATH=%%~B"
      )
    )
  )
)

if defined RAW_ENV_PATH (
  set "ENV_FILE=!RAW_ENV_PATH!"
  set "ENV_FILE=!ENV_FILE:"=!"
  set "ENV_FILE=!ENV_FILE:%%USERPROFILE%%=%USERPROFILE%!"
  set "OPENAI_ENV_FILE=!ENV_FILE!"
)

if not defined KOREA_HOLIDAY_API_MAX_CONCURRENCY set "KOREA_HOLIDAY_API_MAX_CONCURRENCY=3"

echo ==== holiday sync start %DATE% %TIME% ====>> "%LOG_FILE%"
call ".venv\Scripts\python.exe" "scripts\sync_holidays.py" ^
  --progressive-months-per-run 36 ^
  --chunk-months 6 ^
  --sleep-seconds 0.5 ^
  --progressive-start-date 1970-01-01 ^
  --progressive-end-date 2080-12-31 ^
  --until-complete ^
  --full-refresh ^
  --log-file "%LOG_FILE%" ^
  %*
set "SYNC_EXIT=%errorlevel%"
echo ==== holiday sync end code=!SYNC_EXIT! %DATE% %TIME% ====>> "%LOG_FILE%"

:end
if "!SYNC_NO_PAUSE!" NEQ "1" (
  echo.
  echo [INFO] holiday sync exit code: !SYNC_EXIT!
  echo [INFO] log: "%LOG_FILE%"
  echo.
  pause
)

exit /b !SYNC_EXIT!
