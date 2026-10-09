<#
.SYNOPSIS
    TrackMeet Windows 安装包（EXE）一键构建脚本。

.DESCRIPTION
    按顺序完成：校验前置工具 → 构建后端可执行 jar → 构建前端静态资源 → 用 jlink 生成精简 JRE
    → 组装 installer\stage\ → 调用 Inno Setup 编译出 dist\TrackMeet-Setup-<版本>.exe。
    也支持 -Portable 生成免安装绿色压缩包，或 -StageOnly 只组装不编译。

    前置工具：
      JDK 17~21（需含 javac 与 jlink；JDK 27 未经验证，建议 17/21）
      Maven（backend\mvnw.cmd 或 PATH 中的 mvn）
      Node.js 18+ 与 pnpm（仅构建前端需要）
      Inno Setup 6（仅编译 EXE 需要，https://jrsoftware.org/isdl.php）

.EXAMPLE
    pwsh installer/build-installer.ps1
    pwsh installer/build-installer.ps1 -Version P6.1 -SkipFrontend
    pwsh installer/build-installer.ps1 -Portable
    pwsh installer/build-installer.ps1 -StageOnly
#>
[CmdletBinding()]
param(
    [string]$Version = 'P6',
    [string]$JdkHome = '',
    [string]$MavenCmd = '',
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$StageOnly,
    [switch]$Portable,
    [switch]$SkipJlink
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$InstallerDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Split-Path -Parent $InstallerDir
$Stage = Join-Path $InstallerDir 'stage'
$Dist = Join-Path $Root 'dist'
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'
$SqlDir = Join-Path $Root 'sql'

function Write-Step([string]$Text) { Write-Host "`n=== $Text ===" -ForegroundColor Cyan }
function Write-Ok([string]$Text)   { Write-Host "  [OK] $Text" -ForegroundColor Green }
function Write-Warn2([string]$Text){ Write-Host "  [警告] $Text" -ForegroundColor Yellow }
function Fail([string]$Text)       { Write-Host "  [失败] $Text" -ForegroundColor Red; exit 1 }

function Resolve-Jdk {
    param([string]$Explicit)
    $candidates = @()
    if ($Explicit) { $candidates += $Explicit }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java",
                        "$env:ProgramFiles\Microsoft", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")) {
        if (Test-Path $base) {
            $candidates += (Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'jdk|jre|jdk-' } | Sort-Object Name -Descending |
                ForEach-Object { $_.FullName })
        }
    }
    foreach ($candidate in $candidates) {
        $javac = Join-Path $candidate 'bin\javac.exe'
        $jlink = Join-Path $candidate 'bin\jlink.exe'
        if ((Test-Path $javac) -and (Test-Path $jlink)) { return $candidate }
    }
    return $null
}

function Resolve-Inno {
    $candidates = @(
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe",
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe"
    )
    foreach ($candidate in $candidates) { if (Test-Path $candidate) { return $candidate } }
    $onPath = Get-Command 'ISCC.exe' -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    return $null
}

Write-Host "TrackMeet 安装包构建 —— 版本 $Version" -ForegroundColor White
Write-Host "仓库根目录：$Root"

# ---------------------------------------------------------------------------
# 1. 前置校验
# ---------------------------------------------------------------------------
Write-Step '1/7 校验构建工具'

$jdkResolved = Resolve-Jdk -Explicit $JdkHome
if (-not $jdkResolved) {
    Fail "未找到含 javac/jlink 的 JDK（可用 -JdkHome 指定，例如 -JdkHome 'C:\Program Files\Eclipse Adoptium\jdk-17.0.11+9'）"
}
Write-Ok "JDK：$jdkResolved"

$maven = $MavenCmd
if (-not $maven) {
    $mvnw = Join-Path $Backend 'mvnw.cmd'
    if (Test-Path $mvnw) { $maven = $mvnw }
    else {
        $onPath = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
        if (-not $onPath) { $onPath = Get-Command 'mvn' -ErrorAction SilentlyContinue }
        if ($onPath) { $maven = $onPath.Source }
    }
}
if (-not $SkipBackend) {
    if (-not $maven) { Fail "未找到 Maven（可放置 backend\mvnw.cmd 或用 -MavenCmd 指定）" }
    Write-Ok "Maven：$maven"
} else {
    Write-Warn2 '已跳过后端构建（复用现有 target\trackmeet-backend.jar）'
}

if (-not $SkipFrontend) {
    foreach ($tool in @('node', 'pnpm')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { Fail "未找到 $tool（构建前端需要 Node.js 18+ 与 pnpm）" }
    }
    Write-Ok ("Node：{0}  pnpm：{1}" -f (node -v), (pnpm -v))
} else {
    Write-Warn2 '已跳过前端构建（复用现有 frontend\dist）'
}

