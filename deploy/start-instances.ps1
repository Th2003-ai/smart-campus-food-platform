<#
.SYNOPSIS
    启动两个后端实例（8080 / 8081），用于演示 Nginx 负载均衡与故障转移。

.DESCRIPTION
    前提：
      1) backend 已打包：.\mvnw.cmd -o -DskipTests package
      2) MySQL 与 Redis 已启动（两个实例共用同一套）

    两个实例跑的是同一个 jar、同一份配置，只有 --server.port 不同。
    因此任何角色的请求都能落到任意一台 —— 横向扩展是按整体扩，不是按端扩。

.EXAMPLE
    .\deploy\start-instances.ps1
.EXAMPLE
    .\deploy\start-instances.ps1 -Ports 8080,8081,8082
#>
param(
    [string] $Jar      = '',
    [string] $JavaHome = 'C:\Program Files\Java\jdk-17',
    [int[]]  $Ports    = @(8080, 8081)
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not $Jar) {
    $candidate = Get-ChildItem (Join-Path $repoRoot 'backend\target\*.jar') -ErrorAction SilentlyContinue |
                 Where-Object { $_.Name -notmatch 'original' } |
                 Select-Object -First 1
    if (-not $candidate) {
        throw '未找到 jar，请先在 backend 目录执行：.\mvnw.cmd -o -DskipTests package'
    }
    $Jar = $candidate.FullName
}

$java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path $java)) {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $cmd) { throw '找不到 java，请用 -JavaHome 指定 JDK 路径' }
    $java = $cmd.Source
}

Write-Host "jar : $Jar"
Write-Host "java: $java"
Write-Host ''

foreach ($port in $Ports) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "[skip ] $port 已在监听，跳过" -ForegroundColor Yellow
        continue
    }

    $logDir = Join-Path $repoRoot 'logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $log = Join-Path $logDir "instance-$port.log"

    Start-Process -FilePath $java `
        -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', $Jar, "--server.port=$port") `
        -WorkingDirectory (Join-Path $repoRoot 'backend') `
        -WindowStyle Minimized `
        -RedirectStandardOutput $log `
        -RedirectStandardError ($log + '.err')

    Write-Host "[start] http://localhost:$port/api  （日志：$log）" -ForegroundColor Green
}

Write-Host ''
Write-Host '启动约需 10~20 秒。之后用 deploy\check-lb.ps1 验证轮询与故障转移。'
