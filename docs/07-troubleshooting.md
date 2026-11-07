# 7. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `adb connect` times out | The car is off, asleep, or on another network. Check that both devices are on the **same Wi-Fi**, then run `tools/find-headunit.*` again. The IP changes with each network. |
| `adb devices` shows `offline` | A stale connection from an old IP. Run `adb disconnect`, scan, and connect to the new IP. |
| `adb devices` shows `unauthorized` | Tap **Allow** on the head unit screen and tick "Always allow". |
| Port 5555 refused after a reboot | Switch Wireless debugging on again in Developer options (Topway password `john@tw-desktop`). |
| Media card says "Media access needed" | `adb shell cmd notification allow_listener com.fazrilamin.fazzlauncher/com.fazrilamin.fazzlauncher.MediaListener` |
| Media card shows "Bluetooth" with no song | That's the vendor BT app's empty session; it's handled in current builds. Start playback in Spotify or YouTube. |
| Speed shows `--` or "NO GPS" | No fix yet (indoors or in a garage). Check `adb shell dumpsys location`, and grant location: `pm grant com.fazrilamin.fazzlauncher android.permission.ACCESS_FINE_LOCATION`. |
| Speed looks stepped | GNSS is 1 Hz on these units. Fazz Launcher already extrapolates between fixes; for more smoothing, raise `TAU_S` in `DriveModel`. |
| Prayer panel stuck on "Loading…" | No internet yet, or you're outside Malaysia. See `docs/03-customize.md`. |
| Home button opens the old launcher after a reboot | Some firmware resets the default home. Run `cmd package set-home-activity com.fazrilamin.fazzlauncher/.MainActivity` again, or pick Fazz Launcher under Settings → Apps → Default apps. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | It's signed with a different key, e.g. you built your own after installing the release APK. Run `adb uninstall com.fazrilamin.fazzlauncher` first. |
| `build.sh: javac not found` / `Missing …android.jar` | Install the toolchain (`docs/02-build-install.md`) or set `JAVA_HOME` / `ANDROID_HOME`. |
| `$'\r': command not found` when running build.sh | Windows line endings. The repo's `.gitattributes` keeps `.sh` as LF; re-checkout, or run `dos2unix FazzLauncher/build.sh`. |
| Screenshot PNG won't open (Windows) | You used PowerShell `>`. Run `adb exec-out screencap -p > shot.png` from Git Bash instead. |
| `adb pull /system/...` fails in Git Bash with a `C:/Program Files/Git/...` path | Git Bash rewrote the path. Use `MSYS_NO_PATHCONV=1 adb pull ...`. |
| Stock radio crashes now and then | A vendor bug, not Fazz Launcher. See `docs/06-radio-crash.md`. |
