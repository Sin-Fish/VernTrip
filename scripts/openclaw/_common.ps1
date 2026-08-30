Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:RuntimeRoot = Join-Path $script:RepoRoot "openclaw-runtime"
$script:HostRoot = Join-Path $script:RuntimeRoot "host"
$script:PluginRoot = Join-Path $script:RuntimeRoot "plugin"
$script:StateRoot = Join-Path $script:RuntimeRoot "state"
$script:PackageRoot = Join-Path $script:StateRoot "packages"
$script:LogRoot = Join-Path $script:StateRoot "logs"
$script:SecretsPath = Join-Path $script:StateRoot "runtime-secrets.json"
$script:ProcessPath = Join-Path $script:StateRoot "gateway-process.json"
$script:BackendProcessPath = Join-Path $script:StateRoot "backend-process.json"
$script:OpenClawCliPath = Join-Path $script:HostRoot "node_modules\openclaw\openclaw.mjs"
$script:LocalNodePath = Join-Path $script:HostRoot "node_modules\node\bin\node.exe"

function Initialize-OpenClawDirectories {
    foreach ($path in @($script:StateRoot, $script:PackageRoot, $script:LogRoot, (Join-Path $script:StateRoot "workspace"))) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
    }
}

function New-SecureToken {
    $bytes = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    }
    finally {
        $random.Dispose()
    }
    return (($bytes | ForEach-Object { $_.ToString("x2") }) -join "")
}

function Initialize-RuntimeSecrets {
    Initialize-OpenClawDirectories
    if (Test-Path -LiteralPath $script:SecretsPath) {
        return
    }

    $secrets = [ordered]@{
        gatewayToken = New-SecureToken
        serviceToken = New-SecureToken
    }
    $secrets | ConvertTo-Json | Set-Content -LiteralPath $script:SecretsPath -Encoding UTF8
}

function Import-OpenClawProviderEnvironment {
    $supportedNames = @("DEEPSEEK_API_KEY", "AMAP_WEB_SERVICE_KEY")
    $values = @{}
    foreach ($fileName in @(".env", ".env.local")) {
        $path = Join-Path $script:RepoRoot $fileName
        if (-not (Test-Path -LiteralPath $path)) {
            continue
        }
        foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
            if ($line -match '^\s*#' -or $line -notmatch '=') {
                continue
            }
            $parts = $line -split '=', 2
            $name = $parts[0].Trim()
            if ($name -notin $supportedNames) {
                continue
            }
            $values[$name] = $parts[1].Trim().Trim('"').Trim("'")
        }
    }

    foreach ($name in $supportedNames) {
        $existing = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)
        if ([string]::IsNullOrWhiteSpace($existing) -and -not [string]::IsNullOrWhiteSpace($values[$name])) {
            [Environment]::SetEnvironmentVariable(
                $name,
                $values[$name],
                [EnvironmentVariableTarget]::Process
            )
        }
    }
}

function Set-OpenClawRuntimeEnvironment {
    Initialize-RuntimeSecrets
    Import-OpenClawProviderEnvironment
    $secrets = Get-Content -LiteralPath $script:SecretsPath -Encoding UTF8 -Raw | ConvertFrom-Json
    $env:OPENCLAW_STATE_DIR = $script:StateRoot
    $env:OPENCLAW_CONFIG_DIR = $script:StateRoot
    $env:OPENCLAW_CONFIG_PATH = Join-Path $script:StateRoot "openclaw.json"
    $env:OPENCLAW_WORKSPACE_DIR = Join-Path $script:StateRoot "workspace"
    $env:OPENCLAW_GATEWAY_TOKEN = $secrets.gatewayToken
    $env:VERNTRIP_OPENCLAW_SERVICE_TOKEN = $secrets.serviceToken
    $env:OPENCLAW_DISABLE_BONJOUR = "1"
}