$iscc = $null
if (-not $StageOnly -and -not $Portable) {
    $iscc = Resolve-Inno
    if (-not $iscc) { Fail "未找到 Inno Setup 6 的 ISCC.exe（安装后重试，或用 -Portable 生成免安装包）" }
    Write-Ok "Inno Setup：$iscc"
}

# ---------------------------------------------------------------------------
# 2. 构建后端
# ---------------------------------------------------------------------------
Write-Step '2/7 构建后端（mvn package，跳过测试）'
if (-not $SkipBackend) {
    Push-Location $Backend
    try {
        & $maven -q -DskipTests clean package
        if ($LASTEXITCODE -ne 0) { Fail "后端构建失败（退出码 $LASTEXITCODE）" }
    } finally { Pop-Location }
}
$jar = Join-Path $Backend 'target\trackmeet-backend.jar'
if (-not (Test-Path $jar)) { Fail "未找到后端 jar：$jar（可先执行 backend\mvnw.cmd -DskipTests package）" }
Write-Ok ("后端 jar：{0:N1} MB" -f ((Get-Item $jar).Length / 1MB))

# ---------------------------------------------------------------------------
# 3. 构建前端
# ---------------------------------------------------------------------------
Write-Step '3/7 构建前端（pnpm install --frozen-lockfile && pnpm build）'
if (-not $SkipFrontend) {
    Push-Location $Frontend
    try {
        & pnpm install --frozen-lockfile
        if ($LASTEXITCODE -ne 0) { Fail "pnpm install 失败（退出码 $LASTEXITCODE）" }
        & pnpm run build
        if ($LASTEXITCODE -ne 0) { Fail "前端构建失败（退出码 $LASTEXITCODE）" }
    } finally { Pop-Location }
}
$distDir = Join-Path $Frontend 'dist'
if (-not (Test-Path (Join-Path $distDir 'index.html'))) { Fail "未找到前端产物：$distDir\index.html" }
Write-Ok ("前端产物：{0} 个文件" -f (Get-ChildItem $distDir -Recurse -File).Count)

# ---------------------------------------------------------------------------
# 4. 组装 stage
# ---------------------------------------------------------------------------
Write-Step '4/7 组装安装文件（installer\stage）'
if (Test-Path $Stage) { Remove-Item $Stage -Recurse -Force }
New-Item -ItemType Directory -Path $Stage | Out-Null

New-Item -ItemType Directory -Path (Join-Path $Stage 'backend') | Out-Null
Copy-Item $jar (Join-Path $Stage 'backend\app.jar') -Force
Write-Ok 'backend\app.jar'

New-Item -ItemType Directory -Path (Join-Path $Stage 'frontend') | Out-Null
Copy-Item (Join-Path $distDir '*') (Join-Path $Stage 'frontend') -Recurse -Force
Write-Ok 'frontend\（静态资源）'

New-Item -ItemType Directory -Path (Join-Path $Stage 'sql') | Out-Null
Copy-Item (Join-Path $SqlDir '*.sql') (Join-Path $Stage 'sql') -Force
Write-Ok ("sql\（{0} 个脚本）" -f (Get-ChildItem (Join-Path $Stage 'sql') -File).Count)

New-Item -ItemType Directory -Path (Join-Path $Stage 'scripts') | Out-Null
Copy-Item (Join-Path $InstallerDir 'scripts\*') (Join-Path $Stage 'scripts') -Recurse -Force
Write-Ok 'scripts\（配置/启停/卸载脚本）'

# 应用图标（安装包与快捷方式使用；缺失时 Inno Setup 编译会报错，因此必须存在）
New-Item -ItemType Directory -Path (Join-Path $Stage 'assets') | Out-Null
$icon = Join-Path $InstallerDir 'assets\TrackMeet.ico'
if (Test-Path $icon) {
    Copy-Item $icon (Join-Path $Stage 'assets\TrackMeet.ico') -Force
    Write-Ok 'assets\TrackMeet.ico（应用图标）'
} else {
    Fail "缺少应用图标：$icon（安装脚本的 IconFilename 需要该文件）"
}

