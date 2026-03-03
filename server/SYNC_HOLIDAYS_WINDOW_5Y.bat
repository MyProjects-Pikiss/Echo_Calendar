@echo off
setlocal

call "%~dp0SYNC_HOLIDAYS_NOW.bat" --today-window-years 5 %*
exit /b %errorlevel%
