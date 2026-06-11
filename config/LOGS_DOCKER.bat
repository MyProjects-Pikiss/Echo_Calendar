@echo off
setlocal
set EXIT_CODE=0

echo [Echo Calendar] Opening Docker logs. Press Ctrl+C to stop following logs.
echo.
cd /d "%~dp0\..\server"
docker compose --profile tunnel logs -f
if errorlevel 1 goto :docker_error
echo.
echo [OK] Docker logs command ended.
goto :done

:docker_error
echo.
echo [ERROR] Docker logs failed. Check Docker Desktop and the messages above.
set EXIT_CODE=1
goto :done

:done
echo.
pause
exit /b %EXIT_CODE%
