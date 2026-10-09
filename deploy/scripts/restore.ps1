#!/usr/bin/env pwsh
<#
===============================================================================
 TrackMeet 数据库恢复脚本（Windows PowerShell 5.1+ / PowerShell 7+）
-------------------------------------------------------------------------------
 Usage:
   powershell -ExecutionPolicy Bypass -File deploy\scripts\restore.ps1 -BackupFile <备份文件.sql.gz|.sql> [-Yes] [-NoSafety] [-Keep <N>] [-Container <名>]
   pwsh      -File deploy/scripts/restore.ps1 -BackupFile deploy/backups/xxx.sql.gz -Yes

 参数:
   -BackupFile <路径>  要恢复的备份文件（.sql.gz 或 .sql），必填
   -Yes                跳过交互式二次确认（自动化/演练用，请先确认目标库正确）
   -NoSafety           恢复前不做安全备份（默认会先备份当前库）
   -Keep <N>           安全备份保留份数（默认 30，透传给 backup.ps1）
   -Container <名>     MySQL 容器名（默认 .env 的 MYSQL_CONTAINER）
   -Help               显示本帮助

 示例:
   .\restore.ps1 -BackupFile .\backups\trackmeet_trackmeet_20251018_093000.sql.gz
   .\restore.ps1 -BackupFile .\backups\xxx.sql.gz -Yes -Keep 60

 行为:
   1) 校验备份文件存在、非空，.gz 时做 gzip 完整性校验；
   2) 交互式二次确认（输入 y/yes 继续，除非 -Yes）；
   3) 恢复前对当前库做一次安全备份（除非 -NoSafety）；
   4) 解压到临时 .sql，用 cmd 输入重定向导入（字节级，避免编码转换损坏中文数据）；
   5) 校验：比对备份内 CREATE TABLE 数量与 information_schema 实际表数量；
   6) 退出码：0 成功 / 1 参数或环境错误 / 2 备份或恢复失败 / 3 用户取消。
===============================================================================
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$BackupFile = '',
    [switch]$Yes,
    [switch]$NoSafety,
    [Alias('KeepCount')]
    [int]$Keep = -1,
    [string]$Container = '',
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Off

$ScriptPath = $MyInvocation.MyCommand.Path
$ScriptDir = Split-Path -Parent $ScriptPath
$DeployDir = Split-Path -Parent $ScriptDir
$prevEap = $ErrorActionPreference

