@echo off
rem ============================================================================
rem TrackMeet 后端停止脚本（安装包使用）：仅结束运行 app.jar 的 java 进程
rem ============================================================================
setlocal
cd /d "%~dp0.."

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$procs = Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*app.jar*' };" ^
  "if (-not $procs) { Write-Host '未发现运行中的 TrackMeet 服务'; exit 0 }" ^
  "foreach ($p in $procs) { Write-Host ('停止进程 PID ' + $p.ProcessId); Stop-Process -Id $p.ProcessId -Force }" ^
  "Write-Host 'TrackMeet 服务已停止'"

endlocal
