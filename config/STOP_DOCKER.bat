@echo off
setlocal
set EXIT_CODE=0

echo [Echo Calendar] Stopping Docker services...
cd /d "%~dp0\..\server"
docker compose --profile tunnel down
if errorlevel 1 goto :docker_error
echo.
echo [OK] Docker services stopped.
goto :done

:docker_error
echo.
echo [ERROR] Docker stop failed. Check Docker Desktop and the messages above.
set EXIT_CODE=1
goto :done

:done
echo.
pause
exit /b %EXIT_CODE%
