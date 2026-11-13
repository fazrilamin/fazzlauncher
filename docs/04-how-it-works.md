# 4. How Fazz Launcher works

Fazz Launcher is a single-activity Android launcher (package `com.fazrilamin.fazzlauncher`, min SDK 28, target SDK 33), written in plain Java 8 with no libraries.

## Files (`FazzLauncher/src/com/fazrilamin/fazzlauncher/`)

| File | Role |
|---|---|
| `MainActivity.java` | HOME activity. Builds the chosen skin in code, runs the 1 s tick, GPS, network fetches, media sessions, the app drawer, the cluster and skin menus, and the Maps window's lifecycle. |
| `DriveModel.java` | Pure driving state: speed smoothing, gearbox simulation, G-force, eco score, trip, demo drive. No views. One instance, owned by `MainActivity`. |
| `DriveView.java` | Base of the speed displays: frame loop (redraws only while something changes), mode colour fade, gear chip, G-ball, eco bar. |
| `ClusterView.java` | Skin 1: speed ring, segmented RPM ring, gear, strip with G-ball, eco and trip. |
| `DigitalSpeedView.java` | Skin 2: big digital speed, speed bar with the limit, gear/rpm, mode and eco. |
| `TwinDialView.java` | Skin 3: analog speedometer and tachometer with needles, strip with G-ball, eco and trip. |
| `MapHost.java` | Skin 2: opens Google Maps in the firmware's navigation window, moves it out of the way, reads the window's rectangle. |
| `Net.java` | HTTP helper, prayer-time / weather / place-name requests, Hijri month names, the state-from-zone fallback. |
| `Glyph.java` | Media and UI icons drawn in code (play, pause, next, previous, grid, close). |
| `MediaListener.java` | Empty `NotificationListenerService`. Granting it notification access is what lets the app read media sessions. |
| `build.sh` | aapt2 → javac (Java 8) → d8 → zipalign → apksigner. |

## Screen layouts (1280×720)

Skin 1, Classic:

```
┌───────────────┬────────────────────────┬───────────────┐
│ clock, date,  │ drive cluster          │ WAKTU SOLAT   │
│ Hijri date,   │ (speed ring, RPM ring, │ place · zone  │
│ weather       │  gear, G-ball, eco,    │ 6 times, next │
│               │  trip)                 │ + countdown   │
├───────────────┴────┬─────┬─────┬─────┬─┴───┬───────────┤
│ media card         │tile │tile │tile │tile │ All apps  │
└────────────────────┴─────┴─────┴─────┴─────┴───────────┘
```

