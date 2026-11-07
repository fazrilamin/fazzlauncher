#!/usr/bin/env bash
# Builds build/FazzLauncher.apk with the plain Android SDK tools (no Gradle, no Android Studio).
#
# Needs JDK 17 (JAVA_HOME, or javac on PATH) and an Android SDK (ANDROID_HOME or ANDROID_SDK_ROOT)
# with "platforms;android-34" and "build-tools;34.0.0". See docs/02-build-install.md.
#
# Tested on Windows (Git Bash). The macOS/Linux branch uses the same steps but is untested.
set -euo pipefail
cd "$(dirname "$0")"

die() { echo "build.sh: $*" >&2; exit 1; }

case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) WIN=1; EXE=.exe; BAT=.bat ;;
    *) WIN=0; EXE=; BAT= ;;
esac
unixpath() { if [ "$WIN" = 1 ]; then cygpath -u "$1"; else printf '%s' "$1"; fi; }
LAD="$(unixpath "${LOCALAPPDATA:-/nonexistent}")"

# ---- JDK 17
if [ -z "${JAVA_HOME:-}" ]; then
    for c in "$LAD"/Java/jdk-17* "/c/Program Files/Eclipse Adoptium"/jdk-17* \
             /usr/lib/jvm/*17* /Library/Java/JavaVirtualMachines/*17*/Contents/Home; do
        if [ -d "$c" ]; then JAVA_HOME="$c"; break; fi
    done
fi
if [ -n "${JAVA_HOME:-}" ]; then
    JAVA_HOME="$(unixpath "$JAVA_HOME")"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v javac >/dev/null || die "javac not found. Install JDK 17 and set JAVA_HOME."

# ---- Android SDK
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
    for c in "$LAD/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [ -d "$c" ]; then SDK="$c"; break; fi
    done
fi
[ -n "$SDK" ] || die "Android SDK not found. Set ANDROID_HOME (see docs/02-build-install.md)."
SDK="$(unixpath "$SDK")"

JAR="$SDK/platforms/android-34/android.jar"
[ -f "$JAR" ] || die "Missing $JAR. Run: sdkmanager \"platforms;android-34\""
BT="$SDK/build-tools/34.0.0"
if [ ! -d "$BT" ]; then
    BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
    BT="${BT%/}"
fi
[ -n "$BT" ] && [ -d "$BT" ] || die "No build-tools in $SDK. Run: sdkmanager \"build-tools;34.0.0\""

AAPT2="$BT/aapt2$EXE"; AAPT="$BT/aapt$EXE"; ZIPALIGN="$BT/zipalign$EXE"
D8="$BT/d8$BAT"; APKSIGNER="$BT/apksigner$BAT"; STUBS="$BT/core-lambda-stubs.jar"
for t in "$AAPT2" "$AAPT" "$ZIPALIGN" "$D8" "$APKSIGNER" "$STUBS"; do
    [ -e "$t" ] || die "Missing $t (incomplete build-tools install?)"
done

# ---- build
rm -rf build
mkdir -p build/gen build/classes build/dex

"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -I "$JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 28 --target-sdk-version 33 \
    --java build/gen -o build/unsigned.apk build/res.zip

# JDK 17 only accepts -bootclasspath for target 8, so the source must stay Java 8.
# core-lambda-stubs.jar supplies LambdaMetafactory, which android.jar lacks.
javac -source 8 -target 8 -bootclasspath "$JAR" -classpath "$STUBS" -Xlint:-options -encoding UTF-8 \
    -d build/classes $(find src build/gen -name '*.java')
jar cf build/classes.jar -C build/classes .
"$D8" --release --min-api 28 --lib "$JAR" --output build/dex build/classes.jar
(cd build/dex && "$AAPT" add ../unsigned.apk classes.dex >/dev/null)

"$ZIPALIGN" -f -p 4 build/unsigned.apk build/aligned.apk

# ---- sign
# The first build creates keystore/fazzlauncher.jks. Keep it (and back it up): every later build must be
# signed with the same key, or Android refuses to install it over the old version.
KS="${FAZZ_KEYSTORE:-keystore/fazzlauncher.jks}"
KS_PASS="${FAZZ_KS_PASS:-carhome}"   # default password for keys made by this script
if [ ! -f "$KS" ]; then
    mkdir -p "$(dirname "$KS")"
    keytool -genkeypair -keystore "$KS" -alias fazzlauncher -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Fazz Launcher" >/dev/null
    echo "Created a new signing key: $KS (back it up)"
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out build/FazzLauncher.apk build/aligned.apk
echo "Built: build/FazzLauncher.apk ($(du -k build/FazzLauncher.apk | cut -f1) KB)"
