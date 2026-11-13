package com.fazrilamin.fazzlauncher;

import android.content.SharedPreferences;
import android.location.Location;

/**
 * Driving state behind the cluster. No views here; {@link ClusterView} calls {@link #frame(long)}
 * once per drawn frame and reads the public fields.
 *
 * Speed: the GNSS gives 1 fix/s, so between fixes speed is extrapolated from the measured
 * acceleration and low-pass filtered (same constants as the old SpeedView).
 *
 * RPM and gear are SIMULATED (the head unit has no CAN/OBD data): a model of a typical
 * 4-speed automatic (1.3 L compact sedan) driven by GPS speed and acceleration.
 * Edit the gearbox constants below to match your own car (see docs/03-customize.md).
 * G-force, trip and eco score come from real GPS data.
 */
final class DriveModel {
    // ---- speed smoothing (unchanged from the old SpeedView)
    private static final float TAU_S = 0.55f;
    private static final float ACCEL_WEIGHT = 0.5f;
    private static final float MAX_PREDICT_S = 1.2f;
    private static final float MAX_ACCEL = 30f; // km/h per s

    // ---- braking feedback (km/h per s; 1 g = 35.3 km/h per s)
    private static final float BRAKE_SOFT = 3f, BRAKE_FIRM = 6f, BRAKE_HARD = 10f;
    private static final long BRAKE_HOLD_MS = 2000;

    // ---- Gearbox model: example 4-speed automatic (1.3 L compact sedan). Edit per car.
    private static final float[] RATIO = {2.919f, 1.551f, 1.000f, 0.713f}; // gear ratios, 1st..4th
    private static final float FINAL_DRIVE = 4.375f;
    private static final float TYRE_M = 1.83f;   // tyre circumference in metres (185/55 R15)
    static final float IDLE = 850f, REDLINE = 6250f, RPM_MAX = 8000f; // RPM_MAX = top of the car's tachometer
    // Shift points: this example car is in D4 by about 50 km/h at normal throttle.
    private static final float[] UPSHIFT = {14f, 28f, 45f};   // 1->2, 2->3, 3->4 (km/h, light throttle)
    private static final float[] DOWNSHIFT = {9f, 20f, 37f};  // 2->1, 3->2, 4->3

    static final int ECO = 0, NORMAL = 1, SPORT = 2, OVER = 3;

    // ---- outputs
    float speed;              // km/h, smoothed
    float rpm = IDLE;
    int gear = 1;             // 1..4
    boolean neutral = true;   // stopped: gear chip shows "N"
    float gLong, gLat;        // g; +long = accelerating, +lat = turning right
    float eco = 100f;         // 0..100
    int mode = NORMAL;
    String hint = "Ready";
    int limit = 110;          // km/h, 0 = off
    boolean demo;

    // ---- trip (real GPS distance)
    double tripM;
    long tripMovingMs;
    float tripMax;
    private long tripLastWall;
    private Location lastTripLoc;

    // ---- internal
    private float lastKmh, accel, accelFast, accelNow, latRaw;
    private int brakeLevel;    // 0 none, 1 braking, 2 firm, 3 hard
    private long brakeUntil;   // hold the braking hint so a 1 s event doesn't just flash
    private long lastFixMs, lastFrameMs;
    private boolean noFix = true;
    private float lastBearing = Float.NaN;
    private long demoStart;
    private double savedTripM;
    private long savedTripMs;
    private float savedTripMax;

    // ------------------------------------------------------------------ inputs

    /** New GPS fix. kmh already computed by MainActivity (Doppler speed or distance fallback). */
    void onFix(float kmh, Location l, long now) {
        if (demo) return;
        if (kmh < 2f) kmh = 0f; // ignore GPS jitter while parked
        float dt = (now - lastFixMs) / 1000f;
        boolean regular = lastFixMs != 0 && dt > 0.3f && dt < 3f;
        float sample = regular ? clamp((kmh - lastKmh) / dt, -MAX_ACCEL, MAX_ACCEL) : 0f;
        if (regular && kmh > 0f && lastKmh > 0f) {
            accel = accel + (sample - accel) * ACCEL_WEIGHT; // average out single noisy fixes
        } else {
            accel = 0f;
        }
        // G-force and braking feedback use the unaveraged sample, including the last second
        // into a stop (kmh == 0), which is usually the hardest part of the braking.
        accelFast = sample;
        // Lateral acceleration = v * yaw rate, from the change in GPS bearing.
        if (l != null && l.hasBearing() && kmh > 10f) {
            float b = l.getBearing();
            if (!Float.isNaN(lastBearing) && regular) {
                float db = b - lastBearing;
                while (db > 180f) db -= 360f;
                while (db < -180f) db += 360f;
                latRaw = (float) (kmh / 3.6 * Math.toRadians(db) / dt);
            }
            lastBearing = b;
        } else {
            lastBearing = Float.NaN;
            latRaw = 0f;
        }
        lastKmh = kmh;
        lastFixMs = now;
        trip(kmh, l);
    }

