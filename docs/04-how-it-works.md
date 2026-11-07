# 4. How Fazz Launcher works

Fazz Launcher is a single-activity Android launcher (package `com.fazrilamin.fazzlauncher`, min SDK 28, target SDK 33), written in plain Java 8 with no libraries.

## Files (`FazzLauncher/src/com/fazrilamin/fazzlauncher/`)

| File | Role |
|---|---|
| `MainActivity.java` | HOME activity. Builds the whole UI in code, runs the 1 s tick, GPS, network fetches, media sessions, the app drawer and the cluster menu. |
| `DriveModel.java` | Pure driving state: speed smoothing, gearbox simulation, G-force, eco score, trip, demo drive. No views. |
| `ClusterView.java` | Draws the drive cluster from `DriveModel`. Only redraws while something is changing. |
| `Net.java` | HTTP helper, prayer-time / weather / place-name requests, Hijri month names, the state-from-zone fallback. |
| `Glyph.java` | Media and UI icons drawn in code (play, pause, next, previous, grid, close). |
| `MediaListener.java` | Empty `NotificationListenerService`. Granting it notification access is what lets the app read media sessions. |
| `build.sh` | aapt2 → javac (Java 8) → d8 → zipalign → apksigner. |

## Screen layout (1280×720)

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

## Data flow

- **GPS:** `LocationManager` GPS updates (0 ms requested, about 1 Hz delivered) → `MainActivity.onLocationChanged()`.
  - It computes km/h from Doppler speed, or from the distance between fixes when there's no speed.
  - Then `ClusterView.onFix()` → `DriveModel.onFix()`.
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
| Settings | `limit_kmh`, `hide_town` | set by the owner |

## Drive cluster

- **Speed:** extrapolated between 1 Hz fixes from a smoothed acceleration, low-passed (τ = 0.55 s), with hysteresis on the readout.
- **Gear and RPM (simulated):** pick a gear from speed with up/down hysteresis, delayed under acceleration. Then rpm = wheel rpm × ratio × final drive, plus torque-converter flare at low speed, filtered over τ = 0.25 s. It shows N when stopped.
- **G-force (real GPS):**
  - longitudinal = Δspeed / Δt;
  - lateral = v × Δbearing / Δt, only above 10 km/h.

  Both are low-passed and clamped to ±0.6 g.
- **Drive mode:** a 0–100 eco score from penalties for hard acceleration, hard braking, rpm above 2,500 and speed outside 40–100 km/h, smoothed over about 3 s.

  | Mode | When | Colour |
  |---|---|---|
  | ECO | score ≥ 70 | green |
  | SPORT | hard acceleration or braking, rpm above 4,000, or a low score | orange |
  | LIMIT | over the set limit | amber; red pulse 10 km/h above it |
  | NORMAL | otherwise | cyan |

- **Trip:** distance only adds up between fixes with accuracy under 30 m, speed over 3 km/h and no jumps. A trip auto-resets after 30 minutes off.
- **Demo:** a 60 s scripted drive (`DriveModel.startDemo`). It saves the real trip first and restores it afterwards.

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
