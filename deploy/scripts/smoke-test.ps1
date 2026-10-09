#!/usr/bin/env pwsh
<#
===============================================================================
 TrackMeet 部署冒烟验收脚本（Windows PowerShell 5.1+ / PowerShell 7+）
-------------------------------------------------------------------------------
 用途：对「已启动」的 Compose 环境按序调用关键接口，输出「用例编号 + 结果」表格，
       任一用例失败即返回非 0 退出码，可直接接入发版检查单。

 Usage:
   powershell -ExecutionPolicy Bypass -File deploy\scripts\smoke-test.ps1 [-Base <URL>] [-Web <URL>] [-MeetId <ID>] [-User <账号>] [-Password <口令>] [-Timeout <秒>] [-KeepGoing]
   pwsh      -File deploy/scripts/smoke-test.ps1 -Base http://127.0.0.1:8080/api -Web http://127.0.0.1

 参数:
   -Base <URL>       后端 API 根地址（默认 http://127.0.0.1:<BACKEND_PORT>/api）
   -Web <URL>        前端（Nginx）根地址（默认 http://127.0.0.1:<WEB_PORT>），用于整链路验收
   -MeetId <ID>      指定届次 ID（默认调用 /meets 自动取第一条，取不到则用 1）
   -User <账号>      登录账号（默认 admin）
   -Password <口令>  登录口令（默认 .env 的 DEFAULT_ACCOUNT_PASSWORD，否则 Admin@123）
   -Timeout <秒>     单请求超时（默认 20）
   -KeepGoing        全部用例跑完（默认失败即停止，便于快速定位）
   -Help             显示本帮助

 示例:
   .\smoke-test.ps1
   .\smoke-test.ps1 -MeetId 1 -KeepGoing
   .\smoke-test.ps1 -Base http://127.0.0.1:8080/api -Web http://127.0.0.1

 依赖: PowerShell 5.1+（Invoke-RestMethod / ConvertFrom-Json），无 jq / python 依赖。
 用例: TC-SMOKE-01 … TC-SMOKE-16（编号与断言见 deploy/README.md 第 7 节）。
 退出码: 0 全部通过 / 1 存在失败用例 / 2 环境不可用（后端未启动等）。
