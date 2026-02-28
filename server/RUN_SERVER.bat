@echo off
setlocal enabledelayedexpansion

REM One-click backend launcher for Echo Calendar AI (Windows)
REM - reads local env file path from SERVER_ENV_PATH.txt
REM - creates venv if missing
REM - installs requirements
REM - starts uvicorn on :8088

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%") do set "SERVER_ROOT=%%~fI"

if not exist "%SERVER_ROOT%\backend\" (
  echo [ERROR] backend folder not found.
  echo [INFO] Expected path: "%SERVER_ROOT%\backend"
  exit /b 1
)

cd /d "%SERVER_ROOT%\backend"

set "PATH_CONFIG_FILE=%~dp0SERVER_ENV_PATH.txt"
if not exist "%PATH_CONFIG_FILE%" (
  (
    echo # Echo Calendar AI launcher config
    echo # Format: KEY=VALUE
    echo # This file points to your local env file where OPENAI_API_KEY is stored.
    echo OPENAI_API_KEY_FILE_PATH=%USERPROFILE%\SERVER_ENV_TEMPLATE.env
  ) > "%PATH_CONFIG_FILE%"
  echo [INFO] Created path config: %PATH_CONFIG_FILE%
)

set "RAW_ENV_PATH="
for /f "usebackq tokens=* delims=" %%L in ("%PATH_CONFIG_FILE%") do (
  set "LINE=%%L"
  if defined LINE (
    if not "!LINE:~0,1!"=="#" (
      set "CFG_KEY="
      set "CFG_VAL="
      for /f "tokens=1* delims==" %%A in ("!LINE!") do (
        set "CFG_KEY=%%~A"
        set "CFG_VAL=%%~B"
      )

      if /i "!CFG_KEY!"=="OPENAI_API_KEY_FILE_PATH" (
        set "RAW_ENV_PATH=!CFG_VAL!"
        goto :path_loaded
      )
      REM Backward compatibility: older named keys.
      if /i "!CFG_KEY!"=="API_KEY_FILE_PATH" (
        set "RAW_ENV_PATH=!CFG_VAL!"
        goto :path_loaded
      )
      if /i "!CFG_KEY!"=="OPENAI_ENV_FILE" (
        set "RAW_ENV_PATH=!CFG_VAL!"
        goto :path_loaded
      )

      REM Backward compatibility: legacy format with only one path line.
      if not defined CFG_VAL (
        set "RAW_ENV_PATH=!LINE!"
        goto :path_loaded
      )
    )
  )
)

:path_loaded
if not defined RAW_ENV_PATH (
  echo [ERROR] API key file path is missing in %PATH_CONFIG_FILE%
  echo [ACTION] Add a full file path in %PATH_CONFIG_FILE% and run again.
  exit /b 1
)

set "ENV_FILE=!RAW_ENV_PATH!"
set "ENV_FILE=!ENV_FILE:"=!"
set "ENV_FILE=!ENV_FILE:%%USERPROFILE%%=%USERPROFILE%!"
set "ENV_FILE=!ENV_FILE:%%HOMEDRIVE%%=%HOMEDRIVE%!"
set "ENV_FILE=!ENV_FILE:%%HOMEPATH%%=%HOMEPATH%!"
call set "ENV_FILE=%%ENV_FILE%%"

if not exist "%ENV_FILE%" (
  echo [ERROR] Env file not found: %ENV_FILE%
  echo [ACTION] Duplicate server\SERVER_ENV_TEMPLATE.env to %ENV_FILE% and edit OPENAI_API_KEY
  exit /b 1
)

findstr /b "OPENAI_API_KEY=" "%ENV_FILE%" >nul
if errorlevel 1 (
  echo [WARN] OPENAI_API_KEY is missing in %ENV_FILE%
  echo [WARN] AI interpretation endpoints may fail until OPENAI_API_KEY is set.
)

set "OPENAI_LINE="
for /f "usebackq tokens=* delims=" %%L in (`findstr /b "OPENAI_API_KEY=" "%ENV_FILE%"`) do (
  set "OPENAI_LINE=%%L"
)

if /i "!OPENAI_LINE!"=="OPENAI_API_KEY=" (
  echo [WARN] OPENAI_API_KEY is empty in %ENV_FILE%
  echo [WARN] AI interpretation endpoints may fail.
)

if /i "!OPENAI_LINE!"=="OPENAI_API_KEY=sk-xxxx" (
  echo [WARN] OPENAI_API_KEY is still placeholder ^(sk-xxxx^).
  echo [WARN] Replace with real key to enable remote AI interpretation.
)

set "HOLIDAY_LINE="
for /f "usebackq tokens=* delims=" %%L in (`findstr /b "KOREA_HOLIDAY_API_KEY=" "%ENV_FILE%"`) do (
  set "HOLIDAY_LINE=%%L"
)
if not defined HOLIDAY_LINE (
  echo [WARN] KOREA_HOLIDAY_API_KEY is missing in %ENV_FILE%
  echo [WARN] /holidays endpoint will return NOT_CONFIGURED until you set the key.
) else (
  if /i "!HOLIDAY_LINE!"=="KOREA_HOLIDAY_API_KEY=" (
    echo [WARN] KOREA_HOLIDAY_API_KEY is empty in %ENV_FILE%
    echo [WARN] /holidays endpoint will return NOT_CONFIGURED.
  )
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
echo [INFO] Loaded key file: %ENV_FILE%
echo [INFO] Path config file: %PATH_CONFIG_FILE%
echo.

set "OPENAI_ENV_FILE=%ENV_FILE%"
call ".venv\Scripts\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8088 --reload
exit /b %errorlevel%
