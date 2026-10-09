<#
.SYNOPSIS
    TrackMeet 安装期配置脚本（由安装包自动调用，也可手工执行）。

.DESCRIPTION
    1) 写入 config\application-prod.yml（数据源 / 端口 / 初始口令 / 单进程托管前端 / 日志）
    2) 首次安装时调用后端内置初始化器创建数据库、建表并导入初始数据（可重复执行）
    3) 注册开机自启（计划任务）与防火墙入站规则
    4) 启动服务并等待健康检查通过，打印访问地址

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\configure.ps1 -AppDir "C:\Program Files\TrackMeet" `
        -DbHost 127.0.0.1 -DbPort 3306 -DbUser root -DbPassword *** -WebPort 8080 -AdminPassword Admin@123 -InitDb 1
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$AppDir,
    [string]$DbHost = '127.0.0.1',
    [string]$DbPort = '3306',
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$DbName = 'trackmeet',
    [string]$WebPort = '8080',
    [string]$AdminPassword = 'Admin@123',
    [string]$InitDb = '1',
    [string]$AutoStart = '1',
    [string]$Firewall = '1',
    [string]$SkipDemo = '0'
)

$ErrorActionPreference = 'Stop'
$AppDir = (Resolve-Path $AppDir).Path
$ConfigDir = Join-Path $AppDir 'config'
$LogDir = Join-Path $AppDir 'logs'
$Java = Join-Path $AppDir 'runtime\bin\java.exe'
if (-not (Test-Path $Java)) { $Java = 'java' }
$Jar = Join-Path $AppDir 'backend\app.jar'
if (-not (Test-Path $Jar)) { throw "未找到后端程序：$Jar" }

New-Item -ItemType Directory -Path $ConfigDir -Force | Out-Null
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

function Write-Step([string]$t) { Write-Host "=== $t ===" -ForegroundColor Cyan }
function Write-Ok([string]$t)   { Write-Host "  [OK] $t" -ForegroundColor Green }
function Write-Warn2([string]$t){ Write-Host "  [警告] $t" -ForegroundColor Yellow }

# ---------------------------------------------------------------------------
# 1. 生成配置
# ---------------------------------------------------------------------------
Write-Step '写入配置 config\application-prod.yml'

$secret = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 48 | ForEach-Object { [char]$_ })
$configPath = Join-Path $ConfigDir 'application-prod.yml'
$config = @"
# 由 TrackMeet 安装程序生成，可手工调整后重启服务生效
server:
  port: $WebPort

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&allowMultiQueries=true"
    username: $DbUser
    password: "$DbPassword"
    hikari:
      pool-name: TrackMeetHikariPool
      minimum-idle: 5
      maximum-pool-size: 30
      connection-timeout: 30000
      initialization-fail-timeout: 1

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl

trackmeet:
  # 单进程部署：由后端直接托管前端静态资源（frontend 目录）
  serve-ui: true
  jwt:
    secret: $secret
  cors:
    allowed-origins: "*"
  default-password: $AdminPassword
  pdf:
    font-path: ""
  # 首次安装由安装程序以命令行参数开启，日常运行为 false
  init-db: false
  init-scripts-dir: sql

logging:
  level:
    root: info
    com.trackmeet: info
  file:
    name: $($LogDir -replace '\\','/')/trackmeet.log
"@
Set-Content -Path $configPath -Value $config -Encoding UTF8
Write-Ok $configPath

# ---------------------------------------------------------------------------
# 2. 初始化数据库
# ---------------------------------------------------------------------------
if ($InitDb -eq '1') {
    Write-Step '初始化数据库（建库 / 建表 / 初始数据，可重复执行）'
    $skipDemoArg = if ($SkipDemo -eq '1') { '--trackmeet.init-db-skip-demo=true' } else { '--trackmeet.init-db-skip-demo=false' }
    $initArgs = @(
        '-Dfile.encoding=UTF-8',
        '-jar', $Jar,
        '--spring.profiles.active=prod',
        "--spring.config.additional-location=file:$($ConfigDir -replace '\\','/')/",
        '--spring.main.web-application-type=none',
        '--trackmeet.init-db=true',
        '--trackmeet.init-db-host=' + $DbHost,
        '--trackmeet.init-db-port=' + $DbPort,
        '--trackmeet.init-scripts-dir=' + (Join-Path $AppDir 'sql'),
        '--spring.datasource.hikari.initialization-fail-timeout=-1',
        '--spring.datasource.hikari.minimum-idle=0',
        $skipDemoArg
    )
    $initLog = Join-Path $LogDir 'init-db.log'
    Write-Host "  执行：$Java $($initArgs -join ' ')" -ForegroundColor DarkGray
    & $Java @initArgs *>&1 | Tee-Object -FilePath $initLog
    if ($LASTEXITCODE -ne 0) {
        Write-Warn2 "数据库初始化未成功（退出码 $LASTEXITCODE），详见 $initLog"
        Write-Warn2 '常见原因：MySQL 未启动、账号口令错误、端口不通、库已存在但结构不一致。修正后可重新运行本脚本。'
    } else {
        Write-Ok "数据库已就绪（日志：$initLog）"
    }
} else {
    Write-Warn2 '已跳过数据库初始化（-InitDb 0）'
}

# ---------------------------------------------------------------------------
# 3. 开机自启
# ---------------------------------------------------------------------------
$TaskName = 'TrackMeet-Backend'
if ($AutoStart -eq '1') {
    Write-Step "注册开机自启（计划任务 $TaskName）"
    $startCmd = Join-Path $AppDir 'scripts\start-backend.cmd'
    $action = "cmd.exe /c `"$startCmd`""
    & schtasks.exe /Create /TN $TaskName /TR $action /SC ONSTART /RU SYSTEM /RL HIGHEST /F | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Ok '已注册（开机自动启动）' } else { Write-Warn2 '计划任务注册失败，可手工运行 scripts\start-backend.cmd 启动' }
} else {
    Write-Warn2 '未注册开机自启（安装时可勾选，或稍后手工执行 schtasks 命令）'
}

