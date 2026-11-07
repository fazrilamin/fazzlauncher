# Prompt to give your own AI agent

Clone or download this repo, open a terminal in it, start **Claude Code** (`claude`) or **Codex** (`codex`), and paste:

```
I want to set up Fazz Launcher (this repo) on my Android car head unit. Read AGENTS.md first and follow its
hard rules. In particular, check that my car is stationary before doing anything that changes the screen, and only
make changes that can be undone.

My head unit: <brand / model, e.g. Topway T9>, Android <version>. My PC: <Windows / macOS / Linux>.
My car: <make/model optional>, gearbox <4-speed auto / CVT / manual>, speedometer top <e.g. 220 km/h>,
tachometer top <e.g. 8000 rpm>, red zone from <e.g. 6000 rpm>, idle <e.g. 850 rpm>, top gear at about <km/h>.
Shortcuts I want: <e.g. Spotify, YouTube, Google Maps, Radio>.
Region: <e.g. Malaysia, so keep prayer times / outside Malaysia, so hide or replace them>.

Steps:
1. Help me turn on ADB over Wi-Fi (docs/01-enable-adb.md) and connect.
2. Inspect my unit read-only and compare it with the reference unit in AGENTS.md.
3. Adapt the constants (shortcuts, stock home, gearbox, scales) and build it (docs/02-build-install.md).
4. Install it as a normal app first, show me a screenshot, and only make it my home screen after I agree.
5. Tell me exactly how to undo everything.
```

## Tips

- **Want just the APK?** Say so; the agent can install `release/FazzLauncher-1.0.apk` instead of building. It only fits Topway 1280×720 units well.
- **Have a crash or glitch?** Ask the agent to follow `docs/06-radio-crash.md`'s method: crash buffer, exit-info, then bytecode.
- **Sharing screenshots?** Ask the agent to turn on privacy mode first (`--ez hide_town true`).
