param(
    [switch]$Json
)

. (Join-Path $PSScriptRoot "_common.ps1")

$record = Get-GatewayProcessRecord
$process = Get-RecordedGatewayProcess
$port = if ($null -ne $record -and $null -ne $record.port) { [int]$record.port } else { 18789 }
$healthy = $false

if ($null -ne $process) {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/healthz" -UseBasicParsing -TimeoutSec 2
        $healthy = $response.StatusCode -eq 200
    }
    catch {
        $healthy = $false
    }
}

$status = [ordered]@{
    running = $null -ne $process
    healthy = $healthy
    pid = if ($null -ne $process) { $process.Id } else { $null }
    port = $port
}

if ($Json) {
    $status | ConvertTo-Json
} else {
    $statusText = if ($healthy) { "healthy" } elseif ($null -ne $process) { "starting/unhealthy" } else { "stopped" }
    Write-Host "OpenClaw gateway: $statusText"
    if ($null -ne $process) {
        Write-Host "PID: $($process.Id), URL: http://127.0.0.1:$port"
    }
}
