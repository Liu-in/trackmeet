<#
    一键上传 TrackMeet 到 GitHub（无需编程基础，按提示回车即可）

    作用：把本项目上传到你的 GitHub 仓库，从而自动触发云端构建 Windows 安装包（exe）。

    使用：双击同目录下的「1-上传代码到GitHub.cmd」。
#>
[CmdletBinding()]
param()

# 注意：原生命令（git）会把提示信息写到 stderr（例如换行符警告）。
# 若把 ErrorActionPreference 设为 Stop，这些提示会被当成致命错误而使脚本中止，
# 因此这里统一使用 Continue，并通过退出码判断成败。
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Github = 'https://github.com'
$script:GitExe = 'git'

function Line { Write-Host ('-' * 66) -ForegroundColor DarkGray }
function Title([string]$t) { Write-Host ''; Write-Host $t -ForegroundColor Cyan; Line }
function Ok([string]$t) { Write-Host "  [完成] $t" -ForegroundColor Green }
function Info([string]$t) { Write-Host "  $t" -ForegroundColor Gray }
function Warn([string]$t) { Write-Host "  [注意] $t" -ForegroundColor Yellow }
function Stop2([string]$t) {
    Write-Host ''
    Write-Host "  [中止] $t" -ForegroundColor Red
    Write-Host ''
    Read-Host '按回车键关闭窗口'
    exit 1
}

# 执行 git：忽略 stderr 提示，仅依据退出码判断成败
function Invoke-Git {
    param([string[]]$GitArgs)
    $output = & $script:GitExe @GitArgs 2>&1
    $code = $LASTEXITCODE
    if ($output) {
        foreach ($item in $output) {
            $text = [string]$item
            if ($text -match '(?i)warning:') { continue }
            Write-Host "    $text" -ForegroundColor DarkGray
        }
    }
    return $code
}

Clear-Host
Write-Host ''
Write-Host ' ============================================================' -ForegroundColor White
Write-Host '   TrackMeet 一键上传到 GitHub（用于云端自动生成 exe）' -ForegroundColor White
Write-Host ' ============================================================' -ForegroundColor White
Write-Host ''
Info "项目目录：$Root"
Info '整个过程只需你：① 注册/登录 GitHub ② 建一个空仓库 ③ 复制一个"令牌" ④ 按提示粘贴'
Info '预计 5 分钟，全程鼠标操作。'

# ---------------------------------------------------------------------------
Title '第 0 步：检查环境'
$gitPath = $null
$cmd = Get-Command 'git.exe' -ErrorAction SilentlyContinue
if ($cmd) { $gitPath = $cmd.Source }
elseif (Test-Path 'C:\Program Files\Git\cmd\git.exe') { $gitPath = 'C:\Program Files\Git\cmd\git.exe' }
if (-not $gitPath) {
    Stop2 '这台电脑没装 Git。请先打开 https://git-scm.com/download/win 下载安装（一路点"下一步"即可），安装完重新双击本脚本。'
}
$script:GitExe = $gitPath
Ok "已找到 Git：$gitPath"
$version = (& $script:GitExe --version 2>&1 | Out-String).Trim()
Info "版本：$version"

# ---------------------------------------------------------------------------
Title '第 1 步：输入你的 GitHub 信息'
Info '如果还没有账号：先在浏览器打开 https://github.com/signup 注册（免费，1 分钟）。'
Write-Host ''
$user = Read-Host '  请输入你的 GitHub 用户名（英文/数字，例如 zhangsan）'
if ([string]::IsNullOrWhiteSpace($user)) { Stop2 '用户名不能为空。' }
$user = $user.Trim()

$repo = Read-Host '  请输入仓库名（直接回车用默认值 trackmeet）'
if ([string]::IsNullOrWhiteSpace($repo)) { $repo = 'trackmeet' }
$repo = $repo.Trim()

Write-Host ''
Info '接下来需要"访问令牌"（相当于一次性的专用密码，GitHub 已不允许用登录密码上传代码）。'
Info '获取方法（照着点）：'
Info '  1) 浏览器打开 https://github.com/settings/tokens?type=beta'
Info '  2) 点右上角「Generate new token」'
Info '  3) Token name 随便填（例如 trackmeet-upload）'
Info '  4) Expiration 选 30 days 或 90 days'
Info '  5) Repository access 选「All repositories」（或 Only select repositories 里选中你的仓库）'
Info '  6) Permissions 展开「Repository permissions」，把「Contents」设为 Read and write'
Info '  7) 点最下方「Generate token」，页面上会出现一串 ghp_ 或 github_pat_ 开头的字符'
Info '  8) 点旁边的复制按钮，回到本窗口，右键粘贴到下面'
Write-Host ''
$token = Read-Host '  请粘贴访问令牌（粘贴时屏幕可能不显示，属正常）'
if ([string]::IsNullOrWhiteSpace($token)) { Stop2 '令牌不能为空。' }
$token = $token.Trim()
if ($token.Length -lt 20) { Warn '令牌看起来偏短，若有问题请重新生成一个再试。' }

$remoteUrl = "$Github/$user/$repo.git"
$pushUrl = "https://$user`:$token@github.com/$user/$repo.git"
Info "将上传到：$remoteUrl"

