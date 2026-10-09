#!/usr/bin/env pwsh
<#
===============================================================================
 TrackMeet 增量补丁执行脚本（Windows PowerShell 5.1+ / PowerShell 7+）
-------------------------------------------------------------------------------
 适用场景：数据库卷中已存在数据（/docker-entrypoint-initdb.d 不再执行），
           但代码升级引入了新的 DDL / 权限点补丁（sql/07、08、09 …）。

 Usage:
   powershell -ExecutionPolicy Bypass -File deploy\scripts\apply-patches.ps1 [-From <NN>] [-Only <NN,NN>] [-List] [-DryRun] [-Yes] [-Container <名>] [-SqlDir <DIR>]
   pwsh      -File deploy/scripts/apply-patches.ps1 -From 09 -Yes

 参数:
   -From <NN>        从编号 NN 及之后的补丁开始执行（默认 07）
   -Only <NN,NN>     只执行指定编号的补丁，例如 -Only 08,09
   -List             仅列出补丁文件与「是否已生效」探测结果（不写库）
   -DryRun           打印将要执行的补丁清单与文件首部（不写库）
   -Yes              跳过交互确认
   -Container <名>   MySQL 容器名（默认 .env 的 MYSQL_CONTAINER）
   -SqlDir <DIR>     补丁目录（默认 <仓库>\sql）
   -Help             显示本帮助

 示例:
   .\apply-patches.ps1 -List
   .\apply-patches.ps1                    # 依次执行 07、08、09
   .\apply-patches.ps1 -From 09           # 只执行 09
   .\apply-patches.ps1 -Only 08,09 -Yes

 说明:
   1) sql/07、08、09 均为幂等脚本（INSERT ... WHERE NOT EXISTS / INSERT IGNORE /
      MODIFY COLUMN），重复执行安全；执行前仍建议先备份： .\backup.ps1
   2) 补丁文件内部自行 USE `trackmeet`；若改了 DB_NAME，请同步修改
      sql/0X-patch-*.sql 顶部的 USE 语句（本脚本按文件原样执行）；
   3) 探测标记：07→event:query、08→schedule:add、09→screen:board（取自 sys_permission）；
   4) 退出码：0 全部成功 / 1 参数或环境错误 / 2 某补丁执行失败（立即停止）/ 3 用户取消。
