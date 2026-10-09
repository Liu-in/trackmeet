<#
.SYNOPSIS
    TrackMeet 卸载清理脚本（由安装包的卸载程序自动调用）。

.DESCRIPTION
    停止服务 → 删除开机自启计划任务 → 删除防火墙规则。
    **不删除数据库**（避免误删成绩数据），仅打印库名与清理方法；日志与备份目录会随安装目录一并移除，
    如需保留请在卸载前复制到别处。
#>
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$AppDir)

$ErrorActionPreference = 'Continue'
Write-Host '=== TrackMeet 卸载清理 ===' -ForegroundColor Cyan

# 1. 停止服务
$procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*app.jar*' }
if ($procs) {
    foreach ($p in $procs) {
        try { Stop-Process -Id $p.ProcessId -Force; Write-Host "  已停止进程 PID $($p.ProcessId)" }
        catch { Write-Host "  停止进程失败：$($_.Exception.Message)" }
    }
} else {
    Write-Host '  服务未在运行'
}

# 2. 删除开机自启计划任务
$taskName = 'TrackMeet-Backend'
$task = schtasks.exe /Query /TN $taskName 2>$null
if ($LASTEXITCODE -eq 0) {
    schtasks.exe /Delete /TN $taskName /F | Out-Null
    Write-Host "  已删除计划任务 $taskName"
} else {
    Write-Host '  计划任务不存在'
}

# 3. 删除防火墙规则
$rules = Get-NetFirewallRule -DisplayName 'TrackMeet Web*' -ErrorAction SilentlyContinue
if ($rules) {
    $rules | Remove-NetFirewallRule -ErrorAction SilentlyContinue
    Write-Host "  已删除防火墙规则 $($rules.Count) 条"
} else {
    Write-Host '  防火墙规则不存在'
}

# 4. 保留数据提示
Write-Host ''
Write-Host '提示：数据库未删除。如需彻底清理，请执行（请先确认无需保留成绩数据）：' -ForegroundColor Yellow
Write-Host '  mysql -u root -p -e "DROP DATABASE IF EXISTS trackmeet;"'
Write-Host '卸载前如需保留数据，请先备份：mysqldump -u root -p --databases trackmeet > trackmeet_backup.sql'
Write-Host '安装目录中的 config（配置）与 logs（日志）将随卸载一并删除。'
Write-Host '=== 卸载清理完成 ===' -ForegroundColor Cyan
