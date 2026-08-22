param(
    [string]$EnvFile = "deploy/.env",
    [switch]$SkipBuild
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
    throw "Deployment environment is missing: $envPath. Run scripts/docker/init-env.ps1 first."
}

$baseArguments = @("compose", "--env-file", $envPath, "-f", $composePath)

Write-Host "Validating Docker Compose configuration..."
& $docker.Source @baseArguments "config" "--quiet"
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose configuration validation failed."
}

$upArguments = @($baseArguments) + @("up", "-d")
if (-not $SkipBuild) {
    $upArguments += "--build"
}

Write-Host "Starting VernTrip Docker stack..."
& $docker.Source @upArguments
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose startup failed."
}

& $docker.Source @baseArguments "ps"

$webPort = "80"
foreach ($line in Get-Content -LiteralPath $envPath -Encoding UTF8) {
    if ($line -match '^WEB_PORT=(.*)$' -and -not [string]::IsNullOrWhiteSpace($matches[1])) {
        $webPort = $matches[1].Trim().Trim('"').Trim("'")
        break
    }
}
$webUrl = if ($webPort -eq "80") { "http://localhost" } else { "http://localhost:$webPort" }
Write-Host "VernTrip is starting at $webUrl. Run scripts/docker/status.ps1 for health checks."
