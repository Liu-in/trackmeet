@echo off
rem ============================================================================
rem TrackMeet 后端启动脚本（安装包使用）
rem   工作目录固定为安装根目录，配置读取 config\application-prod.yml
rem   日志输出到 logs\backend.out.log 与 logs\trackmeet.log
rem ============================================================================
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