New-Item -ItemType Directory -Path (Join-Path $Stage 'docs') | Out-Null
foreach ($doc in @('README.md', 'deploy\README.md', 'docs\20-验收测试清单.md')) {
    $path = Join-Path $Root $doc
    if (Test-Path $path) { Copy-Item $path (Join-Path $Stage ('docs\' + (Split-Path $doc -Leaf))) -Force }
}
Write-Ok 'docs\（使用与验收文档）'

# ---------------------------------------------------------------------------
# 5. 生成精简运行时
# ---------------------------------------------------------------------------
Write-Step '5/7 生成精简 JRE（jlink）'
$runtimeDir = Join-Path $Stage 'runtime'
if ($SkipJlink) {
    if ($env:JAVA_HOME) {
        Copy-Item $env:JAVA_HOME $runtimeDir -Recurse -Force
        Write-Warn2 '已跳过 jlink，直接复制 JAVA_HOME 作为运行时（体积较大）'
    } else { Fail '启用 -SkipJlink 时需要设置 JAVA_HOME 以便复制运行时' }
} else {
    $jlink = Join-Path $jdkResolved 'bin\jlink.exe'
    $modules = 'java.base,java.logging,java.naming,java.sql,java.desktop,java.management,java.instrument,java.xml,java.net.http,java.scripting,jdk.unsupported,jdk.crypto.ec,jdk.zipfs,jdk.localedata'
    & $jlink --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=2 --include-locales=en,zh --output $runtimeDir
    if ($LASTEXITCODE -ne 0) { Fail "jlink 失败（退出码 $LASTEXITCODE）" }
    Write-Ok ("运行时：{0:N1} MB" -f ((Get-ChildItem $runtimeDir -Recurse -File | Measure-Object Length -Sum).Sum / 1MB))
}

# ---------------------------------------------------------------------------
# 6. 生成免安装包 / 编译 EXE
# ---------------------------------------------------------------------------
New-Item -ItemType Directory -Path $Dist -Force | Out-Null
if ($Portable) {
    Write-Step '6/7 生成免安装绿色包（Portable）'
    $portableZip = Join-Path $Dist "TrackMeet-Portable-$Version.zip"
    if (Test-Path $portableZip) { Remove-Item $portableZip -Force }
    Compress-Archive -Path (Join-Path $Stage '*') -DestinationPath $portableZip -CompressionLevel Optimal
    $portableHash = (Get-FileHash $portableZip -Algorithm SHA256).Hash.ToLower()
    "$portableHash  $(Split-Path $portableZip -Leaf)" | Set-Content ($portableZip + '.sha256') -Encoding UTF8
    Write-Ok ("免安装包：{0}（{1:N1} MB）" -f (Split-Path $portableZip -Leaf), ((Get-Item $portableZip).Length / 1MB))
    Write-Host "  SHA256：$portableHash"
} elseif ($StageOnly) {
    Write-Step '6/7 仅组装（未编译 EXE）'
    Write-Warn2 "stage 已就绪：$Stage"
    Write-Host "  手工编译：`"$((Resolve-Inno))`" `"$(Join-Path $InstallerDir 'TrackMeet.iss')`""
} else {
    Write-Step '6/7 编译安装包（Inno Setup）'
    $env:TRACKMEET_VERSION = $Version
    & $iscc (Join-Path $InstallerDir 'TrackMeet.iss')
    if ($LASTEXITCODE -ne 0) { Fail "Inno Setup 编译失败（退出码 $LASTEXITCODE）" }
    $exe = Join-Path $Dist "TrackMeet-Setup-$Version.exe"
    if (-not (Test-Path $exe)) { Fail "未找到编译产物：$exe" }
    $hash = (Get-FileHash $exe -Algorithm SHA256).Hash.ToLower()
    "$hash  $(Split-Path $exe -Leaf)" | Set-Content ($exe + '.sha256') -Encoding UTF8
    Write-Ok ("安装包：{0}（{1:N1} MB）" -f (Split-Path $exe -Leaf), ((Get-Item $exe).Length / 1MB))
    Write-Host "  SHA256：$hash"
}

# ---------------------------------------------------------------------------
# 7. 汇总
# ---------------------------------------------------------------------------
Write-Step '7/7 完成'
Write-Host "  版本      : $Version"
Write-Host "  暂存目录  : $Stage"
Write-Host "  输出目录  : $Dist"
Get-ChildItem $Dist -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending |
    Select-Object -First 5 | ForEach-Object { Write-Host ("  - {0}  ({1:N1} MB)" -f $_.Name, ($_.Length / 1MB)) }
Write-Host "`n安装（含静默）：" -ForegroundColor White
Write-Host "  .\TrackMeet-Setup-$Version.exe"
Write-Host "  .\TrackMeet-Setup-$Version.exe /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /DBHOST=127.0.0.1 /DBPORT=3306 /DBUSER=root /DBPASSWORD=*** /WEBPORT=8080 /ADMINPWD=Admin@123 /INITDB=1"
