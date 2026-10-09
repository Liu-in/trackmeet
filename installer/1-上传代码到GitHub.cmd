@echo off
rem ===========================================================================
rem  TrackMeet - Upload project to GitHub  (double-click this file)
rem  After upload, GitHub builds the Windows installer (exe) automatically.
rem ===========================================================================
setlocal
title TrackMeet - Upload to GitHub
cd /d "%~dp0"
echo.
echo   Starting the upload wizard, please wait...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0push-to-github.ps1"
set "EXITCODE=%ERRORLEVEL%"
echo.
if not "%EXITCODE%"=="0" (
  echo   *** The wizard stopped with exit code %EXITCODE%. ***
  echo   *** Please take a screenshot of this window and send it for support. ***
  echo.
)
pause
endlocal
