@echo off
rem ===========================================================================
rem  TrackMeet backend stop script: kills only java processes running app.jar
rem ===========================================================================
setlocal
cd /d "%~dp0.."

powershell -NoProfile -ExecutionPolicy Bypass -Command "$procs = Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*app.jar*' }; if (-not $procs) { Write-Host 'TrackMeet service is not running'; exit 0 }; foreach ($p in $procs) { Write-Host ('Stopping PID ' + $p.ProcessId); Stop-Process -Id $p.ProcessId -Force }; Write-Host 'TrackMeet service stopped'"

endlocal
