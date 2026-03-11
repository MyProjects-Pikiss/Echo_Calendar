@echo off
setlocal

cd /d "%~dp0"
python tools\sync_app_version.py
if errorlevel 1 goto :python_error
docker compose -f docker-compose.yml -f docker-compose.external-env.yml -f docker-compose.tunnel.yml up --build -d
goto :eof

:python_error
py -3 tools\sync_app_version.py
if errorlevel 1 exit /b 1
docker compose -f docker-compose.yml -f docker-compose.external-env.yml -f docker-compose.tunnel.yml up --build -d
