#!/usr/bin/env pwsh
<#
===============================================================================
 TrackMeet 数据库逻辑备份脚本（Windows PowerShell 5.1+ / PowerShell 7+）
-------------------------------------------------------------------------------
 Usage:
   powershell -ExecutionPolicy Bypass -File deploy\scripts\backup.ps1 [-Keep <N>] [-OutDir <DIR>] [-Container <NAME>]
   pwsh      -File deploy/scripts/backup.ps1 -Keep 7 -OutDir D:\backup

 参数:
   -Keep <N>        保留最近 N 份备份，超出的旧备份自动删除（默认 30，或 .env 的 BACKUP_KEEP）
   -OutDir <DIR>    备份输出目录（默认 .env 的 BACKUP_DIR，否则 deploy\backups）
   -Container <名>  MySQL 容器名（默认 .env 的 MYSQL_CONTAINER，否则 trackmeet-mysql）
   -Help            显示本帮助

 示例:
   .\backup.ps1
   .\backup.ps1 -Keep 7 -OutDir D:\backup
   .\backup.ps1 -Keep 0            # 0 = 不做清理，全部保留

 产出:
   <OutDir>\trackmeet_<库名>_<yyyyMMdd_HHmmss>.sql.gz

 说明:
   1) 口令不落命令行：容器内生成临时 --defaults-extra-file（0600），脚本结束时删除；
   2) 使用 --single-transaction（InnoDB 一致性快照）、--routines --triggers --events、
      --set-gtid-purged=OFF、--default-character-set=utf8mb4；
   3) 采用「先 dump 到宿主机 .sql（字节级管道，避免编码损坏）→ .NET GZipStream 压缩 → 校验」
      三步流程，压缩包与 bash 版本命名一致（.sql.gz），可用任一端恢复；
   4) 备份完成后做 gzip 完整性校验，并打印压缩前后大小；
   5) upload\ 附件目录（如存在）不在本脚本范围，请另行打包；
   6) 退出码：0 成功 / 1 参数或环境错误 / 2 备份执行失败。
