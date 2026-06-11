@echo off
setlocal
set EXIT_CODE=0

cd /d "%~dp0\.."
echo [Echo Calendar] Syncing holiday data...

python config\scripts\sync_holidays.py
if %ERRORLEVEL% EQU 9009 goto :python_error
if errorlevel 1 goto :sync_error
echo.
echo [OK] Holiday sync completed.
goto :done

:python_error
py -3 config\scripts\sync_holidays.py
if errorlevel 1 goto :sync_error
echo.
echo [OK] Holiday sync completed.
goto :done

:sync_error
echo.
echo [ERROR] Holiday sync failed. Check the messages above.
set EXIT_CODE=1
goto :done

:done
echo.
pause
exit /b %EXIT_CODE%
