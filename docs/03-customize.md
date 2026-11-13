# 3. Customise Fazz Launcher for your unit and car

All settings are constants in `FazzLauncher/src/com/fazrilamin/fazzlauncher/`. After changing any of them, rebuild with `bash FazzLauncher/build.sh` and reinstall with `adb install -r`.

## Skins

Fazz Launcher has three home-screen layouts. The owner picks one on the unit (**All apps → Skins**, a long-press on the clock, or the cluster menu); it's saved as `skin` in prefs.

| # | Skin | Built by | Speed display |
|---|---|---|---|
| 1 | Classic | `buildHome()`: clock panel, cluster, prayer list | `ClusterView` |
| 2 | Map | `buildHomeMap()`: Google Maps window, clock strip, digital speed, next prayer | `DigitalSpeedView` |
| 3 | Twin dials | `buildHome()`: twin dials, side column with clock and next prayer | `TwinDialView` |

- **Default skin:** `prefs.getInt("skin", SKIN_CLASSIC)` in `onCreate()`.
- **Adding a skin:**
  1. Add a constant (`SKIN_*`) and a label in `SKIN_LABELS`, and raise the upper bound in `applySkin()`.
  2. Build its layout in `buildHome()`. Reuse `buildBottomRow()`, `buildNextPrayerCard()` or `buildPrayerPanel()`, and pass the speed display to `buildDrivePanel()`.
  3. For a new speed display, extend `DriveView` and implement `draw()`. Read everything from the `DriveModel` you're given; the base class handles the frame loop and colours.
  4. Set the view fields you use (`weatherTemp`, `countdown`, `placeView`, …). Leave the rest `null`; every update method skips null views.
- **Skin 2 on other units:** the map window is a Topway firmware feature (see [04 – How it works](04-how-it-works.md#skin-2-the-google-maps-window)). Without it, the panel shows a "tap to open Maps" placeholder. Its layout follows the firmware's window rectangle, so it adapts if your firmware uses another position.

## Home-screen shortcuts

`MainActivity.TILES` is a list of `{label, package, explicit activity or null}`:

```java
private static final String[][] TILES = {
    {"Spotify", "com.spotify.music", null},
    {"YouTube", "com.google.android.youtube", null},
    {"Maps", "com.google.android.apps.maps", null},
    {"Radio", "com.tw.radio", "com.tw.radio.RadioActivity"},
};
```

- **Four tiles** fit beside the media card and All apps.
- **Finding a vendor app's activity:**
  `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER <pkg>`
- **When it can't see an app:** Android 11+ hides other apps unless they're declared. All launchable apps are already covered by the `<queries>` intent in `AndroidManifest.xml`. If you look up a package that has no launcher activity, add a `<package android:name="..."/>` line there.
- **Play button:** `DEFAULT_MEDIA_APP` is the app the play button opens when nothing is playing. The default is Spotify.

## Stock launcher

`MainActivity.STOCK_HOME` is the component the drawer's **Stock Home** entry opens. Set it to your unit's original home component; see `docs/02-build-install.md` for how to find it. Also update the `<package>` entry in `<queries>`.

## RPM and gear simulation

Most head units get **no engine data** from the car (no CAN/OBD), so RPM and gear are **estimated** from GPS speed and acceleration through a gearbox model. The constants are at the top of `DriveModel.java`:

| Constant | Meaning | Example (1.3 L sedan, 4-speed auto) |
|---|---|---|
| `RATIO` | gear ratios, 1st → top | `{2.919, 1.551, 1.000, 0.713}` |
| `FINAL_DRIVE` | final drive ratio | `4.375` |
| `TYRE_M` | tyre circumference, in metres | `1.83` (185/55 R15) |
| `IDLE` | idle rpm, also shown when stopped | `850` |
| `REDLINE` | highest rpm the simulation will show | `6250` |
| `RPM_MAX` | top of the tachometer dial | `8000` |
| `UPSHIFT` | speeds (km/h) for 1→2, 2→3, 3→4 at light throttle | `{14, 28, 45}` |
| `DOWNSHIFT` | speeds for 2→1, 3→2, 4→3 | `{9, 20, 37}` |

Notes:
- **Kickdown feel:** `stretch` in `frame()` holds gears longer under brisk acceleration, up to 1.3×.
- **Other gearboxes:** for 5 or 6 gears, add entries to all three arrays and change the `gear < 4` check in `frame()` to the number of gears. For a CVT, a simple approach is to hold the rpm in a 1,500–2,500 band that rises with acceleration.
- **Tyre circumference:** π × (rim × 25.4 + 2 × width × aspect/100) mm. For example, 185/55 R15 = π × (381 + 203.5) ≈ 1,836 mm.
- **Checking the numbers:** rpm = (km/h ÷ 3.6 ÷ `TYRE_M` × 60) × gear ratio × `FINAL_DRIVE`. Compare with your real tachometer at 50 and 100 km/h.
- **Gear readout:** **N** when stopped, otherwise **D1…D4**.

## Dial scales and speed limit

- **Speedometer top:** `DriveView.VMAX`, default 220. Match it to your car's speedometer. All three skins use it.
- **Red zone:** `DriveView.RED_FROM_RPM`, default 6000.
- **Tachometer top:** `DriveModel.RPM_MAX`, default 8000.
- **Speed-limit warning:** the default comes from `limit_kmh` in `DriveModel.load()` (110). The owner can change it on the unit: long-press the cluster → Speed limit.

## Prayer times (Malaysia only)

The prayer panel uses JAKIM times from `api.waktusolat.app`, by GPS zone (e.g. WLY01, SGR01). Outside Malaysia that API has no data, and the panel stays on "Loading prayer times…".

Options for an agent to implement:
- **Hide the panel:** in `buildHome()`, drop `buildPrayerPanel()` and give the clock and cluster panels its width.
- **Use a worldwide source:** swap `Net.prayerUrl()` and the parsing in `renderPrayer()` for another API, e.g. a calculation-method API that takes lat/lon. Keep the same prefs cache so it works offline.

The location line uses OpenStreetMap Nominatim and works worldwide. The state fallback (`Net.stateOfZone`) is Malaysia-specific.

## Colours

- **Panels and text:** `MainActivity` (`BG`, `PANEL`, `STROKE`, `TEXT`, `SUB`, `ACCENT`).
- **Drive-mode colours:** `DriveView` (`C_ECO`, `C_NORMAL`, `C_SPORT`, `C_AMBER`, `C_RED`).

## Other screen sizes

The layout was tuned for **1280×720 at density 160**. Check yours with `adb shell wm size` and `adb shell wm density`.
- `buildHome()` sets the panel proportions: the top row is weighted 0.9 / 1.3 / 0.9 (Classic) or 2.3 / 0.8 (Twin dials), and the bottom row is 176 dp tall.
- Skin 2 places its panels around the map window's rectangle in `placeMapSkin()`, and its bottom row is compact.
- Text sizes are in sp. The cluster scales itself to its panel size.
- On higher densities or smaller screens, reduce the clock size (92 sp) and the bottom-row height first. Check with screenshots.

## Privacy mode for screenshots

```
adb shell am start -n com.fazrilamin.fazzlauncher/.MainActivity --ez hide_town true     # location line shows only the state
adb shell am start -n com.fazrilamin.fazzlauncher/.MainActivity --ez hide_town false    # back to normal
adb shell am start -n com.fazrilamin.fazzlauncher/.MainActivity --ei skin 2             # switch skin (1, 2 or 3)
```

Skin 2 shows your real map, so blur the map area before you share a screenshot of it.