function Get-LocalNodePath {
    if (-not (Test-Path -LiteralPath $script:LocalNodePath)) {
        throw "Project-local Node 24 is missing. Run scripts\openclaw\install.ps1 first."
    }
    return $script:LocalNodePath
}

function Normalize-ProcessPathEnvironment {
    $pathKeys = @(
        [Environment]::GetEnvironmentVariables([EnvironmentVariableTarget]::Process).Keys |
            Where-Object { $_ -ieq "PATH" }
    )
    if ($pathKeys.Count -gt 1) {
        # Windows PowerShell Start-Process rejects Path/PATH as duplicate dictionary keys.
        [Environment]::SetEnvironmentVariable(
            "PATH",
            $null,
            [EnvironmentVariableTarget]::Process
        )
    }
}

function Invoke-Npm {
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $npm = Get-Command npm.cmd -ErrorAction Stop
    Initialize-OpenClawDirectories
    $previousNpmCache = $env:npm_config_cache
    $env:npm_config_cache = Join-Path $script:StateRoot "npm-cache"
    Push-Location $WorkingDirectory
    try {
        & $npm.Source @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "npm command failed in $WorkingDirectory with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
        $env:npm_config_cache = $previousNpmCache
    }
}

function Invoke-OpenClaw {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Set-OpenClawRuntimeEnvironment
    $nodePath = Get-LocalNodePath
    if (-not (Test-Path -LiteralPath $script:OpenClawCliPath)) {
        throw "Project-local OpenClaw is missing. Run scripts\openclaw\install.ps1 first."
    }

    & $nodePath $script:OpenClawCliPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "OpenClaw command failed with exit code $LASTEXITCODE."
    }
}

function Get-GatewayProcessRecord {
    if (-not (Test-Path -LiteralPath $script:ProcessPath)) {
        return $null
    }
    return Get-Content -LiteralPath $script:ProcessPath -Encoding UTF8 -Raw | ConvertFrom-Json
}

function Get-RecordedGatewayProcess {
    return Get-RecordedRuntimeProcess -RecordPath $script:ProcessPath -ProcessName "node"
}

function Get-BackendProcessRecord {
    if (-not (Test-Path -LiteralPath $script:BackendProcessPath)) {
        return $null
    }
    return Get-Content -LiteralPath $script:BackendProcessPath -Encoding UTF8 -Raw | ConvertFrom-Json
}

function Get-RecordedBackendProcess {
    return Get-RecordedRuntimeProcess -RecordPath $script:BackendProcessPath -ProcessName "java"
}

function Get-RecordedRuntimeProcess {
    param(
        [Parameter(Mandatory = $true)][string]$RecordPath,
        [Parameter(Mandatory = $true)][string]$ProcessName
    )

    if (-not (Test-Path -LiteralPath $RecordPath)) {
        return $null
    }
    $record = Get-Content -LiteralPath $RecordPath -Encoding UTF8 -Raw | ConvertFrom-Json
    if ($null -eq $record) {
        return $null
    }

    $process = Get-Process -Id ([int]$record.pid) -ErrorAction SilentlyContinue
    if ($null -eq $process -or $process.ProcessName -ne $ProcessName) {
        return $null
    }

    $recordedStart = if ($record.startedAt -is [DateTime]) {
        # PowerShell 7 deserializes the trailing-Z JSON value as Unspecified on Windows.
        if ($record.startedAt.Kind -eq [DateTimeKind]::Unspecified) {
            [DateTime]::SpecifyKind($record.startedAt, [DateTimeKind]::Utc)
        } else {
            $record.startedAt.ToUniversalTime()
        }
    } else {
        [DateTimeOffset]::Parse(
            [string]$record.startedAt,
            [Globalization.CultureInfo]::InvariantCulture
        ).UtcDateTime
    }
    $actualStart = $process.StartTime.ToUniversalTime()
    if ([Math]::Abs(($recordedStart - $actualStart).TotalSeconds) -gt 2) {
        return $null
    }
    return $process
}
