<#
  启动本地 Redis（Windows 免安装版 redis-x64-5.0.14.1）
  ------------------------------------------------------------------
  用法：
      powershell -ExecutionPolicy Bypass -File scripts\start-redis.ps1

  默认 Redis 目录：D:\SE\redis-5.0.14.1
  如需改路径，设置环境变量后执行：$env:REDIS_HOME='其他目录'
#>

$ErrorActionPreference = 'Stop'

$redisHome = if ($env:REDIS_HOME) { $env:REDIS_HOME } else { 'D:\SE\redis-5.0.14.1' }
$server    = Join-Path $redisHome 'redis-server.exe'
$cli       = Join-Path $redisHome 'redis-cli.exe'

if (-not (Test-Path $server)) { throw "找不到 $server ，请确认 REDIS_HOME 路径" }

# 已在运行则不重复启动
$running = Get-NetTCPConnection -LocalPort 6379 -State Listen -ErrorAction SilentlyContinue
if ($running) {
    Write-Host 'Redis 已在 6379 运行，跳过启动。' -ForegroundColor Yellow
} else {
    Write-Host '正在启动 Redis...' -ForegroundColor Cyan
    Start-Process -FilePath $server -ArgumentList 'redis.windows.conf' `
                  -WorkingDirectory $redisHome -WindowStyle Minimized
    Start-Sleep -Seconds 2
}

Write-Host ('PING -> ' + (& $cli ping)) -ForegroundColor Green
& $cli info server | Select-String -Pattern 'redis_version|tcp_port'

Write-Host ''
Write-Host '提示：若希望 Redis 随开机自动启动，可在管理员 PowerShell 中执行：' -ForegroundColor DarkGray
Write-Host ("      {0} --service-install redis.windows.conf --loglevel notice" -f $server) -ForegroundColor DarkGray
Write-Host '      net start Redis' -ForegroundColor DarkGray
