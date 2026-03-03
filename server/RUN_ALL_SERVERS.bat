@echo off
setlocal enabledelayedexpansion
set "SCRIPT_DIR=%~dp0"

set "PATH_CONFIG_FILE=%SCRIPT_DIR%SERVER_ENV_PATH.txt"
if not exist "%PATH_CONFIG_FILE%" (
  echo [ERROR] Path config file not found: %PATH_CONFIG_FILE%
  echo [ACTION] Run server\RUN_BACKEND_COMMON.bat first.
  exit /b 1
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
      if /i "!CFG_KEY!"=="API_KEY_FILE_PATH" (
        set "RAW_ENV_PATH=!CFG_VAL!"
        goto :path_loaded
      )
      if /i "!CFG_KEY!"=="OPENAI_ENV_FILE" (
        set "RAW_ENV_PATH=!CFG_VAL!"
        goto :path_loaded
      )
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
  echo [ACTION] Update %PATH_CONFIG_FILE% or create the target env file.
  exit /b 1
)

echo [INFO] Starting AI server window...
start "Echo Server :8088" cmd /k "cd /d ""%SCRIPT_DIR%backend"" && set ""SERVER_MODE=all"" && set ""OPENAI_ENV_FILE=%ENV_FILE%"" && .venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8088 --reload"

echo [READY] Server window launched.
echo [INFO] URL: http://127.0.0.1:8088/health
exit /b 0