# ---------------------------------------------------------------------------
Title '第 2 步：检查网络与仓库'
$netOk = $true
try {
    $null = Invoke-WebRequest -Uri $Github -Method Head -TimeoutSec 15 -UseBasicParsing
    Ok '可以访问 github.com'
} catch {
    $netOk = $false
    Warn "无法访问 github.com（$($_.Exception.Message)）"
    Info '请检查网络或代理；若本机不能上外网，可把整个项目文件夹拷到能上外网的电脑再运行本脚本。'
    if ((Read-Host '  仍要继续尝试上传吗？(y/N)') -notmatch '^[Yy]') { Stop2 '已取消。' }
}
if ($netOk) {
    $repoExists = $true
    try {
        $null = Invoke-WebRequest -Uri "$Github/$user/$repo" -Method Head -TimeoutSec 15 -UseBasicParsing
    } catch {
        $repoExists = $false
    }
    if (-not $repoExists) {
        Warn "没有找到仓库 $user/$repo（若是私有仓库也会显示为找不到，属正常）。"
        Info ('如确实还没建：浏览器打开 https://github.com/new ，Repository name 填「' + $repo + '」，')
        Info '其余都不要勾选（不要勾 Add a README），点最下方绿色「Create repository」，然后回来按回车继续。'
        Read-Host '  建好后按回车继续' | Out-Null
    } else {
        Ok "仓库已存在：$remoteUrl"
    }
}

# ---------------------------------------------------------------------------
Title '第 3 步：准备并上传（约 1~3 分钟，请勿关闭窗口）'
Push-Location $Root
try {
    # 关闭自动换行转换，避免 CRLF/LF 提示，并保证批处理文件在仓库中保持 CRLF
    Invoke-Git @('config','core.autocrlf','false') | Out-Null
    Invoke-Git @('config','core.safecrlf','false') | Out-Null

    if (-not (Test-Path (Join-Path $Root '.git'))) {
        $code = Invoke-Git @('init','-b','main')
        if ($code -ne 0) {
            Invoke-Git @('init') | Out-Null
            Invoke-Git @('checkout','-b','main') | Out-Null
            $code = 0
        }
        Ok '已初始化本地版本库'
    } else {
        Ok '本地版本库已存在，跳过初始化'
    }
    Invoke-Git @('config','user.name','TrackMeet') | Out-Null
    Invoke-Git @('config','user.email','trackmeet@local') | Out-Null

    Info '正在登记文件（已按 .gitignore 排除依赖与构建产物，不会上传 180MB 的 node_modules）...'
    $code = Invoke-Git @('add','-A')
    if ($code -ne 0) { Warn '登记文件时出现提示（个别文件可能被占用），继续尝试提交...' }
    $staged = 0
    if (Test-Path (Join-Path $Root '.git')) {
        $staged = (& $script:GitExe diff --cached --name-only 2>$null | Measure-Object -Line).Lines
    }
    Ok "待上传文件：$staged 个"
    if ($staged -eq 0) { Warn '没有检测到变更（可能之前已上传过），将直接尝试推送。' }

    $code = Invoke-Git @('commit','-m','TrackMeet P6 源码与安装包构建套件')
    if ($code -ne 0) { Warn '提交步骤提示无新变更，继续推送...' } else { Ok '已生成提交' }

    Invoke-Git @('remote','remove','origin') | Out-Null
    Invoke-Git @('remote','add','origin',$pushUrl) | Out-Null
    Info '正在上传（第一次较慢，请耐心等待）...'
    $code = Invoke-Git @('push','-u','origin','main','--force')
    if ($code -ne 0) {
        Write-Host ''
        Warn '上传失败。常见原因与处理：'
        Info '  1) 令牌权限不足 → 重新生成令牌，Contents 必须为 Read and write'
        Info '  2) 仓库名/用户名拼错 → 重新运行本脚本，仔细核对'
        Info '  3) 令牌已过期 → 重新生成后再试'
        Info '  4) 网络中断 → 稍后重试；也可在仓库页面用 Add file → Upload files 手工上传'
        Stop2 '未能完成上传。'
    }
    Ok '上传成功！'

    Invoke-Git @('remote','set-url','origin',$remoteUrl) | Out-Null
    Ok '已清除本地保存的令牌（更安全）'
} finally {
    if ((Get-Location).Path -eq $Root) { Pop-Location }
}

# ---------------------------------------------------------------------------
Title '第 4 步：等待云端自动生成 exe'
$actionsUrl = "$Github/$user/$repo/actions"
Info "构建页面：$actionsUrl"
Info '页面里会出现一条正在运行的记录（黄色圆点 → 转圈），大约 8~15 分钟后变成绿色对勾。'
Info '变成绿色后：点进这条记录 → 页面拉到最底部「Artifacts」→ 点「trackmeet-windows-P6」下载。'
Info '下载得到的是压缩包，解压后里面的 TrackMeet-Setup-P6.exe 就是安装包，双击即可安装。'
Write-Host ''
Warn '若 5 分钟内没有任何记录：确认仓库里能看到 .github/workflows/build-installer.yml 文件。'
if ((Read-Host '  现在打开构建页面吗？(Y/n)') -notmatch '^[Nn]') {
    Start-Process $actionsUrl
}
Write-Host ''
Ok '本脚本工作已完成，可以关闭窗口。'
Read-Host '按回车键关闭'