===============================================================================
#>
[CmdletBinding()]
param(
    [string]$From = '07',
    [string]$Only = '',
    [string]$Container = '',
    [string]$SqlDir = '',
    [switch]$List,
    [switch]$DryRun,
    [switch]$Yes,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Off

$ScriptPath = $MyInvocation.MyCommand.Path
$ScriptDir = Split-Path -Parent $ScriptPath
$DeployDir = Split-Path -Parent $ScriptDir
$RepoDir = Split-Path -Parent $DeployDir
$prevEap = $ErrorActionPreference

function Write-Log  { param([string]$Message) Write-Host ("[{0}] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) }
function Write-Warn { param([string]$Message) Write-Host ("[{0}] [警告] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) -ForegroundColor Yellow }
function Fail       { param([string]$Message, [int]$Code = 1) Write-Host ("[{0}] [错误] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) -ForegroundColor Red; exit $Code }
function Show-Usage { Get-Help $ScriptPath -Full | Out-String | Write-Host }

if ($Help) { Show-Usage; exit 0 }
if ($From -notmatch '^[0-9]{2}$') { Fail "-From 必须是两位编号（如 07），当前：$From" 1 }

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

function Get-MarkerOf {
    param([string]$Number)
    switch ($Number) {
        '07' { return 'event:query' }
        '08' { return 'schedule:add' }
        '09' { return 'screen:board' }
        default { return '' }
    }
}

# ---------------------------------------------------------------------------
# 参数与 .env
# ---------------------------------------------------------------------------
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

$SqlDirFull = if ($SqlDir -ne '') { $SqlDir } else { Join-Path $RepoDir 'sql' }
if (-not (Test-Path -LiteralPath $SqlDirFull)) { Fail "补丁目录不存在：$SqlDirFull（可用 -SqlDir 指定）" 1 }
if ($DbUser -eq '')     { Fail "缺少 DB_USERNAME：请在 $EnvFile 中配置" 1 }
if ($DbPassword -eq '') { Fail "缺少 DB_PASSWORD：请在 $EnvFile 中配置" 1 }

# ---------------------------------------------------------------------------
# 收集补丁文件
# ---------------------------------------------------------------------------
$all = @(Get-ChildItem -LiteralPath $SqlDirFull -Filter '*-patch-*.sql' -File |
         Where-Object { $_.Name -match '^[0-9]{2}-patch-' } |
         Sort-Object Name)
if ($all.Count -eq 0) { Fail "未找到补丁文件（$SqlDirFull\[0-9][0-9]-patch-*.sql）" 1 }

$onlyList = @()
if ($Only -ne '') { $onlyList = @($Only.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }) }

$selected = @()
foreach ($f in $all) {
    $num = $f.Name.Substring(0, 2)
    if ($onlyList.Count -gt 0) {
        if ($onlyList -contains $num) { $selected += $f }
    } elseif ($num -ge $From) {
        $selected += $f
    }
}
if ($selected.Count -eq 0) { Fail "没有匹配的补丁（-From $From / -Only $Only）" 1 }

# ---------------------------------------------------------------------------
# 容器状态
# ---------------------------------------------------------------------------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail '未找到 docker 命令：请先安装 Docker 24+' 1 }

$ErrorActionPreference = 'Continue'
docker inspect $ContainerName *> $null
$inspectCode = $LASTEXITCODE
$ErrorActionPreference = $prevEap
if ($inspectCode -ne 0) { Fail "MySQL 容器不存在：$ContainerName" 1 }

$ErrorActionPreference = 'Continue'
$running = (docker inspect -f '{{.State.Running}}' $ContainerName 2>$null | Select-Object -First 1)
$ErrorActionPreference = $prevEap
if ("$running".Trim() -ne 'true' -and -not $List) { Fail "MySQL 容器未运行：$ContainerName（docker compose start mysql）" 1 }
$containerOk = ("$running".Trim() -eq 'true')

$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$ContainerCnf = "/tmp/.trackmeet_patch_$Stamp.cnf"
$MysqlBase = "mysql --defaults-extra-file='$ContainerCnf' --default-character-set=utf8mb4"

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

function Invoke-MysqlScalar {
    param([string]$Sql)
    $ErrorActionPreference = 'Continue'
    $out = (docker exec $ContainerName sh -c "$MysqlBase -N -B -e \`"$Sql\`"" 2>$null | Select-Object -First 1)
    $ErrorActionPreference = $prevEap
    if ($null -eq $out) { return '' }
    return "$out".Trim()
}

# ---------------------------------------------------------------------------
# -List / -DryRun
# ---------------------------------------------------------------------------
if ($List) {
    Write-Log "补丁目录：$SqlDirFull"
    Write-Log "容器：$ContainerName（就绪：$containerOk）  库：$DbName"
    if ($containerOk) { Set-ContainerDefaultsFile }
    try {
        foreach ($f in $selected) {
            $num = $f.Name.Substring(0, 2)
            $marker = Get-MarkerOf -Number $num
            $state = 'unknown（容器未就绪，无法探测）'
            if ($containerOk -and $marker -ne '') {
                $hit = Invoke-MysqlScalar -Sql "SELECT COUNT(*) FROM sys_permission WHERE perm_code='$marker';"
                if ($hit -ne '' -and $hit -ne '0') { $state = '已生效(无需执行)' } else { $state = '未生效(待执行)' }
            }
            Write-Host ("  {0,-34} {1,-22} 探测标记：{2}" -f $f.Name, $state, $(if ($marker -eq '') { '无' } else { $marker }))
        }
    } finally { Remove-ContainerDefaultsFile }
    Write-Log '-List 模式：未执行任何 SQL。'
    exit 0
}

if ($DryRun) {
    Write-Log '-DryRun 模式：以下为待执行补丁首 25 行预览，未写库。'
    foreach ($f in $selected) {
        Write-Log "----- $($f.Name) -----"
        Get-Content -LiteralPath $f.FullName -Encoding UTF8 -TotalCount 25 | ForEach-Object { Write-Host $_ }
    }
    exit 0
}

# ---------------------------------------------------------------------------
# 确认与执行
# ---------------------------------------------------------------------------
Write-Log "补丁目录：$SqlDirFull"
Write-Log "将执行 $($selected.Count) 个补丁（目标容器 $ContainerName，库 $DbName）："
foreach ($f in $selected) { Write-Log "  - $($f.Name)" }

if (-not $Yes) {
    Write-Host '补丁幂等、可重复执行，但建议先备份。确认继续？输入 yes 继续：' -NoNewline
    $answer = Read-Host
    if ($answer -notmatch '^(y|Y|yes|YES|Yes)$') { Fail '用户取消。' 3 }
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$patchFailed = ''

try {
    try {
        Set-ContainerDefaultsFile
        $before = Invoke-MysqlScalar -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName';"
        if ($before -eq '') { $before = '0' }
        Write-Log "执行前表数量：$before"

        $index = 0
        foreach ($f in $selected) {
            $index++
            Write-Log "== [$index/$($selected.Count)] 执行补丁：$($f.Name) =="
            $savedConsole = [Console]::OutputEncoding
            $savedOutputEncoding = $OutputEncoding
            [Console]::OutputEncoding = $utf8NoBom
            $OutputEncoding = $utf8NoBom
            $ErrorActionPreference = 'Continue'
            try {
                Get-Content -LiteralPath $f.FullName -Encoding UTF8 | docker exec -i $ContainerName sh -c "$MysqlBase '$DbName'"
                $status = $LASTEXITCODE
            } finally {
                [Console]::OutputEncoding = $savedConsole
                $OutputEncoding = $savedOutputEncoding
                $ErrorActionPreference = $prevEap
            }
            if ($status -ne 0) {
                $patchFailed = "补丁 $($f.Name) 执行失败（退出码 $status），已停止后续补丁；请修复后重跑（补丁幂等）。"
                break
            }
            Write-Log "补丁 $($f.Name) 执行成功。"
        }

        if ($patchFailed -ne '') { throw $patchFailed }

        $after = Invoke-MysqlScalar -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName';"
        if ($after -eq '') { $after = '0' }
        $delta = [int]$after - [int]$before
        Write-Log "执行后表数量：$after（变化 $delta）"
        Write-Log "全部补丁执行完成（共 $($selected.Count) 个）。建议继续："
        Write-Log '  1) docker compose restart backend'
        Write-Log '  2) powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1'
    } catch {
        $msg = "$($_.Exception.Message)"
        Write-Warn $msg
        Fail "[补丁执行失败] $msg" 2
    }
}
finally {
    Remove-ContainerDefaultsFile
}

exit 0
