param(
    [string]$EnvFile = "deploy/.env"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$composePath = Join-Path $repoRoot "deploy/docker-compose.yml"
$envPath = Join-Path $repoRoot $EnvFile
$docker = Get-Command docker -ErrorAction SilentlyContinue

if ($null -eq $docker) {
    throw "Docker CLI is not installed or is not available in PATH."
}
if (-not (Test-Path -LiteralPath $envPath)) {
    throw "Deployment environment is missing: $envPath."
}

& $docker.Source compose --env-file $envPath -f $composePath down
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose shutdown failed."
}
