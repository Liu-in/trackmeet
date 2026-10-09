# ============================================================================
# TrackMeet 一键启动脚本（Windows PowerShell）
# 用法： pwsh -File deploy/scripts/start.ps1 [up|down|restart|logs|ps]
# ============================================================================
param(
    [ValidateSet('up', 'down', 'restart', 'logs', 'ps')]
    [string]$Action = 'up'
)

$ErrorActionPreference = 'Stop'
$deployDir = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $deployDir '.env'

if (-not (Test-Path $envFile)) {
    Copy-Item (Join-Path $deployDir '.env.example') $envFile
    Write-Host "已根据 .env.example 生成 $envFile，请按需修改后重新执行。" -ForegroundColor Yellow
}

Push-Location $deployDir
try {
    switch ($Action) {
        'up'      { docker compose up -d --build }
        'down'    { docker compose down }
        'restart' { docker compose restart }
        'logs'    { docker compose logs -f --tail=200 }
        'ps'      { docker compose ps }
    }
    if ($Action -eq 'up') {
        Write-Host ''
        Write-Host '等待服务就绪...' -ForegroundColor Cyan
        Start-Sleep -Seconds 10
        docker compose ps
        Write-Host ''
        Write-Host '后端健康检查: http://localhost:8080/actuator/health' -ForegroundColor Green
        Write-Host '默认账号: admin / Admin@123（首次登录请立即修改）' -ForegroundColor Green
        Write-Host '首次启动会自动执行 sql/01~06 脚本，MySQL 初始化约需 30-60 秒。' -ForegroundColor Yellow
    }
}
finally {
    Pop-Location
}
