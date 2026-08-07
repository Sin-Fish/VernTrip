$root = Split-Path -Parent $PSScriptRoot
$stdout = Join-Path $root "vernflow-backend-dev.out.log"
$stderr = Join-Path $root "vernflow-backend-dev.err.log"
$jar = Get-ChildItem (Join-Path $root "vernflow-backend\build\libs\vernflow-backend-*.jar") |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "Backend JAR was not found. Run gradlew :vernflow-backend:bootJar first."
}

$process = Start-Process `
    -FilePath "java.exe" `
    -ArgumentList "-jar", $jar.FullName `
    -WorkingDirectory $root `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -WindowStyle Hidden `
    -PassThru

Write-Output $process.Id