# ---------------------------------------------------------------------------
# 4. 防火墙
# ---------------------------------------------------------------------------
if ($Firewall -eq '1') {
    Write-Step "添加防火墙入站规则（TCP $WebPort）"
    try {
        $ruleName = "TrackMeet Web $WebPort"
        Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
        New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP -LocalPort $WebPort | Out-Null
        Write-Ok "已放行 TCP $WebPort"
    } catch {
        Write-Warn2 "防火墙规则添加失败（可手工放行）：$($_.Exception.Message)"
    }
}

# ---------------------------------------------------------------------------
# 5. 启动并健康检查
# ---------------------------------------------------------------------------
Write-Step '启动服务并等待健康检查'
$startCmdPath = Join-Path $AppDir 'scripts\start-backend.cmd'
if ($AutoStart -eq '1') {
    & schtasks.exe /Run /TN $TaskName | Out-Null
    Write-Ok '已通过计划任务启动'
} else {
    Start-Process -FilePath 'cmd.exe' -ArgumentList "/c `"$startCmdPath`"" -WindowStyle Hidden
    Write-Ok '已启动（后台）'
}

$health = "http://127.0.0.1:$WebPort/actuator/health"
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest -Uri $health -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200 -and $response.Content -match 'UP') { $ready = $true; break }
    } catch { }
}
if ($ready) {
    Write-Ok "健康检查通过：$health"
    Write-Host ""
    Write-Host "  管理端：http://localhost:$WebPort/" -ForegroundColor White
    Write-Host "  默认账号：admin / $AdminPassword" -ForegroundColor White
    Write-Host "  大屏公告：http://localhost:$WebPort/screen/public-board?meetId=1&token=<在「大屏配置」中查看>" -ForegroundColor White
} else {
    Write-Warn2 "健康检查未通过（$health）。请查看日志：$LogDir\backend.out.log、$($LogDir)\trackmeet.log"
}

Write-Host ""
Write-Host "后续操作：" -ForegroundColor White
Write-Host "  启动：$startCmdPath"
Write-Host "  停止：$(Join-Path $AppDir 'scripts\stop-backend.cmd')"
Write-Host "  备份：deploy 目录下的 backup 脚本，或使用 MySQL 自带工具导出 $DbName 库"