    void setNoFix(boolean v) {
        if (demo || v == noFix) return;
        noFix = v;
        if (v) {
            lastKmh = 0f;
            accel = 0f;
            accelFast = 0f;
            latRaw = 0f;
            lastBearing = Float.NaN;
        }
    }

    boolean hasFix() {
        return !noFix;
    }

    // ------------------------------------------------------------------ per frame

    /** Advance to {@code now}. Returns true while anything is still changing (keep animating). */
    boolean frame(long now) {
        float dt = lastFrameMs == 0 ? 0.016f : Math.min((now - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = now;
        if (demo) demoStep(now, dt);

        // Speed: extrapolate since the last fix, then low-pass.
        float target = predicted(now);
        speed += (target - speed) * alpha(dt, TAU_S);
        if (Math.abs(target - speed) < 0.05f) speed = target;
        boolean predicting = !noFix && accel != 0f && now - lastFixMs < MAX_PREDICT_S * 1000;

        boolean fresh = !noFix && now - lastFixMs < 2500;
        accelNow += ((fresh ? accelFast : 0f) - accelNow) * alpha(dt, 0.3f);

        // Gear selection with hysteresis; shift points stretch under acceleration (kickdown feel).
        boolean stopped = speed < 1f;
        neutral = stopped;
        float stretch = 1f + clamp(accelNow / 30f, 0f, 0.3f); // brisk pull-away holds gears a little longer
        if (stopped) gear = 1; // pull away in D1
        else if (gear < 4 && speed > UPSHIFT[gear - 1] * stretch) gear++;
        else if (gear > 1 && speed < DOWNSHIFT[gear - 2] * (1f + (stretch - 1f) * 0.5f)) gear--;

        // Engine speed from wheel speed through the gearbox, plus torque-converter flare.
        float wheelRpm = speed / 3.6f / TYRE_M * 60f;
        float rpmTarget = wheelRpm * RATIO[gear - 1] * FINAL_DRIVE;
        float flare = clamp(accelNow / 10f, 0f, 1f) * 500f * clamp(1f - speed / 60f, 0f, 1f);
        rpmTarget = stopped ? IDLE : Math.min(REDLINE, Math.max(IDLE, rpmTarget + flare));
        rpm += (rpmTarget - rpm) * alpha(dt, 0.25f);

        // G-force (real, from GPS).
        float gl = fresh ? accelNow / 3.6f / 9.81f : 0f;
        float gt = fresh && speed > 10f ? latRaw / 9.81f : 0f;
        float oldLong = gLong, oldLat = gLat;
        gLong += (clamp(gl, -0.6f, 0.6f) - gLong) * alpha(dt, 0.25f);
        gLat += (clamp(gt, -0.6f, 0.6f) - gLat) * alpha(dt, 0.35f);

        // Eco score: penalties for hard acceleration/braking, high revs, and speed outside 40-100.
        float pen = 0f;
        if (!stopped) {
            if (accelNow > 3f) pen += (accelNow - 3f) * 9f;
            if (accelNow < -4f) pen += (-accelNow - 4f) * 6f;
            if (rpm > 2500f) pen += (rpm - 2500f) / 25f;
            if (speed < 40f) pen += (40f - speed) * 0.5f;
            if (speed > 100f) pen += (speed - 100f) * 2f;
        }
        float inst = clamp(100f - pen, 0f, 100f);
        float oldEco = eco;
        eco += (inst - eco) * alpha(dt, 3f);

        // Braking tiers (km/h per s): 3 ~ 0.08 g gentle, 6 ~ 0.17 g firm, 10 ~ 0.28 g hard.
        int lvl = accelNow <= -BRAKE_HARD ? 3 : accelNow <= -BRAKE_FIRM ? 2 : accelNow <= -BRAKE_SOFT ? 1 : 0;
        if (lvl > 0 && (lvl >= brakeLevel || now >= brakeUntil)) {
            brakeLevel = lvl;
            brakeUntil = now + BRAKE_HOLD_MS;
        } else if (now >= brakeUntil) {
            brakeLevel = 0;
        }

        if (limit > 0 && speed > limit) {
            mode = OVER;
            hint = speed > limit + 10 ? "Slow down" : "Over limit";
        } else if (brakeLevel == 3) {
            mode = SPORT;
            hint = "Hard braking";
        } else if (brakeLevel == 2) {
            mode = SPORT;
            hint = "Smooth braking";
        } else if (brakeLevel == 1) {
            mode = eco >= 70f ? ECO : NORMAL;
            hint = "Braking";
        } else if (accelNow > 7f || rpm > 4000f) {
            mode = SPORT;
            hint = "Ease off";
        } else if (stopped) {
            mode = NORMAL;
            hint = "Ready";
        } else if (eco >= 70f) {
            mode = ECO;
            hint = "Optimum";
        } else if (eco < 35f) {
            mode = SPORT;
            hint = "Ease off";
        } else {
            mode = NORMAL;
            hint = "Steady";
        }

        return demo || predicting || brakeLevel != 0
            || Math.abs(target - speed) > 0.05f
            || Math.abs(rpmTarget - rpm) > 2f
            || Math.abs(gLong - oldLong) > 0.0005f || Math.abs(gLat - oldLat) > 0.0005f
            || Math.abs(eco - oldEco) > 0.01f;
    }

    /** Called when the view stops animating, so the next frame starts with a fresh delta. */
    void idle() {
        lastFrameMs = 0;
    }

    private float predicted(long now) {
        if (noFix) return 0f;
        float age = Math.min((now - lastFixMs) / 1000f, MAX_PREDICT_S);
        return Math.max(0f, lastKmh + accel * age);
    }

    // ------------------------------------------------------------------ trip

    private void trip(float kmh, Location l) {
        long wall = System.currentTimeMillis();
        if (tripLastWall != 0 && wall - tripLastWall > 30 * 60_000L) resetTrip(); // car was off
        if (l != null && kmh > 3f && l.hasAccuracy() && l.getAccuracy() < 30f) {
            if (lastTripLoc != null) {
                long dtMs = (l.getElapsedRealtimeNanos() - lastTripLoc.getElapsedRealtimeNanos()) / 1_000_000L;
                float dist = lastTripLoc.distanceTo(l);
                if (dtMs > 0 && dtMs < 5000 && dist < dtMs * 0.07f) { // reject jumps (> 250 km/h)
                    tripM += dist;
                    tripMovingMs += dtMs;
                }
            }
            lastTripLoc = l;
        } else {
            lastTripLoc = null;
        }
        if (kmh > tripMax) tripMax = kmh;
        tripLastWall = wall;
    }

    float tripAvgKmh() {
        return tripMovingMs < 30_000 ? 0f : (float) (tripM / 1000.0 / (tripMovingMs / 3_600_000.0));
    }

    void resetTrip() {
        tripM = 0;
        tripMovingMs = 0;
        tripMax = 0;
        lastTripLoc = null;
        tripLastWall = System.currentTimeMillis();
    }

    void load(SharedPreferences p) {
        tripM = p.getFloat("trip_m", 0f);
        tripMovingMs = p.getLong("trip_ms", 0);
        tripMax = p.getFloat("trip_max", 0f);
        tripLastWall = p.getLong("trip_last", 0);
        limit = p.getInt("limit_kmh", 110);
        if (tripLastWall != 0 && System.currentTimeMillis() - tripLastWall > 30 * 60_000L) resetTrip();
    }

    void save(SharedPreferences p) {
        if (demo) return; // never persist demo numbers
        p.edit().putFloat("trip_m", (float) tripM).putLong("trip_ms", tripMovingMs)
            .putFloat("trip_max", tripMax).putLong("trip_last", tripLastWall)
            .putInt("limit_kmh", limit).apply();
    }

    // ------------------------------------------------------------------ demo drive

    // Keyframes: time (s) -> speed (km/h). Pull away, shift up, cruise, overspeed, corner, hard stop.
    private static final float[] DEMO_T = {0, 3, 13, 20, 32, 36, 42, 45, 48, 53, 60};
    private static final float[] DEMO_V = {0, 0, 60, 90, 90, 118, 80, 50, 50, 0, 0};

    void startDemo(long now) {
        if (demo) return;
        savedTripM = tripM;
        savedTripMs = tripMovingMs;
        savedTripMax = tripMax;
        tripM = 0;
        tripMovingMs = 0;
        tripMax = 0;
        demo = true;
        demoStart = now;
        noFix = false;
    }

    private void demoStep(long now, float dt) {
        float t = (now - demoStart) / 1000f;
        if (t >= DEMO_T[DEMO_T.length - 1]) {
            stopDemo();
            return;
        }
        int i = 0;
        while (i < DEMO_T.length - 2 && t > DEMO_T[i + 1]) i++;
        float span = DEMO_T[i + 1] - DEMO_T[i];
        float k = span <= 0 ? 0 : (t - DEMO_T[i]) / span;
        float v = DEMO_V[i] + (DEMO_V[i + 1] - DEMO_V[i]) * k;
        lastKmh = v;
        accel = span <= 0 ? 0 : (DEMO_V[i + 1] - DEMO_V[i]) / span;
        accelFast = accel;
        lastFixMs = now;
        // A right-hand bend at 43-48 s and a lane change at 25-28 s.
        float lat = 0f;
        if (t > 43 && t < 48) lat = 0.35f * (float) Math.sin(Math.PI * (t - 43) / 5);
        else if (t > 25 && t < 28) lat = 0.12f * (float) Math.sin(2 * Math.PI * (t - 25) / 3);
        latRaw = lat * 9.81f;
        if (v > 3f) {
            tripM += v / 3.6f * dt;
            tripMovingMs += (long) (dt * 1000);
        }
        if (v > tripMax) tripMax = v;
    }

    void stopDemo() {
        if (!demo) return;
        demo = false;
        tripM = savedTripM;
        tripMovingMs = savedTripMs;
        tripMax = savedTripMax;
        lastKmh = 0f;
        accel = 0f;
        accelFast = 0f;
        latRaw = 0f;
        brakeLevel = 0;
        noFix = true; // real GPS takes over again on the next fix / tick
    }

    // ------------------------------------------------------------------ util

    private static float alpha(float dt, float tau) {
        return 1f - (float) Math.exp(-dt / tau);
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
