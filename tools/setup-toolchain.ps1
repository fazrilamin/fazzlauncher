<#
.SYNOPSIS
  Windows: installs everything needed to build Fazz Launcher and talk to the head unit, for this user only
  (no admin rights): Temurin JDK 17 and the Android SDK (cmdline-tools, platform-tools, platforms;android-34,
  build-tools;34.0.0). build.sh finds both locations automatically.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\setup-toolchain.ps1
#>
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$javaRoot = Join-Path $env:LOCALAPPDATA 'Java'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
New-Item -ItemType Directory -Force $javaRoot, (Join-Path $sdk 'cmdline-tools') | Out-Null

# --- JDK 17 (Eclipse Temurin)
$jdk = Get-ChildItem $javaRoot -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdk) {
    Write-Host 'Downloading Temurin JDK 17 ...'
    $zip = Join-Path $javaRoot 'jdk17.zip'
    Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip -UseBasicParsing
    Expand-Archive $zip $javaRoot -Force
    Remove-Item $zip
    $jdk = Get-ChildItem $javaRoot -Directory -Filter 'jdk-17*' | Select-Object -First 1
}
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($jdk.FullName)\bin;$env:Path"
Write-Host "JDK: $($jdk.FullName)"

# --- Android command-line tools
$latest = Join-Path $sdk 'cmdline-tools\latest'
if (-not (Test-Path (Join-Path $latest 'bin\sdkmanager.bat'))) {
    Write-Host 'Downloading Android command-line tools ...'
    $zip = Join-Path $sdk 'clt.zip'
    Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $zip -UseBasicParsing
    $tmp = Join-Path $sdk 'cmdline-tools\tmp'
    Expand-Archive $zip $tmp -Force
    Remove-Item $zip
    if (Test-Path $latest) { Remove-Item -Recurse -Force $latest }
    Move-Item (Join-Path $tmp 'cmdline-tools') $latest
    Remove-Item -Recurse -Force $tmp
}

# --- SDK packages (licences accepted non-interactively)
$sm = Join-Path $latest 'bin\sdkmanager.bat'
Write-Host 'Accepting SDK licences ...'
("y`n" * 30) | & $sm "--sdk_root=$sdk" --licenses | Out-Null
Write-Host 'Installing platform-tools, platforms;android-34, build-tools;34.0.0 ...'
& $sm "--sdk_root=$sdk" 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0' | Select-Object -Last 2

$adb = Join-Path $sdk 'platform-tools\adb.exe'
& $adb version | Select-Object -First 1
Write-Host ''
Write-Host "Done. adb: $adb"
Write-Host 'Build from Git Bash:  bash FazzLauncher/build.sh'
Write-Host "Tip: add $sdk\platform-tools to your PATH to type plain 'adb'."
