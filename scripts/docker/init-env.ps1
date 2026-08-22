param(
    [string]$OutputPath = "deploy/.env",
    [switch]$ImportLocalSecrets,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$templatePath = Join-Path $repoRoot "deploy/.env.example"
$targetPath = Join-Path $repoRoot $OutputPath

if ((Test-Path -LiteralPath $targetPath) -and -not $Force) {
    throw "Environment file already exists: $targetPath. Use -Force to replace it."
}

function New-RandomHex {
    param([int]$ByteCount = 32)

    $bytes = New-Object byte[] $ByteCount
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    }
    finally {
        $random.Dispose()
    }
    return (($bytes | ForEach-Object { $_.ToString("x2") }) -join "")
}

$content = Get-Content -LiteralPath $templatePath -Encoding UTF8 -Raw
$content = $content.Replace(
    "replace-with-a-random-database-password",
    (New-RandomHex -ByteCount 24)
)
$content = $content.Replace(
    "replace-with-a-random-admin-password",
    (New-RandomHex -ByteCount 18)
)
$content = $content.Replace(
    "replace-with-a-random-gateway-token",
    (New-RandomHex)
)
$content = $content.Replace(
    "replace-with-a-random-service-token",
    (New-RandomHex)
)

if ($ImportLocalSecrets) {
    $supportedNames = @(
        "DEEPSEEK_API_KEY",
        "VITE_AMAP_JS_KEY",
        "VITE_AMAP_SECURITY_CODE",
        "AMAP_WEB_SERVICE_KEY"
    )
    $localValues = @{}
    foreach ($fileName in @(".env", ".env.local")) {
        $localPath = Join-Path $repoRoot $fileName
        if (-not (Test-Path -LiteralPath $localPath)) {
            continue
        }
        foreach ($line in Get-Content -LiteralPath $localPath -Encoding UTF8) {
            if ($line -match '^\s*#' -or $line -notmatch '=') {
                continue
            }
            $parts = $line -split '=', 2
            $name = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"').Trim("'")
            if ($name -in $supportedNames -and -not [string]::IsNullOrWhiteSpace($value)) {
                $localValues[$name] = $value
            }
        }
    }

    foreach ($name in $localValues.Keys) {
        $value = $localValues[$name]
        $content = [regex]::Replace(
            $content,
            "(?m)^$([regex]::Escape($name))=.*$",
            { param($match) "$name=$value" }
        )
    }
    Write-Host "Imported configured DeepSeek and AMap values from the local development environment."
}

$targetDirectory = Split-Path -Parent $targetPath
New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
Set-Content -LiteralPath $targetPath -Value $content -Encoding UTF8

Write-Host "Created $targetPath with generated local passwords and service tokens."
if (-not $ImportLocalSecrets) {
    Write-Host "Fill DEEPSEEK_API_KEY and the three AMap values before starting Docker Compose."
}
