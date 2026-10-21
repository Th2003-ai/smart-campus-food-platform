<#
.SYNOPSIS
    停止由 start-instances.ps1 启动的后端实例。

.DESCRIPTION
    只杀监听指定端口的 java 进程，不会误伤 IDE 里跑的实例以外的进程。
#>
param(
    [int[]] $Ports = @(8080, 8081)
)

foreach ($port in $Ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host "[skip ] $port 未被监听"
        continue
    }
    foreach ($c in $conns) {
        $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
        if ($p -and $p.ProcessName -eq 'java') {
            Stop-Process -Id $p.Id -Force
            Write-Host "[stop ] 已停止 $port （PID $($p.Id)，已释放到 Nginx 摘除窗口）" -ForegroundColor Yellow
        } else {
            Write-Host "[warn ] $port 被非 java 进程占用：$($p.ProcessName)，已跳过" -ForegroundColor Red
        }
    }
}
