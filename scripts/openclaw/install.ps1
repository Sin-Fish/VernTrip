. (Join-Path $PSScriptRoot "_common.ps1")

Initialize-OpenClawDirectories

Write-Host "Installing project-local Node 24 and OpenClaw 2026.7.1..."
Invoke-Npm -WorkingDirectory $script:HostRoot -Arguments @("install")

Write-Host "Building the VernTrip OpenClaw plugin..."
Invoke-Npm -WorkingDirectory $script:PluginRoot -Arguments @("install", "--ignore-scripts")
Invoke-Npm -WorkingDirectory $script:PluginRoot -Arguments @("run", "check")
Invoke-Npm -WorkingDirectory $script:PluginRoot -Arguments @("run", "build")
Invoke-Npm -WorkingDirectory $script:PluginRoot -Arguments @(
    "pack",
    "--pack-destination",
    $script:PackageRoot
)

$pluginPackage = Join-Path $script:PackageRoot "verntrip-openclaw-plugin-0.1.0.tgz"
if (-not (Test-Path -LiteralPath $pluginPackage)) {
    throw "Plugin package was not created: $pluginPackage"
}

Initialize-RuntimeSecrets
Write-Host "Installing the plugin into the isolated OpenClaw state..."
Invoke-OpenClaw -Arguments @("plugins", "install", "npm-pack:$pluginPackage", "--force")

$batchFile = Join-Path $script:RuntimeRoot "config\verntrip.native.config.batch.json"
Invoke-OpenClaw -Arguments @("config", "set", "--batch-file", $batchFile)
$skillsPath = Join-Path $script:RuntimeRoot "skills"
$nativePathsBatchFile = Join-Path $script:StateRoot "native-paths.config.batch.json"
$nativePathsBatch = @(
    [ordered]@{
        path = "skills.load.extraDirs"
        value = @($skillsPath)
    }
)
ConvertTo-Json -InputObject $nativePathsBatch -Depth 4 |
    Set-Content -LiteralPath $nativePathsBatchFile -Encoding UTF8
Invoke-OpenClaw -Arguments @("config", "set", "--batch-file", $nativePathsBatchFile)

$nodePath = Get-LocalNodePath
Set-OpenClawRuntimeEnvironment
$agentJson = & $nodePath $script:OpenClawCliPath agents list --json
if ($LASTEXITCODE -ne 0) {
    throw "Unable to list OpenClaw agents."
}
$agentData = ($agentJson | Out-String) | ConvertFrom-Json
$agents = if ($agentData.PSObject.Properties.Name -contains "agents") {
    $agentData.agents
} else {
    $agentData
}
$vernTripAgent = $agents | Where-Object { $_.id -eq "verntrip-main" } | Select-Object -First 1
if ($null -eq $vernTripAgent) {
    $agentWorkspace = Join-Path $script:StateRoot "workspace-verntrip"
    Invoke-OpenClaw -Arguments @(
        "agents",
        "add",
        "verntrip-main",
        "--workspace",
        $agentWorkspace,
        "--non-interactive"
    )
}

Invoke-OpenClaw -Arguments @("config", "validate")
Invoke-OpenClaw -Arguments @("plugins", "inspect", "verntrip", "--runtime", "--json")

Write-Host "OpenClaw native runtime installation is ready."
Write-Host "Start it with scripts\openclaw\start.ps1"
