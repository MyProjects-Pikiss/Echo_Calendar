@echo off
setlocal

cd /d "%~dp0"
docker compose -f docker-compose.yml -f docker-compose.external-env.yml -f docker-compose.tunnel.yml down

