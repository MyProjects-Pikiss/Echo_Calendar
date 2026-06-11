@echo off
setlocal
set EXIT_CODE=0

cd /d "%~dp0\.."
echo [Echo Calendar] Syncing generated config files...

python config\scripts\sync_config.py
if %ERRORLEVEL% EQU 9009 goto :python_error
if errorlevel 1 goto :sync_error
echo.
echo [OK] Config sync completed.
goto :done

:python_error
py -3 config\scripts\sync_config.py
if errorlevel 1 goto :sync_error
echo.
echo [OK] Config sync completed.
goto :done

:sync_error
echo.
echo [ERROR] Config sync failed. Check the messages above.
set EXIT_CODE=1
goto :done

:done
echo.
pause
exit /b %EXIT_CODE%
