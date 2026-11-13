# Changelog

Version numbers: the first number goes up for big new features (like skins), the second for fixes and small improvements. `versionCode` in `FazzLauncher/AndroidManifest.xml` goes up by 1 with every release.

## 2.0 (2026-11-13)

### New
- **Skins.** Pick one from **All apps → Skins**, by long-pressing the clock, or from the cluster menu:
  - **1 · Classic:** the 1.0 layout.
  - **2 · Map:** the real Google Maps app in a window on the home screen (no API key), with a clock strip, a digital speedometer and the next prayer. Tap the Maps tile for full screen; press Home to put it back in the window. Uses the Topway firmware's navigation window (see [docs/04-how-it-works.md](docs/04-how-it-works.md#skin-2-the-google-maps-window)).
  - **3 · Twin dials:** an analog speedometer and tachometer with needles, a side column with the clock, and the next prayer.
- Trip, speed limit and demo carry over when you switch skins.
- **Braking feedback:** "Braking" (from about 0.08 g), "Smooth braking" (about 0.17 g) and "Hard braking" (about 0.28 g). Each stays on screen for at least 2 seconds.
- ADB test hook: `--ei skin 1|2|3`.

### Changed
- The G-ball is more sensitive: the outer ring is now 0.4 g (was 0.5 g), and it reacts faster.
- G-force and braking now include the last second before the car stops, which used to be ignored.
- The code now has a shared `DriveView` base class for the speed displays (`ClusterView`, `DigitalSpeedView`, `TwinDialView`), and one `DriveModel` owned by `MainActivity`.

## 1.0 (2026-11-08)

First public release: Classic home screen with a drive cluster (GPS speed, simulated RPM and gear, G-ball, ECO/SPORT feedback, speed-limit warning, trip computer), clock with Hijri date and weather, JAKIM prayer times, media card, shortcuts and app drawer.
