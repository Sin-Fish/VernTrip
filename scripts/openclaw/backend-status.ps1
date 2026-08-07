param(
    [switch]$Json
)

. (Join-Path $PSScriptRoot "_common.ps1")

$record = Get-BackendProcessRecord
$process = Get-RecordedBackendProcess
$port = if ($null -ne $record -and $null -ne $record.port) { [int]$record.port } else { 8080 }
$reachable = $false

if ($null -ne $process) {
    try {
        Invoke-WebRequest `
            -Uri "http://127.0.0.1:$port/api/user/auth/profile" `
            -UseBasicParsing `
            -TimeoutSec 2 | Out-Null
        $reachable = $true
    }
    catch {
        $reachable = $null -ne $_.Exception.Response
    }
}

$status = [ordered]@{
    running = $null -ne $process
    reachable = $reachable
    pid = if ($null -ne $process) { $process.Id } else { $null }
    port = $port
    assistantEngine = if ($null -ne $record -and $null -ne $record.assistantEngine) {
        $record.assistantEngine
    } else {
        "unknown"
    }
}

if ($Json) {
    $status | ConvertTo-Json
} else {
    $statusText = if ($reachable) { "healthy" } elseif ($null -ne $process) { "starting/unhealthy" } else { "stopped" }
    Write-Host "VernTrip backend: $statusText"
    if ($null -ne $process) {
        Write-Host "PID: $($process.Id), URL: http://127.0.0.1:$port"
        Write-Host "Assistant engine: $($status.assistantEngine)"
    }
}
