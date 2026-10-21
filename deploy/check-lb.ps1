<#
.SYNOPSIS
    验证 Nginx 负载均衡：轮询是否生效、单实例故障是否自动转移。

.DESCRIPTION
    用 Host 头模拟域名解析，所以即使没配 hosts 文件也能验证——
    请求发到 127.0.0.1:8088，但 Host 写成 student.campus.local，
    Nginx 会按 server_name 命中对应的 server 块。

    如果某次响应头是 "127.0.0.1:8081, 127.0.0.1:8080"，
    说明 Nginx 先试了 8081（失败）再自动重试到 8080，
    也就是 proxy_next_upstream 生效了 —— 用户看到的仍是 HTTP 200。

.EXAMPLE
    .\deploy\check-lb.ps1
.EXAMPLE
    .\deploy\check-lb.ps1 -Times 10
#>
param(
    [string] $ServerName = 'student.campus.local',
    [int]    $Port       = 8088,
    [int]    $Times      = 6,
    [string] $Path       = '/api/shops'
)

Add-Type -AssemblyName System.Net.Http

$url = "http://127.0.0.1:$Port$Path"
Write-Host "目标: http://$ServerName`:$Port$Path  （Host 头模拟域名）"
Write-Host ''

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(15)

$served  = @()
$retried = 0
$failed  = 0

for ($i = 1; $i -le $Times; $i++) {
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, $url)
    $req.Headers.Host = $ServerName
    try {
        $resp = $client.SendAsync($req).Result
        $code = [int]$resp.StatusCode

        if (-not $resp.Headers.Contains('X-Upstream')) {
            Write-Host ("  第 {0,2} 次  HTTP {1}  upstream=(无)" -f $i, $code) -ForegroundColor Yellow
            $failed++
            $resp.Dispose()
            continue
        }

        $raw   = ($resp.Headers.GetValues('X-Upstream') -join ',')
        # 必须用 @() 包一层：只有一个值时 -split 返回字符串而不是数组，
        # 直接取 [-1] 会变成取最后一个字符（曾导致显示 "0" / "1"）
        $chain = @($raw -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        if ($chain.Count -gt 1) { $retried++ }
        $actual = $chain[-1]
        $served += $actual

        $note = if ($chain.Count -gt 1) { '  <- 先试失败实例后自动重试' } else { '' }
        Write-Host ("  第 {0,2} 次  HTTP {1}  实际服务={2}{3}" -f $i, $code, $actual, $note)
        $resp.Dispose()
    }
    catch {
        Write-Host ("  第 {0,2} 次  请求失败：{1}" -f $i, $_.Exception.Message) -ForegroundColor Red
        $failed++
    }
}
$client.Dispose()

Write-Host ''
$distinct = $served | Sort-Object -Unique

if ($failed -gt 0) {
    Write-Host "结论：有 $failed 次请求失败 —— 两个实例是否都启动了？（deploy\start-instances.ps1）" -ForegroundColor Red
}
elseif ($distinct.Count -ge 2) {
    Write-Host ("结论：轮询生效，请求被分到 {0} 台实例：{1}" -f $distinct.Count, ($distinct -join ' / ')) -ForegroundColor Green
}
elseif ($distinct.Count -eq 1) {
    Write-Host ("结论：所有请求都落在 {0} —— 另一台没启动，或刚被摘除（fail_timeout=10s 内不再尝试）" -f $distinct[0]) -ForegroundColor Yellow
}
else {
    Write-Host '结论：没拿到 X-Upstream 头，检查 campus-locations.conf 里的 add_header' -ForegroundColor Yellow
}

if ($retried -gt 0) {
    Write-Host ("      其中 {0} 次经历了「失败后自动重试」，说明故障转移生效" -f $retried) -ForegroundColor Cyan
}
