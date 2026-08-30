param(
    [int]$Port = 8080,
    [switch]$SkipBuild,
    [ValidateSet("legacy", "shadow", "openclaw")]
    [string]$AssistantEngine = "openclaw"
)

. (Join-Path $PSScriptRoot "_common.ps1")

$existing = Get-RecordedBackendProcess
if ($null -ne $existing) {
    Write-Host "VernTrip backend is already running (PID $($existing.Id))."
    exit 0
}

Set-OpenClawRuntimeEnvironment
$env:ASSISTANT_ENGINE = $AssistantEngine
$env:OPENCLAW_BASE_URL = "http://127.0.0.1:18789"
$env:OPENCLAW_AGENT_ID = "verntrip-main"
$env:OPENCLAW_TIMEOUT = "180s"
$env:SERVER_PORT = $Port.ToString()

if (-not $SkipBuild) {
    $previousGradleHome = $env:GRADLE_USER_HOME
    $env:GRADLE_USER_HOME = Join-Path $script:RepoRoot ".gradle-home-rag"
    try {
        & (Join-Path $script:RepoRoot "gradlew.bat") `
            ":vernflow-backend:bootJar" `
            "--no-daemon" `
            "--console=plain" `
            "--no-configuration-cache" `
            "--project-cache-dir" `
            (Join-Path $script:RepoRoot ".gradle-project-cache")
        if ($LASTEXITCODE -ne 0) {
            throw "Backend build failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        $env:GRADLE_USER_HOME = $previousGradleHome
    }
}

$jar = Get-ChildItem (Join-Path $script:RepoRoot "vernflow-backend\build\libs\vernflow-backend-*.jar") |
    Where-Object { $_.Name -notlike "*-plain.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) {
    throw "Backend jar is missing. Run without -SkipBuild first."
}

$javaPath = (Get-Command java.exe -ErrorAction Stop).Source
Normalize-ProcessPathEnvironment
$stdoutPath = Join-Path $script:LogRoot "backend.out.log"
$stderrPath = Join-Path $script:LogRoot "backend.err.log"
$startArguments = @{
    FilePath = $javaPath
    ArgumentList = @("-jar", $jar.FullName)
    WorkingDirectory = $script:RepoRoot
    WindowStyle = "Hidden"
    RedirectStandardOutput = $stdoutPath
    RedirectStandardError = $stderrPath
    PassThru = $true
}
$process = Start-Process @startArguments

$record = [ordered]@{
    pid = $process.Id
    startedAt = $process.StartTime.ToUniversalTime().ToString("O")
    port = $Port
    assistantEngine = $AssistantEngine
}
$record | ConvertTo-Json | Set-Content -LiteralPath $script:BackendProcessPath -Encoding UTF8

for ($attempt = 0; $attempt -lt 90; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) {
        Remove-Item -LiteralPath $script:BackendProcessPath -Force -ErrorAction SilentlyContinue
        $errorText = if (Test-Path -LiteralPath $stdoutPath) {
            Get-Content -LiteralPath $stdoutPath -Encoding UTF8 -Tail 60 | Out-String
        } else {
            "No application log was created."
        }
        throw "VernTrip backend exited during startup.`n$errorText"
    }

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.ConnectAsync("127.0.0.1", $Port)
        if ($connect.Wait(500) -and $client.Connected) {
            Write-Host "VernTrip backend is listening at http://127.0.0.1:$Port (PID $($process.Id))."
            exit 0
        }
    }
    catch {
        # Spring can take several seconds to run Flyway and initialize JPA.
    }
    finally {
        $client.Dispose()
    }
}

throw "VernTrip backend did not listen within 90 seconds. See $stdoutPath"
