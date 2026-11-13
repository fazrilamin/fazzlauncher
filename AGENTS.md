# AGENTS.md

Instructions for AI coding agents (Claude Code, OpenAI Codex, and similar) helping someone repeat this project on **their own** Android car head unit. Humans can read it too, but it's written as a playbook for an agent with a shell and `adb`.

## What this repo achieves

Everything below was done on a real, **non-rooted** head unit, using only a PC on the same Wi-Fi:

1. **ADB over Wi-Fi** on a Topway head unit, through the vendor's hidden debugging password. No root and no USB cable. See [`docs/01-enable-adb.md`](docs/01-enable-adb.md).
2. **Fazz Launcher**, a custom launcher (`FazzLauncher/`, package `com.fazrilamin.fazzlauncher`). It has:
   - a clock with Hijri date and weather;
   - Malaysian JAKIM prayer times with a location line;
   - three skins: Classic (futuristic drive cluster), Map (the real Google Maps app in a window on the home screen) and Twin dials;
   - a drive cluster: GPS speed, **simulated** RPM and gear, G-ball, ECO/SPORT and braking feedback, speed-limit warning and trip computer;
   - a Spotify/YouTube/BT media card, app shortcuts and an app drawer.

   It's built with plain SDK tools, with no Gradle and no Android Studio.
3. **Safe debloating** of vendor apps, all reversible. See [`docs/05-debloat.md`](docs/05-debloat.md).
4. **Diagnosing a vendor app crash** (the stock FM radio) from logs and bytecode. See [`docs/06-radio-crash.md`](docs/06-radio-crash.md).

Reference unit:
- Topway "T9" platform, Allwinner **T527**, Android **13** (SDK 33), build `TQ2A.230405.003.B2`;
- 1280×720 landscape at density 160 (1 dp = 1 px);
- stock launcher `com.dofun.variety/com.dofun.overseasvariety.Launcher`.

Other units will differ. Inspect before you change anything (Phase 2).

## Hard rules

1. **It's in a car.** Before sending taps, key events, the demo, media keys, reboots, or anything else that changes what's on screen, check that the car is stationary:
   ```
   adb shell "dumpsys location | grep -m1 'last location=Location\[gps'"   # must show vel=0.0
   ```
   If there's no GPS fix, ask the owner whether the car is parked. Read-only commands such as `getprop`, `dumpsys`, `logcat` and screenshots are always fine.
2. **Only make changes that can be undone.**
   - Never disable or uninstall the stock launcher. It's the way back.
   - Remove apps only with `pm uninstall -k --user 0 <pkg>`, which can be undone with `cmd package install-existing <pkg>`. Always ask the owner first, and show them the list.
   - Never touch the vendor core packages listed in [`docs/05-debloat.md`](docs/05-debloat.md).
3. **Record the stock home component before changing home**, so you can always revert:
   ```
   adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```
4. **Don't try to bypass security or rooting.** The ADB method here uses the vendor's own debug switch. If a unit has no such switch, stop and tell the owner.
5. **Keep the signing key** (`FazzLauncher/keystore/fazzlauncher.jks`, created on the first build). Losing it means uninstalling Fazz Launcher before the next install, which wipes its saved data.
6. **Be honest about simulated data.** RPM and gear are modelled from GPS speed; they're not read from the engine. Say so whenever you show or describe them.

## Ask the owner first

