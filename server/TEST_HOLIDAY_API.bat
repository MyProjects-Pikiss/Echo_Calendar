@echo off
setlocal enabledelayedexpansion

REM Holiday API quick check for Echo Calendar backend.
REM Usage:
REM   TEST_HOLIDAY_API.bat
REM   TEST_HOLIDAY_API.bat http://127.0.0.1:8088 2026-01-01 2026-12-31

set "BASE_URL=%~1"
if not defined BASE_URL set "BASE_URL=http://127.0.0.1:8088"

set "START_DATE=%~2"
if not defined START_DATE set "START_DATE=2026-01-01"

set "END_DATE=%~3"
if not defined END_DATE set "END_DATE=2026-12-31"

set "QUERY_URL=%BASE_URL%/holidays?startDate=%START_DATE%^&endDate=%END_DATE%"
set "TMP_FILE=%TEMP%\echo_calendar_holiday_response_%RANDOM%%RANDOM%.json"

echo [INFO] Request URL: %QUERY_URL%
echo [INFO] This checks status code and basic response schema keys.

for /f "usebackq delims=" %%H in (`curl -sS -o "%TMP_FILE%" -w "%%{http_code}" "%QUERY_URL%"`) do set "HTTP_CODE=%%H"

if not defined HTTP_CODE (
  echo [FAIL] No HTTP status was returned. Is backend running?
  if exist "%TMP_FILE%" del /q "%TMP_FILE%" >nul 2>nul
  exit /b 1
)

if not "%HTTP_CODE%"=="200" (
  echo [FAIL] /holidays returned HTTP %HTTP_CODE%
  if exist "%TMP_FILE%" (
    echo [RESPONSE]
    type "%TMP_FILE%"
  )
  if exist "%TMP_FILE%" del /q "%TMP_FILE%" >nul 2>nul
  exit /b 1
)

findstr /c:"\"holidays\"" "%TMP_FILE%" >nul
if errorlevel 1 (
  echo [FAIL] Missing key: holidays
  echo [RESPONSE]
  type "%TMP_FILE%"
  if exist "%TMP_FILE%" del /q "%TMP_FILE%" >nul 2>nul
  exit /b 1
)

findstr /c:"\"date\"" "%TMP_FILE%" >nul
if errorlevel 1 (
  echo [WARN] No date entries found in response for selected range.
)

findstr /c:"\"kind\"" "%TMP_FILE%" >nul
if errorlevel 1 (
  echo [WARN] No kind entries found in response for selected range.
)

echo [PASS] Holiday API check passed.
echo [RESPONSE]
type "%TMP_FILE%"

if exist "%TMP_FILE%" del /q "%TMP_FILE%" >nul 2>nul
exit /b 0
