param(
    [int]$Port = 18789
)

. (Join-Path $PSScriptRoot "_common.ps1")

$existing = Get-RecordedGatewayProcess
if ($null -ne $existing) {
    Write-Host "OpenClaw gateway is already running (PID $($existing.Id))."
    exit 0
}

Set-OpenClawRuntimeEnvironment
$nodePath = Get-LocalNodePath
$stdoutPath = Join-Path $script:LogRoot "gateway.out.log"
$stderrPath = Join-Path $script:LogRoot "gateway.err.log"

Normalize-ProcessPathEnvironment
$startArguments = @{
    FilePath = $nodePath
    ArgumentList = @($script:OpenClawCliPath, "gateway", "run", "--bind", "loopback", "--port", $Port)
    WindowStyle = "Hidden"
    RedirectStandardOutput = $stdoutPath
    RedirectStandardError = $stderrPath
    PassThru = $true
}
$process = Start-Process @startArguments

$record = [ordered]@{
    pid = $process.Id
    startedAt = $process.StartTime.ToUniversalTime().ToString("O")
    port = $Port
}
$record | ConvertTo-Json | Set-Content -LiteralPath $script:ProcessPath -Encoding UTF8

$healthUrl = "http://127.0.0.1:$Port/healthz"
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) {
        $errorText = if (Test-Path -LiteralPath $stderrPath) {
            Get-Content -LiteralPath $stderrPath -Encoding UTF8 -Tail 30 | Out-String
        } else {
            "No error log was created."
        }
        throw "OpenClaw gateway exited during startup.`n$errorText"
    }
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            Write-Host "OpenClaw gateway is healthy at http://127.0.0.1:$Port (PID $($process.Id))."
            exit 0
        }
    }
    catch {
        # The gateway can take a few seconds to initialize plugins.
    }
}

throw "OpenClaw gateway did not become healthy within 30 seconds. See $stderrPath"
