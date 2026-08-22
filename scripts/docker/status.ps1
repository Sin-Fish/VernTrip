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

$baseArguments = @("compose", "--env-file", $envPath, "-f", $composePath)
& $docker.Source @baseArguments "ps"
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read Docker Compose service status."
}

function Get-EnvValue {
    param(
        [string]$Name,
        [string]$DefaultValue
    )

    foreach ($line in Get-Content -LiteralPath $envPath -Encoding UTF8) {
        if ($line -match "^$([regex]::Escape($Name))=(.*)$") {
            $value = $matches[1].Trim().Trim('"').Trim("'")
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                return $value
            }
        }
    }
    return $DefaultValue
}

$webPort = Get-EnvValue -Name "WEB_PORT" -DefaultValue "80"
$backendPort = Get-EnvValue -Name "BACKEND_PORT" -DefaultValue "8080"
$gatewayPort = Get-EnvValue -Name "OPENCLAW_GATEWAY_PORT" -DefaultValue "18789"

$checks = @(
    [ordered]@{ name = "Web"; url = "http://127.0.0.1:$webPort/healthz" },
    [ordered]@{ name = "Backend"; url = "http://127.0.0.1:$backendPort/actuator/health" },
    [ordered]@{ name = "OpenClaw"; url = "http://127.0.0.1:$gatewayPort/healthz" }
)

foreach ($check in $checks) {
    try {
        $response = Invoke-WebRequest -Uri $check.url -UseBasicParsing -TimeoutSec 5
        Write-Host "$($check.name): healthy ($($response.StatusCode))"
    }
    catch {
        Write-Host "$($check.name): unavailable ($($check.url))"
    }
}
