<#
  本地启动后端（无需安装 Maven）
  ------------------------------------------------------------------
  原理：backend\target\classes 已有编译产物与 resources；
        依赖 jar 按本工程 pom.xml 声明的版本，从 %USERPROFILE%\.m2\repository 取，
        因此只要本机有 JDK 就能跑，不需要 mvn。

  【重要】为什么必须固定版本，不能"取最高版本"：
      .m2 里同时存在其它工程的 Spring Boot 4.1.1 / Spring 7.0.9 / Jackson 3.x。
      若按最高版本组装 classpath，会与 pom 声明的 Spring Boot 3.2.5 冲突，
      启动时报 NoClassDefFoundError（例如 Jackson Module 找不到）。
      因此下表严格对齐 backend\pom.xml。

  用法（在仓库根目录执行）：
      powershell -ExecutionPolicy Bypass -File scripts\run-backend.ps1

  前置条件：
      1. MySQL80 服务已启动，且已执行 database/schema.sql + data.sql
      2. Redis 在 127.0.0.1:6379 运行（见 scripts\start-redis.ps1）
#>

$ErrorActionPreference = 'Stop'

$repoRoot   = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'
$m2         = Join-Path $env:USERPROFILE '.m2\repository'

if (-not (Test-Path $m2)) { throw "找不到本地 Maven 仓库：$m2" }
if (-not (Test-Path (Join-Path $backendDir 'target\classes'))) {
    throw "缺少 backend\target\classes，请先编译后端（或在 IDE 中 build 一次）"
}

# artifact 名 -> 期望版本（与 backend\pom.xml 对齐）
$deps = [ordered]@{
    # Spring Boot 3.2.5 / Spring Framework 6.1.6
    'spring-boot'                              = '3.2.5'
    'spring-boot-autoconfigure'                = '3.2.5'
    'spring-context'                           = '6.1.6'
    'spring-core'                              = '6.1.6'
    'spring-beans'                             = '6.1.6'
    'spring-web'                               = '6.1.6'
    'spring-webmvc'                            = '6.1.6'
    'spring-jdbc'                              = '6.1.6'
    'spring-tx'                                = '6.1.6'
    'spring-aop'                               = '6.1.6'
    'spring-expression'                        = '6.1.6'
    'spring-websocket'                         = '6.1.6'
    'spring-messaging'                         = '6.1.6'
    'spring-jcl'                               = '6.1.6'
    'spring-data-redis'                        = '3.2.5'
    'spring-data-keyvalue'                     = '3.2.5'
    'spring-data-commons'                      = '3.2.5'
    'spring-security-crypto'                   = '6.2.4'
    # MyBatis-Plus 3.5.5 / MyBatis 3.5.15
    'mybatis-plus-core'                        = '3.5.5'
    'mybatis-plus-extension'                   = '3.5.5'
    'mybatis-plus-annotation'                  = '3.5.5'
    'mybatis-plus-spring-boot3-starter'        = '3.5.5'
    'mybatis-plus-spring-boot-autoconfigure'   = '3.5.5'
    'mybatis'                                  = '3.5.15'
    'mybatis-spring'                           = '3.0.3'
    # 其它
    'lombok'                                   = '1.18.34'
    'jjwt-api'                                 = '0.12.5'
    'jackson-databind'                         = '2.15.4'
    'jackson-core'                             = '2.15.4'
    'jackson-annotations'                      = '2.15.4'
    'jackson-datatype-jsr310'                  = '2.15.4'
    'jakarta.validation-api'                   = '3.0.2'
    'jakarta.annotation-api'                   = '2.1.1'
    'tomcat-embed-core'                        = '10.1.20'
    'slf4j-api'                                = '2.0.13'
    'swagger-annotations-jakarta'              = ''
    'springdoc-openapi-starter-common'         = '2.3.0'
    'mysql-connector-j'                        = ''
    'lettuce-core'                             = ''
    'commons-pool2'                            = ''
    'easyexcel'                                = '3.3.4'
}

Write-Host '[1/3] 组装 classpath（版本对齐 backend\pom.xml）...' -ForegroundColor Cyan

$found = @()
$missing = @()
foreach ($k in $deps.Keys) {
    $cands = Get-ChildItem -Path $m2 -Filter "$k-*.jar" -Recurse -File -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' }
    if ($deps[$k]) { $cands = $cands | Where-Object { $_.Name -like "*$($deps[$k])*" } }
    $hit = $cands | Select-Object -First 1
    if ($hit) { $found += $hit.FullName } else { $missing += $k }
}

if ($missing.Count -gt 0) {
    Write-Host ("      缺少依赖：{0}" -f ($missing -join ', ')) -ForegroundColor Yellow
    Write-Host '      请联网执行一次 mvn dependency:go-offline，或让 IDE 同步依赖' -ForegroundColor Yellow
}

$cpParts = @((Join-Path $backendDir 'target\classes')) + $found
$classpath = $cpParts -join ';'
Write-Host ("      已解析 {0} 个 jar" -f $found.Count)

Write-Host '[2/3] 检查依赖服务...' -ForegroundColor Cyan
$redisUp = Test-NetConnection -ComputerName 127.0.0.1 -Port 6379 -InformationLevel Quiet -WarningAction SilentlyContinue
$mysqlUp = Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue
Write-Host ("      Redis 6379 : {0}" -f $(if ($redisUp) { 'OK' } else { '未启动' }))
Write-Host ("      MySQL 3306 : {0}" -f $(if ($mysqlUp) { 'OK' } else { '未启动' }))

Write-Host '[3/3] 启动 Spring Boot（profile = dev,local）...' -ForegroundColor Cyan
Write-Host '      接口文档： http://localhost:8080/api/doc.html'
Write-Host '      按 Ctrl+C 停止'
Write-Host ''

Set-Location $backendDir
& java '-Dfile.encoding=UTF-8' -cp $classpath com.campus.delivery.CampusDeliveryApplication --spring.profiles.active=dev,local
