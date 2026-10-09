# One-command dev launcher for Windows: builds + starts the Rust sidecar, waits
# for health, then runs the Flutter shell with HOT RELOAD pointed at it. The
# sidecar is stopped automatically when the Flutter session exits.
#
# Usage:
#   pwsh desktop/scripts/run-dev.ps1
#   pwsh desktop/scripts/run-dev.ps1 -d windows
#   $env:ABK_DEV_PORT=38999; pwsh desktop/scripts/run-dev.ps1
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$DesktopDir = Join-Path $RootDir "desktop"
$FlutterDir = Join-Path $DesktopDir "flutter_app"
$Port = if ($env:ABK_DEV_PORT) { $env:ABK_DEV_PORT } else { "38765" }
$BaseUrl = "http://127.0.0.1:$Port"

# Locate flutter: explicit FLUTTER_SDK, else PATH.
if ($env:FLUTTER_SDK -and (Test-Path (Join-Path $env:FLUTTER_SDK "bin\flutter.bat"))) {
    $Flutter = Join-Path $env:FLUTTER_SDK "bin\flutter.bat"
} elseif (Get-Command flutter -ErrorAction SilentlyContinue) {
    $Flutter = "flutter"
} else {
    throw "flutter not found. Set FLUTTER_SDK to your flutter install path."
}

$DeviceArgs = if ($args.Count -gt 0) { $args } else { @("-d", "windows") }

Write-Host "==> Building sidecar (abk_sidecar)"
Push-Location $DesktopDir
try { cargo build --bin abk_sidecar } finally { Pop-Location }
$SidecarBin = Join-Path $DesktopDir "target\debug\abk_sidecar.exe"

Write-Host "==> Starting sidecar on $BaseUrl"
$env:ABK_DESKTOP_HOST = "127.0.0.1"
$env:ABK_DESKTOP_APP_ROOT = $RootDir
$Sidecar = Start-Process -FilePath $SidecarBin -ArgumentList @("--port", $Port) -PassThru -NoNewWindow

try {
    Write-Host "==> Waiting for sidecar health"
    $healthy = $false
    for ($i = 0; $i -lt 60; $i++) {
        if ($Sidecar.HasExited) { throw "sidecar exited before becoming healthy" }
        try {
            Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/v1/health" -TimeoutSec 2 | Out-Null
            $healthy = $true
            break
        } catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $healthy) { throw "sidecar did not become healthy in time" }
    Write-Host "==> Sidecar healthy"

    Write-Host "==> flutter run $($DeviceArgs -join ' ') (hot reload; press q to quit)"
    $env:ABK_DESKTOP_BASE_URL = $BaseUrl
    Push-Location $FlutterDir
    try { & $Flutter run @DeviceArgs } finally { Pop-Location }
}
finally {
    if (-not $Sidecar.HasExited) {
        Write-Host "==> Stopping sidecar (pid $($Sidecar.Id))"
        Stop-Process -Id $Sidecar.Id -Force -ErrorAction SilentlyContinue
    }
}
