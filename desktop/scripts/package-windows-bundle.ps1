$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $true

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$DesktopDir = Join-Path $RootDir "desktop"
$FlutterDir = Join-Path $DesktopDir "flutter_app"
$OutDir = Join-Path $DesktopDir "out\windows"
$StageDir = Join-Path $OutDir "ABK"
$FlutterReleaseDir = Join-Path $FlutterDir "build\windows\x64\runner\Release"
$ZipPath = Join-Path $OutDir "ABK-windows-x64.zip"
$PythonDir = Join-Path $StageDir "runtime\python"

function Copy-ResourceTree {
    param(
        [string]$Source,
        [string]$Destination
    )
    New-Item -ItemType Directory -Force -Path (Split-Path $Destination -Parent) | Out-Null
    Copy-Item -Recurse -Force $Source $Destination
}

cargo build `
  --manifest-path (Join-Path $DesktopDir "Cargo.toml") `
  --release `
  --bin abk_launcher `
  --bin abk_sidecar

Push-Location $FlutterDir
try {
    flutter build windows --release
} finally {
    Pop-Location
}

if (Test-Path $StageDir) {
    Remove-Item -Recurse -Force $StageDir
}
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $StageDir "flutter") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $StageDir "resources\abk\app\signing") | Out-Null
New-Item -ItemType Directory -Force -Path $PythonDir | Out-Null

Copy-Item -Recurse -Force (Join-Path $FlutterReleaseDir "*") (Join-Path $StageDir "flutter")
Copy-Item -Force (Join-Path $DesktopDir "target\release\abk_launcher.exe") (Join-Path $StageDir "ABK.exe")
Copy-Item -Force (Join-Path $DesktopDir "target\release\abk_sidecar.exe") (Join-Path $StageDir "abk_sidecar.exe")
if (-not $env:pythonLocation) {
    throw "pythonLocation is not set. Run actions/setup-python before packaging."
}
Copy-Item -Recurse -Force (Join-Path $env:pythonLocation "*") $PythonDir

Copy-ResourceTree (Join-Path $RootDir "cli") (Join-Path $StageDir "resources\abk\cli")
Copy-ResourceTree (Join-Path $RootDir "zram") (Join-Path $StageDir "resources\abk\zram")
Copy-ResourceTree (Join-Path $RootDir "ddk") (Join-Path $StageDir "resources\abk\ddk")
Copy-ResourceTree (Join-Path $RootDir "config") (Join-Path $StageDir "resources\abk\config")
Copy-Item -Force (Join-Path $RootDir "hmbird_patch.c") (Join-Path $StageDir "resources\abk\hmbird_patch.c")
Copy-Item -Force (Join-Path $RootDir "app\signing\abk-manager-cert.env") (Join-Path $StageDir "resources\abk\app\signing\abk-manager-cert.env")

if (Test-Path $ZipPath) {
    Remove-Item -Force $ZipPath
}
Compress-Archive -Path (Join-Path $StageDir "*") -DestinationPath $ZipPath
Write-Host "Windows bundle created: $ZipPath"
