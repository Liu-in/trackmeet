@echo off
rem ===========================================================================
rem  TrackMeet backend start script (used by the installed application)
rem  Working directory is the install root; config is read from config\.
rem  Logs: logs\backend.out.log and logs\trackmeet.log
rem ===========================================================================
setlocal
cd /d "%~dp0.."
set "APPDIR=%CD%"
set "JAVA=%APPDIR%\runtime\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"
if not exist "%APPDIR%\logs" mkdir "%APPDIR%\logs"

set "JAVA_OPTS=-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -Xms256m -Xmx1024m"
set "APP_ARGS=--spring.profiles.active=prod --spring.config.additional-location=file:./config/ --trackmeet.serve-ui=true --trackmeet.init-scripts-dir=sql"

start "TrackMeet" /min "%JAVA%" %JAVA_OPTS% -jar "%APPDIR%\backend\app.jar" %APP_ARGS% >> "%APPDIR%\logs\backend.out.log" 2>&1
endlocal
