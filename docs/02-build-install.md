# 2. Build and install Fazz Launcher

## Option A: ready-made APK (no build)

[`release/FazzLauncher-2.0.apk`](../release/) is set up for the reference unit:
- Topway (DoFun) stock launcher, 1280×720;
- shortcuts for Spotify, YouTube, Google Maps and the Topway radio;
- a typical 4-speed-automatic RPM model;
- Malaysian prayer times.

```
adb install -r release/FazzLauncher-2.0.apk
```

Then continue at **Grant permissions** below. If you later build it yourself, uninstall this APK first, because your build is signed with a different key.

## Option B: build from source

### Toolchain

You need **JDK 17** and the **Android SDK** command-line tools, with:
- `platform-tools` (for adb);
- `platforms;android-34`;
- `build-tools;34.0.0`.

Android Studio and Gradle aren't needed.

- **Windows:** run `powershell -ExecutionPolicy Bypass -File tools\setup-toolchain.ps1`.
  - It downloads Temurin JDK 17 into `%LOCALAPPDATA%\Java` and the SDK into `%LOCALAPPDATA%\Android\Sdk`.
  - It also accepts the SDK licences.
  - `build.sh` finds both locations automatically.
- **macOS / Linux:** install JDK 17 (e.g. `brew install temurin@17` or `apt install openjdk-17-jdk`). Then download "Command line tools only" from developer.android.com/studio, unzip it to `$ANDROID_HOME/cmdline-tools/latest`, and run:
  ```
  yes | sdkmanager --licenses
  sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
  ```
  Set `ANDROID_HOME` and `JAVA_HOME` if they aren't in the default places.

### Build

```
bash FazzLauncher/build.sh          # Windows: run it from Git Bash
```

The output is `FazzLauncher/build/FazzLauncher.apk`, about 50 KB.
- **First build:** creates a signing key in `FazzLauncher/keystore/fazzlauncher.jks`. **Back it up.** Later builds must be signed with the same key to install over the old version.
- **Your own key:** set `FAZZ_KEYSTORE` and `FAZZ_KS_PASS` to use an existing keystore.
- **Tested on:** Windows Git Bash. The macOS/Linux path uses the same steps but hasn't been tested yet.

### Install

```
adb install -r FazzLauncher/build/FazzLauncher.apk
```

## Grant permissions (once per install)

```
adb shell pm grant com.fazrilamin.fazzlauncher android.permission.ACCESS_FINE_LOCATION                  # speed, weather, prayer zone
adb shell cmd notification allow_listener com.fazrilamin.fazzlauncher/com.fazrilamin.fazzlauncher.MediaListener           # media card (what's playing)
```

## Try it, then make it the home screen

```
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
#   ^ write this down: it's your STOCK home component (on Topway: com.dofun.variety/com.dofun.overseasvariety.Launcher)
adb shell am start -n com.fazrilamin.fazzlauncher/.MainActivity                        # open it as a normal app first
adb shell cmd package set-home-activity com.fazrilamin.fazzlauncher/.MainActivity     # make it the default home when happy
```

## Undo

- **On the unit:** Settings → Apps → Default apps → Home app → pick the original. Or use Fazz Launcher's **All apps → Stock Home**.
- **Over ADB:**
  ```
  adb shell cmd package set-home-activity <your stock home component>
  adb uninstall com.fazrilamin.fazzlauncher
  ```

Fazz Launcher never disables or removes the stock launcher.
