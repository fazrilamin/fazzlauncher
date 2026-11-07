<#
.SYNOPSIS
  Finds the head unit on your Wi-Fi: scans this PC's /24 network for TCP port 5555 (classic ADB),
  then offers to adb-connect to what it finds.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\find-headunit.ps1
  powershell -ExecutionPolicy Bypass -File tools\find-headunit.ps1 -Subnet 192.168.1
#>
param(
    [string]$Subnet,          # e.g. 192.168.1 ; default: the network of this PC's default route
    [int]$Port = 5555,
    [int]$TimeoutMs = 1500
)

if (-not $Subnet) {
    $cfg = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } | Select-Object -First 1
    if (-not $cfg) { Write-Error "No active network with a default gateway. Pass -Subnet 192.168.x"; exit 1 }
    $ip = $cfg.IPv4Address.IPAddress
    $Subnet = $ip.Substring(0, $ip.LastIndexOf('.'))
    Write-Host "This PC: $ip on '$($cfg.NetProfile.Name)'"
}

Write-Host "Scanning $Subnet.1-254 for port $Port ..."
$tasks = @{}
foreach ($i in 1..254) {
    $c = New-Object System.Net.Sockets.TcpClient
    $tasks[$i] = @($c, $c.ConnectAsync("$Subnet.$i", $Port))
}
Start-Sleep -Milliseconds $TimeoutMs
$found = @()
foreach ($k in ($tasks.Keys | Sort-Object)) {
    if ($tasks[$k][1].Status -eq 'RanToCompletion') { $found += "$Subnet.$k" }
    $tasks[$k][0].Dispose()
}

if (-not $found) {
    Write-Host "Nothing found. Is the head unit on the same Wi-Fi with ADB switched on (docs/01-enable-adb.md)?"
    exit 2
}
Write-Host "Port $Port open on: $($found -join ', ')"

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path $adb)) { $adb = Join-Path $env:LOCALAPPDATA 'Android\platform-tools\adb.exe' }
if (Test-Path $adb) {
    foreach ($h in $found) { & $adb connect "${h}:$Port" }
    & $adb devices -l
} else {
    Write-Host "adb not found; run tools\setup-toolchain.ps1, then: adb connect $($found[0]):$Port"
}