function Write-Log  { param([string]$Message) Write-Host ("[{0}] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) }
function Write-Warn { param([string]$Message) Write-Host ("[{0}] [警告] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) -ForegroundColor Yellow }
function Fail       { param([string]$Message, [int]$Code = 1) Write-Host ("[{0}] [错误] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) -ForegroundColor Red; exit $Code }
function Show-Usage { Get-Help $ScriptPath -Full | Out-String | Write-Host }

if ($Help) { Show-Usage; exit 0 }

function Read-EnvFile {
    param([string]$Path)
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $result }
    foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $idx = $t.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key = $t.Substring(0, $idx).Trim()
        $val = $t.Substring($idx + 1).Trim()
        if ($val.Length -ge 2 -and (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'")))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        $result[$key] = $val
    }
    return $result
}

function Get-Setting {
    param([hashtable]$EnvMap, [string]$Key, [string]$Default = '')
    $fromEnv = [Environment]::GetEnvironmentVariable($Key)
    if (-not [string]::IsNullOrEmpty($fromEnv)) { return $fromEnv }
    if ($EnvMap.ContainsKey($Key) -and $EnvMap[$Key] -ne '') { return [string]$EnvMap[$Key] }
    return $Default
}

function Expand-GzipFile {
    param([string]$Source, [string]$Destination)
    $in = [System.IO.File]::OpenRead($Source)
    try {
        $gz = New-Object System.IO.Compression.GZipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
        try {
            $out = [System.IO.File]::Create($Destination)
            try { $gz.CopyTo($out) } finally { $out.Dispose() }
        } finally { $gz.Dispose() }
    } finally { $in.Dispose() }
}

function Test-GzipFile {
    param([string]$Path)
    try {
        $in = [System.IO.File]::OpenRead($Path)
        try {
            $gz = New-Object System.IO.Compression.GZipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
            try {
                $buf = New-Object byte[] 65536
                while ($gz.Read($buf, 0, $buf.Length) -gt 0) { }
            } finally { $gz.Dispose() }
        } finally { $in.Dispose() }
        return $true
    } catch { return $false }
}

# ---------------------------------------------------------------------------
# 参数与 .env
# ---------------------------------------------------------------------------
$BackupFile = "$BackupFile".Trim()
if ($BackupFile -eq '') { Show-Usage; Fail '缺少参数 -BackupFile：请指定要恢复的备份文件' 1 }

$EnvFile = Join-Path $DeployDir '.env'
$EnvMap = @{}
if (Test-Path -LiteralPath $EnvFile) {
    $EnvMap = Read-EnvFile -Path $EnvFile
} else {
    Write-Warn "未找到 $EnvFile，将仅使用环境变量/默认值"
}

$ContainerName = if ($Container -ne '') { $Container } else { Get-Setting -EnvMap $EnvMap -Key 'MYSQL_CONTAINER' -Default 'trackmeet-mysql' }
$DbName     = Get-Setting -EnvMap $EnvMap -Key 'DB_NAME' -Default 'trackmeet'
$DbUser     = Get-Setting -EnvMap $EnvMap -Key 'DB_USERNAME' -Default ''
$DbPassword = Get-Setting -EnvMap $EnvMap -Key 'DB_PASSWORD' -Default ''
$KeepCount  = if ($Keep -ge 0) { $Keep } else { [int](Get-Setting -EnvMap $EnvMap -Key 'BACKUP_KEEP' -Default '30') }

# ---------------------------------------------------------------------------
# 前置校验
# ---------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) { Fail "备份文件不存在：$BackupFile" 1 }
$SrcItem = Get-Item -LiteralPath $BackupFile
if ($SrcItem.Length -le 0) { Fail "备份文件为空：$BackupFile" 1 }
if ($DbUser -eq '')     { Fail "缺少 DB_USERNAME：请在 $EnvFile 中配置" 1 }
if ($DbPassword -eq '') { Fail "缺少 DB_PASSWORD：请在 $EnvFile 中配置" 1 }
if ($KeepCount -lt 0)   { Fail "-Keep / BACKUP_KEEP 必须是非负整数，当前：$KeepCount" 1 }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail '未找到 docker 命令：请先安装 Docker 24+' 1 }

$ErrorActionPreference = 'Continue'
docker inspect $ContainerName *> $null
$inspectCode = $LASTEXITCODE
$ErrorActionPreference = $prevEap
if ($inspectCode -ne 0) { Fail "MySQL 容器不存在：$ContainerName" 1 }

$ErrorActionPreference = 'Continue'
$running = (docker inspect -f '{{.State.Running}}' $ContainerName 2>$null | Select-Object -First 1)
$ErrorActionPreference = $prevEap
if ("$running".Trim() -ne 'true') { Fail "MySQL 容器未运行：$ContainerName（docker compose start mysql）" 1 }

$IsGzip = $false
if ($BackupFile -match '\.(gz|tgz)$') { $IsGzip = $true }
if ($IsGzip -and -not (Test-GzipFile -Path $BackupFile)) { Fail "gzip 完整性校验失败，备份文件可能损坏：$BackupFile" 1 }

$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$ContainerCnf = "/tmp/.trackmeet_restore_$Stamp.cnf"
$TempSql = Join-Path $env:TEMP ("trackmeet_restore_{0}.sql" -f $Stamp)

# ---------------------------------------------------------------------------
# 二次确认
# ---------------------------------------------------------------------------
Write-Log '即将恢复：'
Write-Log "  备份文件 : $BackupFile（$([math]::Round($SrcItem.Length / 1MB, 2)) MB）"
Write-Log "  目标容器 : $ContainerName"
Write-Log "  目标库   : $DbName"
Write-Log ("  安全备份 : {0}" -f $(if ($NoSafety) { '否（-NoSafety）' } else { '是（恢复前自动备份当前库）' }))
if (-not $Yes) {
    Write-Host '确认要继续吗？该操作会覆盖目标库中的同名表与数据！输入 yes 继续：' -NoNewline
    $answer = Read-Host
    if ($answer -notmatch '^(y|Y|yes|YES|Yes)$') { Fail '用户取消恢复操作。' 3 }
    Write-Log '已确认，继续执行。'
}

function Set-ContainerDefaultsFile {
    $content = "[client]`nuser=$DbUser`npassword=`"$DbPassword`"`n"
    $ErrorActionPreference = 'Continue'
    $content | docker exec -i $ContainerName sh -c "umask 077; cat > '$ContainerCnf'"
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($code -ne 0) { Fail "写入容器内临时口令文件失败：$ContainerName" 2 }
}

function Remove-ContainerDefaultsFile {
    $ErrorActionPreference = 'Continue'
    docker exec $ContainerName sh -c "rm -f '$ContainerCnf'" *> $null
    $ErrorActionPreference = $prevEap
}

try {
    # -----------------------------------------------------------------------
    # 1/4 恢复前安全备份
    # -----------------------------------------------------------------------
    if (-not $NoSafety) {
        Write-Log '== 1/4 恢复前安全备份（备份失败将中止恢复）=='
        $backupScript = Join-Path $ScriptDir 'backup.ps1'
        if (-not (Test-Path -LiteralPath $backupScript)) { Fail "未找到备份脚本：$backupScript" 2 }
        $ErrorActionPreference = 'Continue'
        & $backupScript -KeepCount $KeepCount -Container $ContainerName
        $backupCode = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        if ($backupCode -ne 0) { Fail "恢复前的安全备份失败（退出码 $backupCode），已中止恢复（避免无回滚点）" 2 }
    } else {
        Write-Log '== 1/4 已跳过恢复前安全备份（-NoSafety）=='
    }

    # -----------------------------------------------------------------------
    # 2/4 解压
    # -----------------------------------------------------------------------
    Write-Log '== 2/4 解压备份文件到临时文件 =='
    if ($IsGzip) { Expand-GzipFile -Source $BackupFile -Destination $TempSql }
    else { Copy-Item -LiteralPath $BackupFile -Destination $TempSql -Force }
    if (-not (Test-Path -LiteralPath $TempSql)) { Fail '解压失败：未生成临时 SQL 文件' 2 }
    $sqlBytes = (Get-Item -LiteralPath $TempSql).Length
    if ($sqlBytes -le 0) { Fail "解压后 SQL 为空，备份文件可能损坏：$BackupFile" 2 }
    $expectTables = (Select-String -LiteralPath $TempSql -Pattern '^CREATE TABLE' -ErrorAction SilentlyContinue | Measure-Object).Count
    Write-Log "解压完成：$sqlBytes 字节，备份内 CREATE TABLE 数量：$expectTables"

    Write-Log '准备 MySQL 容器内临时口令文件 ...'
    Set-ContainerDefaultsFile

    # -----------------------------------------------------------------------
    # 3/4 导入（PowerShell 文本管道 + UTF-8 无 BOM，实测字节与源文件一致）
    # -----------------------------------------------------------------------
    Write-Log "== 3/4 导入数据库 [$DbName]（大库可能需要数分钟，请勿中断）=="
    $mysqlCmd = "mysql --defaults-extra-file='$ContainerCnf' --default-character-set=utf8mb4 --show-warnings '$DbName'"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $savedConsole = [Console]::OutputEncoding
    $savedOutputEncoding = $OutputEncoding
    [Console]::OutputEncoding = $utf8NoBom
    $OutputEncoding = $utf8NoBom
    $ErrorActionPreference = 'Continue'
    try {
        Get-Content -LiteralPath $TempSql -Encoding UTF8 | docker exec -i $ContainerName sh -c $mysqlCmd
        $importCode = $LASTEXITCODE
    } finally {
        [Console]::OutputEncoding = $savedConsole
        $OutputEncoding = $savedOutputEncoding
        $ErrorActionPreference = $prevEap
    }
    if ($importCode -ne 0) { Fail "导入失败（退出码 $importCode）；请检查备份完整性、目标库是否存在、字符集是否一致" 2 }

    # -----------------------------------------------------------------------
    # 4/4 校验
    # -----------------------------------------------------------------------
    Write-Log '== 4/4 恢复结果校验 =='
    $countSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName';"
    $ErrorActionPreference = 'Continue'
    $actualRaw = (docker exec $ContainerName sh -c "mysql --defaults-extra-file='$ContainerCnf' -N -B -e `"$countSql`" 2>/dev/null" 2>$null | Select-Object -First 1)
    $ErrorActionPreference = $prevEap
    $actual = 0
    if (-not [string]::IsNullOrWhiteSpace("$actualRaw")) { [int]::TryParse("$actualRaw".Trim(), [ref]$actual) | Out-Null }
    Write-Log "information_schema 实际表数量：$actual（备份内 CREATE TABLE：$expectTables）"
    if ($actual -gt 0) {
        Write-Log "表数量校验：通过（存在 $actual 张表）"
    } else {
        Write-Warn "目标库 [$DbName] 中没有任何表，恢复可能未生效，请检查备份内容与库名"
    }

    Write-Log '恢复完成。建议按顺序执行：'
    Write-Log '  1) docker compose restart backend'
    Write-Log '  2) powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1'
}
finally {
    Remove-ContainerDefaultsFile
    if (Test-Path -LiteralPath $TempSql) { Remove-Item -LiteralPath $TempSql -Force -ErrorAction SilentlyContinue }
}

exit 0
