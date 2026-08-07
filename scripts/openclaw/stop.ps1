. (Join-Path $PSScriptRoot "_common.ps1")

$process = Get-RecordedGatewayProcess
if ($null -eq $process) {
    Write-Host "No matching OpenClaw gateway process is running."
    if (Test-Path -LiteralPath $script:ProcessPath) {
        Remove-Item -LiteralPath $script:ProcessPath -Force
    }
    exit 0
}

Stop-Process -Id $process.Id
Wait-Process -Id $process.Id -Timeout 15 -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $script:ProcessPath -Force -ErrorAction SilentlyContinue
Write-Host "OpenClaw gateway stopped (PID $($process.Id))."
