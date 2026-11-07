# Fazz Launcher (source)

A custom Android launcher for car head units. It's plain Java 8, uses no libraries, and builds without Gradle.

```
bash build.sh            # -> build/FazzLauncher.apk   (needs JDK 17 + Android SDK platform 34 / build-tools 34.0.0)
```

- **Setup and install:** [`../docs/02-build-install.md`](../docs/02-build-install.md)
- **Customising** (shortcuts, gearbox, dial scales, prayer times): [`../docs/03-customize.md`](../docs/03-customize.md)
- **Architecture:** [`../docs/04-how-it-works.md`](../docs/04-how-it-works.md)

The first build creates `keystore/fazzlauncher.jks`. Keep it, because updates must be signed with the same key. It's git-ignored and must never be committed.
