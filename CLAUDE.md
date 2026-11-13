# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The full playbook, safety rules and code map are in AGENTS.md (shared with Codex and other agents):

@AGENTS.md

## Claude Code specifics

- On Windows, Claude Code has both PowerShell and Bash tools. Use **Bash** for anything binary: `adb exec-out screencap -p > shot.png`, `adb pull`. PowerShell redirection corrupts PNGs.
- In Bash, prefix `adb pull`/`push` of device paths with `MSYS_NO_PATHCONV=1`.
- To see the head unit's screen, take a screenshot and open the PNG with the Read tool.
- For long log captures (waiting for a rare crash), run the logcat loop as a background command, and stop it when the owner is done.
- `adb` is often not on PATH. `tools/setup-toolchain.ps1` installs platform-tools to `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- There are no tests or linter. The loop is: `bash FazzLauncher/build.sh` → `adb install -r FazzLauncher/build/FazzLauncher.apk` → screenshot → `logcat -d -b crash`.
- The ADB test extras (`--ez demo`, `--ei skin`, `--ez hide_town`) are read only in `onNewIntent`. `adb install -r` kills the app, so start it once (HOME key) before sending them, or they're silently ignored.
- Skin 2 testing (Topway): while the Maps freeform window is up, the HOME key won't switch to a different home app. If another launcher's leftover task shows up after an install, start Maps normally (`am start -n com.google.android.apps.maps/com.google.android.maps.MapsActivity`, which makes it fullscreen), then press HOME. Check the window with `dumpsys activity activities | grep -m1 'Task{'` (`mode=freeform`).
- When a change affects users or the code map, update `CHANGELOG.md`, the relevant `docs/` page and the code map in `AGENTS.md`. A release also bumps `versionCode`/`versionName` in `FazzLauncher/AndroidManifest.xml` and replaces the APK in `release/` (and its SHA-256 in `README.md`).
