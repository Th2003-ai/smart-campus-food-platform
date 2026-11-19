<#
  本地启动后端（不需要事先安装 Maven，也不需要手工维护依赖清单）
  ------------------------------------------------------------------
  原理：
    1) 用仓库自带的 Maven Wrapper（backend\mvnw.cmd）执行
       dependency:build-classpath，把 pom.xml 解析出的运行时依赖
       写进 backend\target\run-classpath.txt；
    2) 把 backend\target\classes 放在最前面，拼出 java -cp 的 classpath；
    3) 用 java 直接启动 CampusDeliveryApplication。

  为什么不再手写依赖表：
    旧版本在脚本里维护了一张「artifact 名 + 版本」的表，pom 一升级就会漏包。
    实测漏掉 snakeyaml 时启动直接报：
      Attempted to load Config resource 'class path resource [application.yml]'
      but snakeyaml was not found on the classpath
    现在依赖完全由 Maven 从 pom.xml 推导，不会再有这类漏项。

  用法（在仓库根目录执行）：
      powershell -ExecutionPolicy Bypass -File scripts\run-backend.ps1

  前置条件：
      1. MySQL80 服务已启动，且已执行 database/schema.sql + data.sql
      2. Redis 在 127.0.0.1:6379 运行（见 scripts\start-redis.ps1）

  可选参数：
      -SkipBuild     不重新解析依赖，直接复用 target\run-classpath.txt
      -Rebuild       强制重新编译后端再启动
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'

$repoRoot   = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'
$mvnw       = Join-Path $backendDir 'mvnw.cmd'
$classesDir = Join-Path $backendDir 'target\classes'
$cpFile     = Join-Path $backendDir 'target\run-classpath.txt'
$pomFile    = Join-Path $backendDir 'pom.xml'

if (-not (Test-Path $mvnw))    { throw "找不到 Maven Wrapper：$mvnw" }
if (-not (Test-Path $pomFile)) { throw "找不到 pom.xml：$pomFile" }

# ---------------------------------------------------------------- 依赖服务
Write-Host '[1/3] 检查依赖服务...' -ForegroundColor Cyan
$redisUp = Test-NetConnection -ComputerName 127.0.0.1 -Port 6379 -InformationLevel Quiet -WarningAction SilentlyContinue
$mysqlUp = Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue
Write-Host ("      Redis 6379 : {0}" -f $(if ($redisUp) { 'OK' } else { '未启动 —— 先执行 scripts\start-redis.ps1' }))
Write-Host ("      MySQL 3306 : {0}" -f $(if ($mysqlUp) { 'OK' } else { '未启动 —— 以管理员身份执行 net start MySQL80' }))

# ---------------------------------------------------------------- 编译 / 依赖
function Invoke-Maven {
    param([string[]]$Arguments)
    & $mvnw @Arguments
    if ($LASTEXITCODE -ne 0) { throw "mvnw 执行失败（退出码 $LASTEXITCODE）：mvnw $($Arguments -join ' ')" }
}

$needCompile   = $Rebuild -or -not (Test-Path $classesDir)
$needClasspath = -not $SkipBuild -and (
    -not (Test-Path $cpFile) -or
    (Get-Item $pomFile).LastWriteTime -gt (Get-Item $cpFile).LastWriteTime
)

if ($needCompile) {
    Write-Host '[2/3] 编译后端（mvnw compile）...' -ForegroundColor Cyan
    Push-Location $backendDir
    try { Invoke-Maven @('-B', '-DskipTests', 'compile') } finally { Pop-Location }
}
else {
    Write-Host '[2/3] 复用已有编译产物 backend\target\classes' -ForegroundColor DarkGray
}

if ($needClasspath) {
    Write-Host '      解析运行时依赖（mvnw dependency:build-classpath）...' -ForegroundColor Cyan
    Push-Location $backendDir
    try {
        Invoke-Maven @('-q', '-B', 'dependency:build-classpath',
                       "-Dmdep.outputFile=$cpFile", '-DincludeScope=runtime')
    }
    finally { Pop-Location }
}

if (-not (Test-Path $cpFile)) {
    throw "缺少依赖清单 $cpFile，请去掉 -SkipBuild 再执行一次。"
}

$deps = (Get-Content $cpFile -Raw).Trim()
if (-not $deps) { throw "依赖清单为空：$cpFile" }

# snakeyaml 缺失是旧版脚本最典型的故障，这里显式兜底
if ($deps -notmatch 'snakeyaml') {
    throw "classpath 里没有 snakeyaml，Maven 解析异常，请删除 $cpFile 后重跑本脚本。"
}

$classpath = @($classesDir) + $deps

# ---------------------------------------------------------------- 启动
Write-Host '[3/3] 启动 Spring Boot（profile = dev,local）...' -ForegroundColor Cyan
Write-Host ("      已加载 {0} 个 classpath 条目" -f $classpath.Split(';').Count)
Write-Host '      接口文档： http://localhost:8080/api/doc.html'
Write-Host '      按 Ctrl+C 停止'
Write-Host ''

Set-Location $backendDir
& java '-Dfile.encoding=UTF-8' -cp ($classpath -join ';') com.campus.delivery.CampusDeliveryApplication --spring.profiles.active=dev,local
