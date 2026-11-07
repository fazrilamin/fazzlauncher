# 5. Remove unused vendor apps (safely)

Everything here can be undone. Apps are only removed **for user 0**, and the APK stays on the system partition.

```
adb shell pm uninstall -k --user 0 <package>          # remove
adb shell cmd package install-existing <package>      # bring it back
```

A factory reset also brings everything back. Always show the owner the list and get a yes first.

## Check what's on your unit first

```
adb shell pm list packages -f -u      # every package with its APK path
adb shell pm list packages -3         # user-installed apps
adb shell pm list packages -d         # already disabled
```

## Removed on the reference unit (Topway T9, T527, Android 13)

| Package | What it is |
|---|---|
| `com.dofun.market` | DoFun app store (the Play Store covers it) |
| `com.tw.bug.report` | Topway bug reporter |
| `com.android.dreams.basic`, `com.android.dreams.phototable` | Screensavers |
| `com.android.htmlviewer`, `com.android.bookmarkprovider` | Old browser leftovers |
| `com.tw.dvr` | Dashcam app (keep it if you have a Topway dashcam) |
| `cn.cardoor.zt360` | 360° camera app (keep it if you have the 360 camera) |
| `com.tw.tv` | TV tuner app; already removed on that firmware |

## Optional (they do something; ask first)

| Package | What you lose |
|---|---|
| `cn.cardoor.d3dview` | 3D car scene on the stock home screen; the heaviest app on the GPU |
| `com.dofun.dofunweather.main` | Weather on the stock home screen |
| `com.tw.video`, `com.tw.music` | USB video and music players |
| `com.google.android.googlequicksearchbox` | Google app / "Hey Google" |
| `com.tw.rightview`, `com.tw.auxin` | Right-side camera, AUX input |
| `com.tw.secondary` | Second-screen launcher (dual-screen setups) |

## Never remove

- **Head unit core:** `com.tw.service`, `com.tw.service.xt`, `com.tw.core`, `com.tw.coreservice`, `com.tw.net`, `com.tw.devicefan`, `com.tw.display.control`, `com.tw.color`, `com.tw.bootanimation`.
- **Car features:**
  - `com.tw.radio`, `com.tw.bt` (Bluetooth and calls), `com.tw.eq`;
  - **`com.tw.reverse`** (reverse camera);
  - **`com.tw.carchoose`** (car model and CAN bus settings);
  - `com.dofun.carsetting`, `com.dofun.carassistant.car`.
- **Stock launcher:** `com.dofun.variety`.
- **Firmware updates:** `com.abupdate.fota_demo_iot`, `com.ab2update.fota_demo_iot`, `com.softwinner.update`.
- **Chip support:** all `com.softwinner.*` and `android.overlay.*`.
- **Google:** Play services, Play Store, keyboard, Google TTS (Maps voice), Chrome, Files.
