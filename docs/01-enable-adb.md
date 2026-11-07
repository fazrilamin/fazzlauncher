# 1. Turn on ADB over Wi-Fi (no root, no cable)

ADB lets a PC on the same Wi-Fi install apps, read logs and change settings on the head unit. Many aftermarket head units hide it. This guide covers how it was switched on for the **Topway T9 (Allwinner T527, Android 13)**, plus what to try on other units.

> Use at your own risk, and only on a unit you own. Do this while parked.

## Topway units (tested on T9 / T527)

1. **Connect the head unit to Wi-Fi**, on the same network as your PC. A phone hotspot works fine.
2. **Open Developer options.** On the tested unit it asked for the PIN **`7890`**.
   - Some Topway firmware wants **7890 followed by the current hour** in 24-hour time instead, e.g. `789021` at 21:xx.
3. **Turn on Wireless debugging.** Topway asks for a second password here: **`john@tw-desktop`** (all lowercase).
4. On Topway this switch opens **classic ADB on TCP port 5555**, so no pairing code is needed. You can skip "Pair device with pairing code".
5. **Connect from the PC.** The head unit shows **"Allow debugging?"**: tick **Always allow from this computer** and tap **Allow**.
   ```
   adb connect <HEADUNIT_IP>:5555
   adb devices -l        # should list the unit as "device" (not "unauthorized")
   ```
6. Find `<HEADUNIT_IP>` with `tools/find-headunit.ps1` (Windows) or `tools/find-headunit.sh`. They scan your PC's Wi-Fi network for port 5555. You can also look it up on the unit under Settings → About → Status.

On the tested unit, ADB on 5555 **stayed on after a reboot**. It only needed reconnecting when the Wi-Fi network (and so the IP address) changed.

Codes credit: [StarMusa88/TopWay-Android-Headunit-ADB-Guide](https://github.com/StarMusa88/TopWay-Android-Headunit-ADB-Guide).

### What didn't work on the T9 (save yourself the time)

- **Engineer Mode.** Factory settings → `123456` → Engineer Mode → ADB shell is mentioned in some guides, but it isn't present on T9/T527 firmware.
- **`setprop service.adb.tcp.port 5555`** from a terminal app such as Termux fails without root, and this unit isn't rooted.
- **Pairing-code wireless debugging** isn't needed, because the vendor switch opens port 5555 directly.

## Other head units

- **Android 11 or newer:** look for Wireless debugging in Developer options. If it offers "Pair device with pairing code", use:
  ```
  adb pair <ip>:<pair-port> <6-digit code>
  adb connect <ip>:<port shown on the main Wireless debugging screen>
  ```
- **Factory or engineering menus** often have an ADB or "USB mode: Device" switch. Common codes reported by owners: `8888`, `3368`, `1234`, `126`, `1617`, `123456`, `0000122`. These vary by brand and firmware.
- **USB:** car USB ports are usually host-only. ADB over USB only works if the unit has an OTG/device mode option.
- **If there's no vendor switch and no root,** stop there. Don't try to bypass the unit's security.

## Checking it worked

```
adb shell getprop ro.build.version.release      # Android version
adb shell id                                    # uid=2000(shell) is normal (not root)
```
