& (Join-Path $PSScriptRoot "stop.ps1")
$gatewayExitCode = $LASTEXITCODE

& (Join-Path $PSScriptRoot "stop-backend.ps1")
$backendExitCode = $LASTEXITCODE

if ($gatewayExitCode -ne 0) {
    exit $gatewayExitCode
}
exit $backendExitCode