- **Tell them the risk first:** everything here was tested on one unit only (the author's Topway T9). In the worst case, changes over ADB can leave a head unit unusable, and the author is not responsible. Get their OK to proceed at their own risk before Phase 5.
- Head unit brand and model, chip, and Android version (Settings → About). A photo of the About screen is fine.
- Does Developer options open? What does it ask for when they switch on **Wireless debugging**?
- Which apps do they want on the home screen? The defaults are Spotify, YouTube, Google Maps and the vendor Radio.
- Their car's gearbox (automatic or CVT, number of gears) and dial scales (top speed on the speedometer, top of the tachometer, where the red zone starts). If they don't know, use the defaults and tell them the RPM is an estimate.
- Their region. **Prayer times only work in Malaysia** (JAKIM zones). Everywhere else, offer to hide that panel or swap in another prayer-time source.
- Do they want Fazz Launcher as the default home, or only as an app to try first? Always install it as an app first.

## Playbook

### Phase 1: connect

1. Follow [`docs/01-enable-adb.md`](docs/01-enable-adb.md) to switch on ADB on the unit.
2. Find the unit's IP address: run `tools/find-headunit.ps1` (Windows) or `tools/find-headunit.sh`. Both scan the PC's /24 network for TCP port 5555.
3. Run `adb connect <ip>:5555`. If it says `unauthorized`, ask the owner to tap **Allow** on the car screen and tick "Always allow".
4. The IP changes whenever the PC or the car joins a different Wi-Fi network. If `adb` says `offline`, run `adb disconnect` and scan again.

### Phase 2: inspect (read-only)

```
adb shell getprop ro.product.model; adb shell getprop ro.build.version.release; adb shell getprop ro.build.display.id
adb shell wm size; adb shell wm density
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell "pm list packages | grep -iE 'radio|music|bt|launcher|variety'"
adb shell "dumpsys media_session | grep -E 'package=|active=|state='"
adb shell "pm list features | grep -c android.hardware.location.gps"
```

Compare the results with the reference unit. Fazz Launcher's layout assumes **1280×720 at density 160**. On other sizes, check it with screenshots and adjust (see [`docs/03-customize.md`](docs/03-customize.md)).

### Phase 3: adapt the source to this unit and car

All of these are constants near the top of their files:

| What | Where |
|---|---|
| Stock launcher component (the drawer's "Stock Home" entry) | `MainActivity.STOCK_HOME` |
| Home-screen shortcuts: label, package, explicit activity | `MainActivity.TILES` |
| App that "play" opens when nothing is playing | `MainActivity.DEFAULT_MEDIA_APP` |
| Package visibility (Android 11+) | `<queries>` in `AndroidManifest.xml`; add any package you look up by name |
| Gearbox model: ratios, final drive, tyre, idle, redline, shift points | `DriveModel` (top) |
| Dial scales | `DriveView.VMAX`, `DriveView.RED_FROM_RPM`, `DriveModel.RPM_MAX` |
| Default skin, skin list | `MainActivity.SKIN_*`, `SKIN_LABELS`, `buildHome()` |
| Google Maps window (skin 2) | `MapHost` (package/activity, fallback rectangle) |
| Default speed-limit warning | `DriveModel.load()`, default 110 |

Find the radio and other vendor apps' launch activities with:
```
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER <pkg>
```

### Phase 4: build

See [`docs/02-build-install.md`](docs/02-build-install.md). In short: install JDK 17 and the Android SDK (`tools/setup-toolchain.ps1` on Windows), then run `bash FazzLauncher/build.sh`. The output is `FazzLauncher/build/FazzLauncher.apk`.

### Phase 5: install, grant, try, then set as home

```
adb install -r FazzLauncher/build/FazzLauncher.apk
adb shell pm grant com.fazrilamin.fazzlauncher android.permission.ACCESS_FINE_LOCATION
adb shell cmd notification allow_listener com.fazrilamin.fazzlauncher/com.fazrilamin.fazzlauncher.MediaListener     # media card
adb shell am start -n com.fazrilamin.fazzlauncher/.MainActivity                                    # try it as an app
adb shell cmd package set-home-activity com.fazrilamin.fazzlauncher/.MainActivity                  # only after the owner agrees
```

### Phase 6: verify

```
adb exec-out screencap -p > shot.png        # look at it; run from bash: PowerShell's > corrupts binary output
adb shell "logcat -d -b crash | grep -A15 com.fazrilamin.fazzlauncher"       # must be empty
```

Test hooks. Only use them when the car is stationary (Hard rule 1):
- `adb shell am start -n com.fazrilamin.fazzlauncher/.MainActivity --ez demo true` runs a 60 s scripted drive. It shifts through the gears, goes ECO → over the limit → corner → hard braking, and never changes the saved trip.
- `--ez hide_town true` shows only the state on the location line, for screenshots you want to share. Use `--ez hide_town false` to undo it.
- `--ei skin 1|2|3` switches the skin (Classic, Map, Twin dials). The activity must already be running: start it once without extras, then send the extra.
- Skin 2 shows the owner's real map around their location. Blur the map area before sharing a screenshot of it.

Skin 2 check (Topway): the Maps tile opens Maps fullscreen, HOME puts it back into the window, opening All apps moves it out of the way. `dumpsys activity activities | grep -m1 'Task{'` shows `mode=freeform` with the window's bounds while it's in the window.

### Phase 7: revert (always tell the owner how)

- On the unit: Settings → Apps → Default apps → Home app → pick the original. Or use Fazz Launcher's All apps → **Stock Home**.
- Over ADB:
  ```
  adb shell cmd package set-home-activity <stock component from Hard rule 3>
  adb uninstall com.fazrilamin.fazzlauncher
  ```

## Code map and invariants (`FazzLauncher/`)

- **No Gradle.** `build.sh` drives `aapt2` → `javac` → `d8` → `zipalign` → `apksigner` directly. Keep it that way, so builds need nothing but JDK 17 and the SDK.
- **Java 8 syntax only.** JDK 17 rejects `-bootclasspath` for targets above 8, and lambdas need `build-tools/core-lambda-stubs.jar` on the classpath. Both are handled in `build.sh`, so don't use `var`, switch expressions, records or text blocks.
- **No XML layouts.** `MainActivity` builds the whole UI in code. The only resource is the vector icon.
- **The loop:** one 1-second `tick` drives the GPS timeout, the prayer countdown, saving the trip and throttled network fetches. The skin's `DriveView` redraws on its own, only while values change.
- **Skins:** `skin` in prefs (1 Classic, 2 Map, 3 Twin dials). `buildHome()` builds the chosen layout; `applySkin()` swaps it in place (the drawer stays). Views a skin doesn't have stay `null`, so every render method null-checks them.
- **Skin 2 / `MapHost`:** a Topway framework feature, not a public API. Starting the unit's configured navigation app with the extra `NAVI=OPEN` opens it as a borderless freeform window at the rectangle in `sys.tw.forcepip.x/y/w/h`; skin 2 lays its panels out around that rectangle. The window floats above the launcher, so the drawer and dialogs call `suppressMap()` first (Maps briefly goes fullscreen, then the launcher comes back in front) and `unsuppressMap()` after. HOME turns the window fullscreen behind the launcher, so `onResume()` puts it back.
- **Offline-first:** everything fetched is cached in `SharedPreferences("fazzlauncher")`:
  - weather `w_*`;
  - prayer month `p_*`;
  - place name `loc_*`;
  - trip `trip_*`, speed limit `limit_kmh`, privacy `hide_town`, skin `skin`.
- **Network:**
  - Open-Meteo for weather;
  - `api.waktusolat.app` for JAKIM prayer times by GPS;
  - OpenStreetMap Nominatim for the place name. Its usage policy requires the `User-Agent` sent by `Net.get()` and allows at most 1 request per second; the caching keeps it far below that.

  The location goes nowhere else.
- **Media:**
  - `MediaListener` is an empty `NotificationListenerService`. It exists only so `MediaSessionManager.getActiveSessions()` is allowed.
  - `pickController()` prefers a session that is playing, then any session with a title.
  - The vendor BT app keeps an empty active session, so it must not win.
- **Drive cluster:**
  - `DriveModel` holds pure state: speed extrapolation between 1 Hz GPS fixes, the gearbox simulation, G-force from Δspeed and Δbearing, the eco score, the trip, and the demo script.
  - `MainActivity` owns the one `DriveModel`; it survives skin changes.
  - `DriveView` is the base of the speed displays: the frame loop, mode colour fade, and shared pieces (gear chip, G-ball, eco bar). `ClusterView` (skin 1), `DigitalSpeedView` (skin 2) and `TwinDialView` (skin 3) only draw.
  - GNSS on these units is 1 Hz, even with a 0 ms request interval. Tune `TAU_S` and the extrapolation, not the request rate.

More detail is in [`docs/04-how-it-works.md`](docs/04-how-it-works.md).

## Diagnostics recipes

| Question | Command |
|---|---|
| What crashed? | `adb shell logcat -d -b crash` |
| Why did an app stop (crash, LMK, force-stop, and by whom)? | `adb shell dumpsys activity exit-info <pkg>` |
| Which app was in front when? | `adb shell dumpsys usagestats` (keeps only a short history) |
| Who is using GPS, and how fast? | `adb shell dumpsys location` |
| What does a system app do internally? | `MSYS_NO_PATHCONV=1 adb pull <apk path from 'pm path pkg'>`, then `build-tools/dexdump -d classes*.dex` |
| Capture a crash that happens rarely | `adb logcat -G 16M`, then record `adb logcat -b main,system,crash,events -v time > file` with a reconnect loop |

## Platform pitfalls

- **Windows Git Bash** rewrites device paths such as `/system/...`. Use `MSYS_NO_PATHCONV=1 adb pull ...`.
- **PowerShell** `>` re-encodes binary output. Take screenshots from bash with `adb exec-out screencap -p > file.png`.
- **Classic ADB** on Topway goes through the Wireless debugging switch, which opens port 5555 without pairing. On the reference unit it stayed on after a reboot. If it doesn't on yours, switch it on again.
- **Topway's system service** (`com.tw.service`) force-stops source apps when the audio source changes. That's normal, not a crash.
