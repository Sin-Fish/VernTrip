param(
    [switch]$SkipBuild,
    [ValidateSet("legacy", "shadow", "openclaw")]
    [string]$AssistantEngine = "openclaw"
)

$backendArguments = @{
    AssistantEngine = $AssistantEngine
}
if ($SkipBuild) {
    $backendArguments.SkipBuild = $true
}

& (Join-Path $PSScriptRoot "start-backend.ps1") @backendArguments
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& (Join-Path $PSScriptRoot "start.ps1")
exit $LASTEXITCODE
