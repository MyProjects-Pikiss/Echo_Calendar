@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%") do set "SERVER_ROOT=%%~fI"

if not exist "%SERVER_ROOT%\backend\" (
  echo [ERROR] backend folder not found.
  exit /b 1
)

cd /d "%SERVER_ROOT%\backend"

if not exist ".venv\Scripts\python.exe" (
  echo [ERROR] Python venv not found. Run server\RUN_SERVER.bat once first.
  exit /b 1
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

call ".venv\Scripts\python.exe" "scripts\sync_holidays.py"
exit /b %errorlevel%
