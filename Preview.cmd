@echo off
powershell.exe -NoProfile -File "%~dp0tools\Start-Preview.ps1"
set "BuildExitCode=%errorlevel%"
if not "%BuildExitCode%"=="0" pause
exit /b %BuildExitCode%