===============================================================================
#>
[CmdletBinding()]
param(
    [string]$Base = '',
    [string]$Web = '',
    [string]$MeetId = '',
    [string]$User = 'admin',
    [string]$Password = '',
    [int]$Timeout = 20,
    [switch]$KeepGoing,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Off
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ScriptPath = $MyInvocation.MyCommand.Path
$ScriptDir = Split-Path -Parent $ScriptPath
$DeployDir = Split-Path -Parent $ScriptDir
$prevEap = $ErrorActionPreference

function Write-Log  { param([string]$Message) Write-Host ("[{0}] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) }
function Write-Warn { param([string]$Message) Write-Host ("[{0}] [警告] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) -ForegroundColor Yellow }
function Fail       { param([string]$Message, [int]$Code = 2) Write-Host ("[{0}] [错误] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message) -ForegroundColor Red; exit $Code }
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

# ---------------------------------------------------------------------------
# 基础配置
# ---------------------------------------------------------------------------
$EnvFile = Join-Path $DeployDir '.env'
$EnvMap = @{}
if (Test-Path -LiteralPath $EnvFile) { $EnvMap = Read-EnvFile -Path $EnvFile }

if ($Password -eq '') { $Password = Get-Setting -EnvMap $EnvMap -Key 'DEFAULT_ACCOUNT_PASSWORD' -Default 'Admin@123' }
$ApiBase = if ($Base -ne '') { $Base } else { 'http://127.0.0.1:{0}/api' -f (Get-Setting -EnvMap $EnvMap -Key 'BACKEND_PORT' -Default '8080') }
$WebBase = if ($Web -ne '')  { $Web }  else { 'http://127.0.0.1:{0}' -f (Get-Setting -EnvMap $EnvMap -Key 'WEB_PORT' -Default '80') }
$ApiBase = $ApiBase.TrimEnd('/')
$WebBase = $WebBase.TrimEnd('/')

$Headers = @{}
$Token = ''
$ResolvedMeetId = ''
$Pass = 0
$Fail = 0
$Total = 0
$FirstFail = ''
$Results = New-Object System.Collections.ArrayList

# ---------------------------------------------------------------------------
# HTTP 调用
#   说明：这里用 System.Net.HttpWebRequest 而不是 Invoke-RestMethod，原因：
#     1) PS 5.1 的 Invoke-RestMethod 没有可靠的 -TimeoutSec，失败时容易长时间挂住；
#     2) 需要精确控制超时，并在 HTTP 401/403/500 时也能读到响应体里的业务 code。
#   JSON 解析使用 ConvertFrom-Json，因此脚本不依赖 jq / python 等外部工具。
# ---------------------------------------------------------------------------
function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Body = '',
        [string]$OutFile = ''
    )
    $req = [System.Net.HttpWebRequest]::Create($Url)
    $req.Method = $Method
    $req.Timeout = $Timeout * 1000
    $req.ReadWriteTimeout = $Timeout * 1000
    $req.AllowAutoRedirect = $false
    foreach ($k in $Headers.Keys) { $req.Headers[$k] = $Headers[$k] }
    if ($Body -ne '') {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
        $req.ContentType = 'application/json; charset=utf-8'
        $req.ContentLength = $bytes.Length
        $s = $req.GetRequestStream()
        try { $s.Write($bytes, 0, $bytes.Length) } finally { $s.Dispose() }
    }
    try {
        $resp = $req.GetResponse()
    } catch [System.Net.WebException] {
        if ($_.Exception.Response -ne $null) { $resp = $_.Exception.Response } else { throw }
    }
    $result = [PSCustomObject]@{ Status = 0; Body = '' }
    try {
        $result.Status = [int]$resp.StatusCode
        $stream = $resp.GetResponseStream()
        try {
            if ($OutFile -ne '') {
                $fs = [System.IO.File]::Create($OutFile)
                try { $stream.CopyTo($fs) } finally { $fs.Dispose() }
                $result.Body = ''
            } else {
                $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                try { $result.Body = $reader.ReadToEnd() } finally { $reader.Dispose() }
            }
        } finally { $stream.Dispose() }
    } finally { $resp.Close() }
    return $result
}

function Get-JsonField {
    param([string]$Body, [string]$Mode)
    if ([string]::IsNullOrWhiteSpace($Body)) { return '' }
    try { $obj = $Body | ConvertFrom-Json } catch { return '' }
    if ($null -eq $obj) { return '' }
    switch ($Mode) {
        'code' {
            if ($obj.PSObject.Properties.Name -contains 'code') { return "$($obj.code)" }
            return ''
        }
        'token' {
            if ($obj.PSObject.Properties.Name -contains 'data' -and $obj.data -ne $null -and
                $obj.data.PSObject.Properties.Name -contains 'accessToken') { return "$($obj.data.accessToken)" }
            if ($obj.PSObject.Properties.Name -contains 'accessToken') { return "$($obj.accessToken)" }
            return ''
        }
        'firstId' {
            $recs = $null
            if ($obj.PSObject.Properties.Name -contains 'data' -and $obj.data -ne $null -and
                $obj.data.PSObject.Properties.Name -contains 'records') { $recs = $obj.data.records }
            elseif ($obj.PSObject.Properties.Name -contains 'records') { $recs = $obj.records }
            if ($recs -ne $null -and @($recs).Count -gt 0) {
                $first = @($recs)[0]
                if ($first.PSObject.Properties.Name -contains 'id') { return "$($first.id)" }
            }
            return ''
        }
        default { return '' }
    }
}

function Add-Result {
    param([string]$Id, [string]$Name, [bool]$Ok, [string]$Detail)
    $Total++
    if ($Ok) { $Pass++ } else { $Fail++; if ($FirstFail -eq '') { $FirstFail = $Id } }
    $null = $Results.Add([PSCustomObject]@{ Id = $Id; Name = $Name; Ok = $Ok; Detail = $Detail })
    $tag = if ($Ok) { '通过' } else { '失败' }
    $color = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host ("{0,-15} {1,-6} {2} | {3}" -f $Id, $tag, $Name, $Detail) -ForegroundColor $color
}

function Stop-OrContinue {
    param([string]$Id)
    if ($Fail -gt 0 -and -not $KeepGoing) {
        Write-Host ''
        Write-Log "用例 $Id 失败，按默认策略停止（加 -KeepGoing 可跑完全部用例）"
        throw 'SMOKE-ABORT'
    }
}

function Show-Report {
    Write-Host ''
    Write-Host '-------------------------------------------------------------------------------'
    if ($Fail -eq 0) {
        Write-Log "冒烟验收结果：通过 $Pass/$Total，全部通过"
        exit 0
    }
    Write-Log "冒烟验收结果：通过 $Pass/$Total，失败 $Fail 条（首个失败：$FirstFail）"
    Write-Host '排查建议：'
    Write-Host "  1) 后端是否健康： Invoke-RestMethod $ApiBase/../actuator/health （或 docker compose ps）"
    Write-Host '  2) 后端日志：     docker compose logs -f --tail=200 backend'
    Write-Host '  3) 登录 401：     确认后端已启动且 DefaultAccountInitializer 已初始化口令（首启 5~20 秒）'
    Write-Host '                    口令见 .env 的 DEFAULT_ACCOUNT_PASSWORD（默认 Admin@123）'
    Write-Host '  4) 403 无权限：   admin 账号权限由 sql/05 初始化；确认已执行 sql/07、08、09 补丁'
    Write-Host "  5) 届次相关报错： 用 -MeetId 指定有效届次（当前使用 $ResolvedMeetId）"
    exit 1
}

# ---------------------------------------------------------------------------
# 探活与登录
# ---------------------------------------------------------------------------
Write-Host '==============================================================================='
Write-Log 'TrackMeet 部署冒烟验收'
Write-Log "后端 API 根地址：$ApiBase"
Write-Log "前端根地址：    $WebBase"
Write-Log "登录账号：      $User"
Write-Host '==============================================================================='
Write-Host ("{0,-15} {1,-6} {2}" -f '用例编号', '结果', '说明 / 断言')
Write-Host '-------------------------------------------------------------------------------'

# 用例 01：后端健康检查
try {
    $health = Invoke-Api -Method 'GET' -Url "$ApiBase/../actuator/health"
    if ($health.Status -eq 200 -and $health.Body -match '"UP"') {
        Add-Result -Id 'TC-SMOKE-01' -Name "后端健康检查 $ApiBase/../actuator/health" -Ok $true -Detail 'HTTP 200 且 status=UP'
    } else {
        Add-Result -Id 'TC-SMOKE-01' -Name "后端健康检查 $ApiBase/../actuator/health" -Ok $false -Detail ("HTTP={0} body={1}" -f $health.Status, $health.Body.Substring(0, [Math]::Min(100, $health.Body.Length)))
        Show-Report
    }
} catch {
    Add-Result -Id 'TC-SMOKE-01' -Name "后端健康检查 $ApiBase/../actuator/health" -Ok $false -Detail "请求异常：$($_.Exception.Message)"
    Show-Report
}

# 用例 02：前端健康检查
try {
    $webHealth = Invoke-Api -Method 'GET' -Url "$WebBase/health"
    if ($webHealth.Status -eq 200 -and $webHealth.Body -match 'ok') {
        Add-Result -Id 'TC-SMOKE-02' -Name "前端健康检查 $WebBase/health" -Ok $true -Detail 'HTTP 200 且返回 ok'
    } else {
        Add-Result -Id 'TC-SMOKE-02' -Name "前端健康检查 $WebBase/health" -Ok $false -Detail ("HTTP={0} body={1}（检查 WEB_PORT 与容器健康状态）" -f $webHealth.Status, $webHealth.Body)
    }
} catch {
    Add-Result -Id 'TC-SMOKE-02' -Name "前端健康检查 $WebBase/health" -Ok $false -Detail "请求异常：$($_.Exception.Message)"
}

# 用例 03：登录
$loginBody = (@{ username = $User; password = $Password } | ConvertTo-Json -Compress)
$login = Invoke-Api -Method 'POST' -Url "$ApiBase/auth/login" -Body $loginBody
$loginCode = Get-JsonField -Body $login.Body -Mode 'code'
$Token = Get-JsonField -Body $login.Body -Mode 'token'
if ($login.Status -eq 200 -and $loginCode -eq '0' -and $Token -ne '') {
    Add-Result -Id 'TC-SMOKE-03' -Name "登录 $User 获取 accessToken" -Ok $true -Detail 'HTTP 200 + code=0 且令牌非空'
    $Headers['Authorization'] = "Bearer $Token"
    $Headers['X-Meet-Id'] = '0'   # 占位，解析出届次后立即覆盖
} else {
    $preview = if ($login.Body.Length -gt 140) { $login.Body.Substring(0, 140) } else { $login.Body }
    Add-Result -Id 'TC-SMOKE-03' -Name "登录 $User 获取 accessToken" -Ok $false -Detail ("HTTP={0} code={1} body={2}" -f $login.Status, $loginCode, $preview)
    Write-Host '排查提示：'
    Write-Host '  1) 确认后端已启动并通过健康检查： docker compose ps / docker compose logs -f backend'
    Write-Host '  2) 确认 DefaultAccountInitializer 已初始化口令（首启后 5~20 秒，日志含「口令已初始化」）'
    Write-Host '  3) 确认账号口令： .env 的 DEFAULT_ACCOUNT_PASSWORD（默认 Admin@123）；首登改密后请用 -Password 指定'
    Show-Report
}

# 用例 04：届次列表（并解析 X-Meet-Id）
$meets = Invoke-Api -Method 'GET' -Url "$ApiBase/meets?page=1&size=10"
$meetsCode = Get-JsonField -Body $meets.Body -Mode 'code'
if ($meets.Status -eq 200 -and $meetsCode -eq '0') {
    Add-Result -Id 'TC-SMOKE-04' -Name '届次列表 GET /api/meets' -Ok $true -Detail 'HTTP 200 + code=0'
} else {
    Add-Result -Id 'TC-SMOKE-04' -Name '届次列表 GET /api/meets' -Ok $false -Detail ("HTTP={0} code={1}" -f $meets.Status, $meetsCode)
}

if ($MeetId -ne '') { $ResolvedMeetId = $MeetId } else {
    $ResolvedMeetId = Get-JsonField -Body $meets.Body -Mode 'firstId'
    if ($ResolvedMeetId -eq '') { $ResolvedMeetId = '1' }
}
$Headers['X-Meet-Id'] = "$ResolvedMeetId"
Write-Log "后续用例使用 X-Meet-Id：$ResolvedMeetId（可用 -MeetId 覆盖）"
Write-Host '-------------------------------------------------------------------------------'

# ---------------------------------------------------------------------------
# 用例 05~15
# ---------------------------------------------------------------------------
$steps = @(
    [PSCustomObject]@{ Id = 'TC-SMOKE-05'; Name = '单位列表 GET /api/units';                   Method = 'GET';  Path = '/units?page=1&size=10';                          Body = ''; Accept = @('0'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-06'; Name = '运动员分页 GET /api/athletes';              Method = 'GET';  Path = '/athletes?page=1&size=10';                       Body = ''; Accept = @('0'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-07'; Name = '项目分页 GET /api/events';                  Method = 'GET';  Path = '/events?page=1&size=10';                         Body = ''; Accept = @('0'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-08'; Name = '报名统计 GET /api/registrations/statistics'; Method = 'GET'; Path = '/registrations/statistics?dimension=UNIT';        Body = ''; Accept = @('0'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-09'; Name = '竞赛单元分页 GET /api/sessions';            Method = 'GET';  Path = '/sessions?page=1&size=10';                       Body = ''; Accept = @('0'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-10'; Name = '日程分页 GET /api/schedule';                Method = 'GET';  Path = '/schedule?page=1&size=10';                       Body = ''; Accept = @('0'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-11'; Name = '成绩分页 GET /api/results';                 Method = 'GET';  Path = '/results?page=1&size=10';                        Body = ''; Accept = @('0'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-12'; Name = 'PDF 能力探测 GET /api/print/pdf/capability'; Method = 'GET'; Path = '/print/pdf/capability';                        Body = ''; Accept = @('0', '50003'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-13'; Name = '报表清单 GET /api/print/reports';           Method = 'GET';  Path = '/print/reports';                                Body = ''; Accept = @('0'); Meet = $false },
    [PSCustomObject]@{ Id = 'TC-SMOKE-14'; Name = '大屏配置 GET /api/screen/config';           Method = 'GET';  Path = '/screen/config?meetId={MEET}';                   Body = ''; Accept = @('0'); Meet = $true },
    [PSCustomObject]@{ Id = 'TC-SMOKE-15'; Name = '自动编排预览 POST /api/schedule/auto (apply=false)'; Method = 'POST'; Path = '/schedule/auto'; Body = ''; Accept = @('0'); Meet = $true }
)

try {
    foreach ($step in $steps) {
        $url = $ApiBase + ($step.Path -replace '\{MEET\}', "$ResolvedMeetId")
        $body = $step.Body
        if ($step.Method -eq 'POST' -and $body -eq '') {
            $body = (@{ meetId = [int]$ResolvedMeetId; apply = $false } | ConvertTo-Json -Compress)
        }
        $savedMeet = $Headers['X-Meet-Id']
        if (-not $step.Meet) { $Headers.Remove('X-Meet-Id') } else { $Headers['X-Meet-Id'] = "$ResolvedMeetId" }

        try {
            $r = Invoke-Api -Method $step.Method -Url $url -Body $body
            $code = Get-JsonField -Body $r.Body -Mode 'code'
            $ok = $false
            $detail = ''
            if ($r.Status -ne 200) {
                $detail = "HTTP $($r.Status)（期望 200）"
            } elseif ($step.Accept -contains $code) {
                $ok = $true
                $detail = "HTTP 200 + code=$code"
            } else {
                $preview = if ($r.Body.Length -gt 140) { $r.Body.Substring(0, 140) } else { $r.Body }
                $detail = "HTTP 200 但 code=$code（期望 $($step.Accept -join ','))：$preview"
            }
        } catch {
            $detail = "请求异常：$($_.Exception.Message)"
        } finally {
            if ($savedMeet -ne $null) { $Headers['X-Meet-Id'] = $savedMeet }
        }

        Add-Result -Id $step.Id -Name $step.Name -Ok $ok -Detail $detail
        Stop-OrContinue -Id $step.Id
    }

    # -----------------------------------------------------------------------
    # 用例 16：前端 Nginx 反代整链路
    # -----------------------------------------------------------------------
    if ($WebBase -ne $ApiBase) {
        $savedMeet = $Headers['X-Meet-Id']
        $Headers.Remove('X-Meet-Id')
        try {
            $w = Invoke-Api -Method 'POST' -Url "$WebBase/api/auth/login" -Body $loginBody
            $wcode = Get-JsonField -Body $w.Body -Mode 'code'
            $wtoken = Get-JsonField -Body $w.Body -Mode 'token'
            if ($w.Status -eq 200 -and $wcode -eq '0' -and $wtoken -ne '') {
                Add-Result -Id 'TC-SMOKE-16' -Name "前端反代整链路 POST $WebBase/api/auth/login" -Ok $true -Detail 'HTTP 200 + code=0（Nginx /api 反代正常）'
            } else {
                Add-Result -Id 'TC-SMOKE-16' -Name "前端反代整链路 POST $WebBase/api/auth/login" -Ok $false -Detail ("HTTP={0} code={1}（检查 nginx/default.conf 的 /api/ 反代与 backend 健康）" -f $w.Status, $wcode)
            }
        } catch {
            Add-Result -Id 'TC-SMOKE-16' -Name "前端反代整链路 POST $WebBase/api/auth/login" -Ok $false -Detail "请求异常：$($_.Exception.Message)"
        } finally {
            if ($savedMeet -ne $null) { $Headers['X-Meet-Id'] = $savedMeet }
        }
    } else {
        Write-Log '跳过 TC-SMOKE-16（未配置前端地址，-Web 可指定）'
    }
} catch {
    if ("$($_.Exception.Message)" -ne 'SMOKE-ABORT') { throw }
}

Show-Report