===============================================================================
#>
[CmdletBinding()]
param(
    [Alias('KeepCount')]
    [int]$Keep = -1,
    [string]$OutDir = '',
    [string]$Container = '',
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Off

$ScriptPath = $MyInvocation.MyCommand.Path
$ScriptDir = Split-Path -Parent $ScriptPath
$DeployDir = Split-Path -Parent $ScriptDir

function Write-Log  { param([string]$Message) Write-Host ("[{0}] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) }
function Write-Warn { param([string]$Message) Write-Host ("[{0}] [警告] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) -ForegroundColor Yellow }
function Fail       { param([string]$Message, [int]$Code = 1) Write-Host ("[{0}] [错误] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) -ForegroundColor Red; exit $Code }

function Show-Usage { Get-Help $ScriptPath -Full | Out-String | Write-Host }

if ($Help) { Show-Usage; exit 0 }

# ---------------------------------------------------------------------------
# .env 解析（键=值，忽略注释与空行；不覆盖已存在的环境变量）
# ---------------------------------------------------------------------------
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

$EnvFile = Join-Path $DeployDir '.env'
$EnvMap = @{}
if (Test-Path -LiteralPath $EnvFile) {
    $EnvMap = Read-EnvFile -Path $EnvFile
} else {
    Write-Warn "未找到 $EnvFile，将仅使用环境变量/默认值（建议先 Copy-Item .env.example .env）"
}

$KeepCount  = if ($Keep -ge 0) { $Keep } else { [int](Get-Setting -EnvMap $EnvMap -Key 'BACKUP_KEEP' -Default '30') }
$ContainerName = if ($Container -ne '') { $Container } else { Get-Setting -EnvMap $EnvMap -Key 'MYSQL_CONTAINER' -Default 'trackmeet-mysql' }
$DbName     = Get-Setting -EnvMap $EnvMap -Key 'DB_NAME' -Default 'trackmeet'
$DbUser     = Get-Setting -EnvMap $EnvMap -Key 'DB_USERNAME' -Default ''
$DbPassword = Get-Setting -EnvMap $EnvMap -Key 'DB_PASSWORD' -Default ''

$OutDirRaw = if ($OutDir -ne '') { $OutDir } else { Get-Setting -EnvMap $EnvMap -Key 'BACKUP_DIR' -Default 'backups' }
if ([System.IO.Path]::IsPathRooted($OutDirRaw)) { $BackupDir = $OutDirRaw } else { $BackupDir = Join-Path $DeployDir $OutDirRaw }

$Stamp      = Get-Date -Format 'yyyyMMdd_HHmmss'
$TargetGz   = Join-Path $BackupDir ("trackmeet_{0}_{1}.sql.gz" -f $DbName, $Stamp)
$TempSql    = Join-Path $BackupDir ("trackmeet_{0}_{1}.sql" -f $DbName, $Stamp)
$ContainerCnf = "/tmp/.trackmeet_backup_$Stamp.cnf"

if ($KeepCount -lt 0) { Fail "-Keep / BACKUP_KEEP 必须是非负整数，当前：$KeepCount" 1 }
if ($DbUser -eq '')     { Fail "缺少 DB_USERNAME：请在 $EnvFile 中配置（参考 .env.example）" 1 }
if ($DbPassword -eq '') { Fail "缺少 DB_PASSWORD：请在 $EnvFile 中配置（参考 .env.example）" 1 }

# ---------------------------------------------------------------------------
# 前置校验
# ---------------------------------------------------------------------------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail '未找到 docker 命令：请先安装 Docker 24+ / Docker Desktop' 1 }

$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
docker inspect $ContainerName *> $null
$inspectCode = $LASTEXITCODE
$ErrorActionPreference = $prevEap
if ($inspectCode -ne 0) { Fail "MySQL 容器不存在：$ContainerName（请先 docker compose up -d mysql，或用 -Container 指定）" 1 }

$ErrorActionPreference = 'Continue'
$running = (docker inspect -f '{{.State.Running}}' $ContainerName 2>$null | Select-Object -First 1)
$ErrorActionPreference = $prevEap
if ("$running".Trim() -ne 'true') { Fail "MySQL 容器未运行：$ContainerName（docker compose start mysql）" 1 }

if (-not (Test-Path -LiteralPath $BackupDir)) {
    try { New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null }
    catch { Fail "无法创建备份目录：$BackupDir（$($_.Exception.Message)）" 1 }
}

# ---------------------------------------------------------------------------
# 工具函数：容器内口令文件 / gzip
# ---------------------------------------------------------------------------
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

function Compress-GzipFile {
    param([string]$Source, [string]$Destination)
    $in = [System.IO.File]::OpenRead($Source)
    try {
        $out = [System.IO.File]::Create($Destination)
        try {
            $gz = New-Object System.IO.Compression.GZipStream($out, [System.IO.Compression.CompressionMode]::Compress)
            try { $in.CopyTo($gz) } finally { $gz.Dispose() }
        } finally { $out.Dispose() }
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

function Get-MbText {
    param([long]$Bytes)
    return ('{0:N2} MB ({1} 字节)' -f ($Bytes / 1MB), $Bytes)
}

# ---------------------------------------------------------------------------
# 执行备份（异常统一在下方 catch 中清理并退出，确保容器内临时口令文件被删除）
# ---------------------------------------------------------------------------
try {
    try {
        Write-Log '准备 MySQL 容器内临时口令文件 ...'
        Set-ContainerDefaultsFile

        Write-Log "开始备份数据库 [$DbName]（容器 $ContainerName）→ $TargetGz"
        $dumpCmd = "mysqldump --defaults-extra-file='$ContainerCnf' " +
                   "--single-transaction --quick --routines --triggers --events " +
                   "--set-gtid-purged=OFF --default-character-set=utf8mb4 " +
                   "--hex-blob --no-create-db '$DbName'"

        $ErrorActionPreference = 'Continue'
        # Windows PowerShell 5.1 使用 -Encoding Byte（字节级写盘，避免 UTF-8 编码转换损坏 dump）
        docker exec $ContainerName sh -c $dumpCmd | Set-Content -LiteralPath $TempSql -Encoding Byte
        $dumpCode = $LASTEXITCODE
        $ErrorActionPreference = $prevEap

        if ($dumpCode -ne 0) { throw "mysqldump 执行失败（退出码 $dumpCode）；请检查库名 [$DbName] 与账号 [$DbUser] 是否存在" }
        if (-not (Test-Path -LiteralPath $TempSql)) { throw "未生成备份文件：$TempSql" }
        $rawBytes = (Get-Item -LiteralPath $TempSql).Length
        if ($rawBytes -le 0) { throw '备份内容为空，备份失败（请检查库名与账号权限）' }

        Write-Log '压缩备份文件（gzip）...'
        if (Test-Path -LiteralPath $TargetGz) { Remove-Item -LiteralPath $TargetGz -Force }
        Compress-GzipFile -Source $TempSql -Destination $TargetGz
        Remove-Item -LiteralPath $TempSql -Force

        $gzBytes = (Get-Item -LiteralPath $TargetGz).Length
        if ($gzBytes -le 0) { throw "备份文件为空，备份失败：$TargetGz" }

        if (Test-GzipFile -Path $TargetGz) {
            Write-Log 'gzip 完整性校验：通过'
        } else {
            throw "gzip 完整性校验失败，备份文件可能损坏：$TargetGz"
        }

        Write-Log "备份完成：$TargetGz"
        Write-Log ("文件大小：压缩后 {0}，原始 {1}" -f (Get-MbText -Bytes $gzBytes), (Get-MbText -Bytes $rawBytes))
        Write-Log '提示：恢复与表数量校验请使用 restore.ps1（会自动比对 information_schema 表数量）。'

        $uploadDirs = @((Join-Path $DeployDir 'upload'), (Join-Path (Split-Path -Parent $DeployDir) 'upload'))
        foreach ($u in $uploadDirs) {
            if (Test-Path -LiteralPath $u) {
                Write-Warn "检测到附件目录：$u`n       本脚本只备份数据库，附件请另行打包（例如 Compress-Archive -Path '$u' -DestinationPath '$BackupDir\upload_$Stamp.zip'）"
                break
            }
        }

        # -----------------------------------------------------------------------
        # 保留策略：按最后写入时间保留最新 KeepCount 份
        # -----------------------------------------------------------------------
        if ($KeepCount -eq 0) {
            Write-Log '保留策略：-Keep 0，跳过清理（历史备份全部保留）'
        } else {
            Write-Log "保留策略：仅保留最新 $KeepCount 份（匹配 trackmeet_${DbName}_*.sql.gz）"
            $all = @(Get-ChildItem -LiteralPath $BackupDir -Filter ("trackmeet_{0}_*.sql.gz" -f $DbName) -File -ErrorAction SilentlyContinue |
                     Sort-Object LastWriteTime -Descending)
            if ($all.Count -gt $KeepCount) {
                $obsolete = $all | Select-Object -Skip $KeepCount
                foreach ($f in $obsolete) {
                    Remove-Item -LiteralPath $f.FullName -Force
                    Write-Log "已删除过期备份：$($f.Name)"
                }
            } else {
                Write-Log "无需清理（当前份数 $($all.Count) 未超过 $KeepCount）"
            }
        }

        Write-Log '当前备份目录内容：'
        Get-ChildItem -LiteralPath $BackupDir -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 10 |
            ForEach-Object { Write-Host ("  {0}  {1}" -f $_.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'), $_.Name) }
    } catch {
        $msg = "$($_.Exception.Message)"
        if ($msg -match '^(gzip|mysqldump|备份内容|未生成备份文件|备份文件为空)') { Fail "[备份失败] $msg" 2 }
        Fail "[备份异常] $msg" 2
    }
}
finally {
    Remove-ContainerDefaultsFile
    if (Test-Path -LiteralPath $TempSql) { Remove-Item -LiteralPath $TempSql -Force -ErrorAction SilentlyContinue }
}

exit 0
