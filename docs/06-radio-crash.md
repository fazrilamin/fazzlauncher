# 6. Case study: why the stock FM radio "randomly" crashes

The owner reported that the Topway radio crashed at random, often after using YouTube or Spotify, or the map on the home screen. Clearing its data didn't help. This is how it was traced, without root, and what was found. Agents can reuse the method on any vendor app.

## Unit and app

- Topway T9 / Allwinner T527, firmware `TQ2A.230405.003.B2`.
- `com.tw.radio`, versionName `T527_SY.20250830`, a system app in `/system_tw/priv-app/`.

## 1. Read the crash buffer

```
adb shell logcat -d -b crash
```

Every radio crash was the same:

```
FATAL EXCEPTION: Thread-3
Process: com.tw.radio
java.lang.NullPointerException: Attempt to invoke virtual method
  'java.io.File android.content.Context.getDatabasePath(java.lang.String)' on a null object reference
    at android.database.sqlite.SQLiteOpenHelper.getDatabaseLocked(SQLiteOpenHelper.java:370)
    at android.database.sqlite.SQLiteOpenHelper.getReadableDatabase(SQLiteOpenHelper.java:340)
    at C0.a$c$a.run(RadioModel.java:77)
```

## 2. Find what was happening at the time

```
adb shell dumpsys activity exit-info com.tw.radio
```

- **Crashes:** `reason=4 (APP CRASH)` with `importance=125`, a foreground service using about 10 MB. That means the radio process was running **only its background service**, without its screen.
- **Other stops:** many `FORCE STOP ... from pid <com.tw.service>`. Those are Topway's system service switching the audio source, which is normal.

## 3. Catch it live

After a reboot, the log showed:

```
Start proc com.tw.radio for service {com.tw.radio/.RadioService}
RadioModel: Model create
RadioModel: onRdsDateChange run: 0404:0   9760
am_crash ... NullPointerException ...
Scheduling restart of crashed service com.tw.radio/.RadioService in 1000ms
```

## 4. Read the bytecode (no root needed)

```
adb shell pm path com.tw.radio
MSYS_NO_PATHCONV=1 adb pull /system_tw/priv-app/com.tw.radio_78cc/com.tw.radio_78cc.apk radio.apk
unzip radio.apk 'classes*.dex'
<build-tools>/dexdump -d classes*.dex > dump.txt
```

- `C0.a$c$a.run()` is a thread started when RDS data changes, i.e. the station name or PI. It calls `F0.b.e(context).getReadableDatabase()` to look up the station logo in a `radiologo` table.
- The `context` is the field `C0.a.a`, set only by `C0.a.A(Context)`.
- `C0.a.A()` is only called from `D0.a.m1()`, which belongs to the **presenter that `RadioActivity` creates**.

## Root cause

When the radio runs in the background without its screen ever being opened in that process, its model's Context is still null. That happens:
- at boot, when the unit resumes the last source;
- after Android closes the process to free memory, e.g. while YouTube or Maps are running.

The first RDS update then starts the logo lookup, which crashes. Android restarts the service about a second later.

## Workarounds

- Open the radio from its icon, which creates `RadioActivity`, rather than relying on it resuming in the background.
- Try **NavRadio+** (`com.navimods.radio`) from the Play Store. Its listing supports "TopWay firmware with Allwinner", and it has its own radio code that skips this path. It isn't tested on T527.
- **The real fix** is a firmware update. Report it to the seller or Topway with the stack trace and the root cause above.
- Clearing the radio's data doesn't help, because nothing is corrupted.
