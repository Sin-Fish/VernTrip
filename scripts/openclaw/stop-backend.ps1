. (Join-Path $PSScriptRoot "_common.ps1")

$process = Get-RecordedBackendProcess
if ($null -eq $process) {
    Write-Host "No matching VernTrip backend process is running."
    if (Test-Path -LiteralPath $script:BackendProcessPath) {
        Remove-Item -LiteralPath $script:BackendProcessPath -Force
    }
    exit 0
}

Stop-Process -Id $process.Id
Wait-Process -Id $process.Id -Timeout 20 -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $script:BackendProcessPath -Force -ErrorAction SilentlyContinue
Write-Host "VernTrip backend stopped (PID $($process.Id))."
