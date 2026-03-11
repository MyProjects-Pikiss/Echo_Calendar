@echo off
setlocal

cd /d "%~dp0"
python ..\tools\sync_app_version.py
if errorlevel 1 goto :python_error
goto :eof

:python_error
py -3 ..\tools\sync_app_version.py
if errorlevel 1 exit /b 1