Skin 2, Map (the map is Google Maps' own window, placed over the frame):

```
┌──────────────────────────────────┬──────────────────────┐
│                                  │ clock · date · °C    │
│   Google Maps window             ├──────────────────────┤
│   (x 63, y 54, 733 × 511 on the  │ digital speed, gear, │
│    reference unit)               │ mode, eco            │
│                                  ├──────────────────────┤
│                                  │ next prayer          │
├────────────────────┬─────┬─────┬─┴───┬─────┬────────────┤
│ media card         │tile │tile │tile │tile │ All apps   │
└────────────────────┴─────┴─────┴─────┴─────┴────────────┘
```

Skin 3, Twin dials:

```
┌───────────────────────────────────────────┬─────────────┐
│  speedometer        tachometer (sim.)     │ clock, date │
│  (needle, km/h)     (needle, gear, rpm)   │ Hijri, °C   │
│ ───────────────────────────────────────── ├─────────────┤
│  G-ball · mode · hint · eco        trip   │ next prayer │
├────────────────────┬─────┬─────┬─────┬────┴┬────────────┤
│ media card         │tile │tile │tile │tile │ All apps   │
└────────────────────┴─────┴─────┴─────┴─────┴────────────┘
```

## Data flow

- **GPS:** `LocationManager` GPS updates (0 ms requested, about 1 Hz delivered) → `MainActivity.onLocationChanged()`.
  - It computes km/h from Doppler speed, or from the distance between fixes when there's no speed.
  - Then the current skin's `DriveView.onFix()` → `DriveModel.onFix()`.
- **The tick, once a second:**
  - GPS timeout (no fix for 5 s means no GPS);
  - prayer highlight and countdown;
  - every 5th tick: weather, prayer and place fetches, and media-session refresh;
  - every 10th tick: save the trip.
- **Cache:** everything fetched is kept in `SharedPreferences("fazzlauncher")`, so the screen fills instantly and works offline.

| Data | Keys | Refreshed when |
|---|---|---|
| Weather | `w_temp`, `w_code`, `w_at` | after 20 min |
| Prayer month | `p_json`, `p_ym`, `p_zone`, `p_ver` | the month changes, or you're more than 15 km from the last fetch |
| Place name | `loc_name`, `loc_*` | you're more than 2 km away and it's more than 5 min old |
| Trip | `trip_m`, `trip_ms`, `trip_max`, `trip_last` | reset after 30 min off |
| Settings | `limit_kmh`, `hide_town`, `skin` | set by the owner |

## Drive cluster

- **Speed:** extrapolated between 1 Hz fixes from a smoothed acceleration, low-passed (τ = 0.55 s), with hysteresis on the readout.
- **Gear and RPM (simulated):** pick a gear from speed with up/down hysteresis, delayed under acceleration. Then rpm = wheel rpm × ratio × final drive, plus torque-converter flare at low speed, filtered over τ = 0.25 s. It shows N when stopped.
- **G-force (real GPS):**
  - longitudinal = Δspeed / Δt;
  - lateral = v × Δbearing / Δt, only above 10 km/h.

  Both are low-passed and clamped to ±0.6 g. Longitudinal G uses the latest fix-to-fix change rather than the averaged acceleration behind the speed extrapolation, and it includes the last second into a stop. The G-ball's outer ring is 0.4 g and the inner ring 0.2 g.
- **Braking feedback:** deceleration of 3 km/h/s (about 0.08 g) shows "Braking", 6 km/h/s (about 0.17 g) shows "Smooth braking" in SPORT orange, and 10 km/h/s (about 0.28 g) shows "Hard braking". Each message stays up for at least 2 s. The thresholds are `BRAKE_SOFT`, `BRAKE_FIRM` and `BRAKE_HARD` in `DriveModel`.
- **Drive mode:** a 0–100 eco score from penalties for hard acceleration, hard braking, rpm above 2,500 and speed outside 40–100 km/h, smoothed over about 3 s.

  | Mode | When | Colour |
  |---|---|---|
  | ECO | score ≥ 70 | green |
  | SPORT | hard acceleration or braking, rpm above 4,000, or a low score | orange |
  | LIMIT | over the set limit | amber; red pulse 10 km/h above it |
  | NORMAL | otherwise | cyan |

- **Trip:** distance only adds up between fixes with accuracy under 30 m, speed over 3 km/h and no jumps. A trip auto-resets after 30 minutes off.
- **Demo:** a 60 s scripted drive (`DriveModel.startDemo`). It saves the real trip first and restores it afterwards.

## Skin 2: the Google Maps window

Topway's firmware has its own "navigation window" for its stock launcher. It's built into the system (`ActivityTaskManagerService`), not into the stock launcher, and any app can use it:

- The head unit has a configured navigation app (its "PiP component", Google Maps on the reference unit).
- Starting that app with the intent extra `NAVI=OPEN` makes the system open it as a **borderless freeform window**. The window's rectangle comes from the system properties `sys.tw.forcepip.x`, `.y`, `.w`, `.h` (63, 54, 733, 511 on the reference unit).
- Starting it normally (without the extra) while the window is showing turns it fullscreen. That's what the Maps tile does.
- Pressing HOME turns the window fullscreen and puts it behind the home screen.

How Fazz Launcher uses it (`MapHost` and `MainActivity`):

- **Showing:** `onResume()` in skin 2 starts Maps with `NAVI=OPEN` after a short delay. So the map is back in its window after HOME, after closing another app, and after boot.
- **Layout:** `placeMapSkin()` reads the rectangle once (`getprop`) and places the frame, the right column and the bottom row around it.
- **Getting it out of the way:** freeform windows float above the launcher, so they would cover the app drawer and dialogs. `suppressMap()` starts Maps normally (it goes fullscreen) and then brings the launcher back in front, so Maps ends up behind it. The map shows briefly during this. `unsuppressMap()` puts it back when the drawer or dialog closes. Switching to another skin does the same once.
- **Other firmware:** `NAVI=OPEN` is ignored, Maps opens fullscreen, and the panel shows a "tap to open Maps" placeholder. `MapHost.available()` also needs the `freeform_window_management` feature and Google Maps installed.

Nothing new leaves the unit: the map is the Google Maps app itself, with its own account and settings.

## Media card

- `MediaSessionManager.getActiveSessions()` needs notification-listener access.
- **Which session it shows:** one that's playing first; otherwise the current one; otherwise any session with a track title. Otherwise it shows "Nothing playing" and the play button opens Spotify.
- **Topway quirk:** Topway's BT app (`com.tw.bt`) keeps an active session with no metadata, which is why "has a title" matters.

## Network

| Purpose | Service | Sent |
|---|---|---|
| Weather | Open-Meteo `api.open-meteo.com` | lat/lon (3 decimals) |
| Prayer times | `api.waktusolat.app/v2/solat/gps/{lat}/{lon}` | lat/lon (4 decimals) |
| Place name | OpenStreetMap Nominatim reverse | lat/lon (5 decimals); requires the app's User-Agent; max 1 request/s by policy |

There's no analytics, no account, and nothing else leaves the unit.
