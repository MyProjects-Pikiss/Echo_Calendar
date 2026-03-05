@echo off
setlocal
set "TUNNEL_NAME=echo-calendar"

where cloudflared >NUL 2>&1
if errorlevel 1 (
  echo [ERROR] cloudflared command not found.
  echo [ACTION] Install cloudflared first.
  pause
  exit /b 1
)

if not exist "%USERPROFILE%\.cloudflared\config.yml" (
  echo [ERROR] Cloudflared config not found: %USERPROFILE%\.cloudflared\config.yml
  echo [ACTION] Configure tunnel first, then retry.
  pause
  exit /b 1
)

echo [INFO] Starting Cloudflare tunnel: %TUNNEL_NAME%
cloudflared tunnel run %TUNNEL_NAME%
