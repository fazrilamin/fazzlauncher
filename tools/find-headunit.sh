#!/usr/bin/env bash
# Finds the head unit on your Wi-Fi: scans a /24 network for TCP port 5555 (classic ADB),
# then runs `adb connect` on whatever answers.
#
#   bash tools/find-headunit.sh               # guess this machine's network
#   bash tools/find-headunit.sh 192.168.1     # or give the first three numbers
#
# Tested on Windows Git Bash. macOS/Linux use the same method but are untested.
set -uo pipefail
PORT="${PORT:-5555}"

subnet="${1:-}"
if [ -z "$subnet" ]; then
    ip=""
    if command -v ip >/dev/null 2>&1; then                    # Linux
        ip="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}')"
    elif command -v ipconfig >/dev/null 2>&1 && [ "$(uname -s)" = Darwin ]; then   # macOS
        ip="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null)"
    fi
    if [ -z "$ip" ] && command -v powershell.exe >/dev/null 2>&1; then              # Windows Git Bash
        ip="$(powershell.exe -NoProfile -Command "(Get-NetIPConfiguration | Where-Object { \$_.IPv4DefaultGateway -and \$_.NetAdapter.Status -eq 'Up' } | Select-Object -First 1).IPv4Address.IPAddress" | tr -d '\r')"
    fi
    [ -n "$ip" ] || { echo "Could not detect this machine's IP. Run: $0 192.168.x" >&2; exit 1; }
    subnet="${ip%.*}"
    echo "This machine: $ip"
fi

echo "Scanning $subnet.1-254 for port $PORT ..."
tmp="$(mktemp)"
for i in $(seq 1 254); do
    ( timeout 1 bash -c "exec 3<>/dev/tcp/$subnet.$i/$PORT" 2>/dev/null && echo "$subnet.$i" >> "$tmp" ) &
done
wait
found="$(sort -t. -k4 -n "$tmp")"; rm -f "$tmp"

if [ -z "$found" ]; then
    echo "Nothing found. Is the head unit on the same Wi-Fi with ADB switched on (docs/01-enable-adb.md)?"
    exit 2
fi
echo "Port $PORT open on: $(echo $found)"
if command -v adb >/dev/null 2>&1; then
    for h in $found; do adb connect "$h:$PORT"; done
    adb devices -l
else
    echo "adb not on PATH; then run: adb connect $(echo "$found" | head -1):$PORT"
fi
