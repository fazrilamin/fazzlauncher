package com.fazrilamin.fazzlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;

/**
 * Base of every speed display (one per skin): {@link ClusterView} (Classic), {@link DigitalSpeedView}
 * (Map) and {@link TwinDialView} (Twin dials). All of them read the same {@link DriveModel}, which
 * MainActivity owns, so the trip, limit and demo carry over when the skin changes.
 *
 * This class runs the frame loop (model.frame → draw → redraw only while something changes),
 * fades the colour with the drive mode, and has the drawing helpers the skins share.
 */
abstract class DriveView extends View {
    static final int C_ECO = 0xFF4ADE80, C_NORMAL = 0xFF22D3EE, C_SPORT = 0xFFFB923C;
    static final int C_AMBER = 0xFFF6AD55, C_RED = 0xFFF43F5E;
    static final int TRACK = 0xFF262D38, TICK = 0x40FFFFFF, TICK_MAJOR = 0x80FFFFFF;
    // Dial scales: match your car's own cluster (here 0-220 km/h, 0-8 x1000 rpm, red zone from 6,000).
    static final float VMAX = 220f, RED_FROM_RPM = 6000f;
    private static final int TRAIL = 24;

    final DriveModel model;
    protected final float d;
    protected boolean gps;
    /** Speed readout with hysteresis, so it doesn't flicker between two integers. */
    protected int displayed;
    /** Limit pulse (0.55..1) while far over the limit, else 1. */
    protected float pulse = 1f;
    protected boolean hardOver;

    protected final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = stroke(), tick = stroke();
    protected final Paint small = text("sans-serif"), bold = text("sans-serif-medium");
    protected final RectF box = new RectF();

    private float colR, colG, colB = -1;
    private long lastColorMs, lastTrailMs;
    private final float[] trailX = new float[TRAIL], trailY = new float[TRAIL];
    private int trailHead, trailCount;

    DriveView(Context c, DriveModel model) {
        super(c);
        this.model = model;
        d = c.getResources().getDisplayMetrics().density;
        tick.setStrokeCap(Paint.Cap.BUTT);
        small.setColor(MainActivity.SUB);
        small.setFontFeatureSettings("tnum");
        bold.setFontFeatureSettings("tnum");
    }

    // ------------------------------------------------------------------ inputs from MainActivity

    void onFix(float kmh, Location l) {
        gps = true;
        model.setNoFix(false);
        model.onFix(kmh, l, SystemClock.uptimeMillis());
        invalidate();
    }

    void setGps(boolean fix) {
        model.setNoFix(!fix);
        if (fix != gps) {
            gps = fix;
            invalidate();
        }
    }

    void startDemo() {
        model.startDemo(SystemClock.uptimeMillis());
        invalidate();
    }

    // ------------------------------------------------------------------ frame loop

    @Override
    protected final void onDraw(Canvas c) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        long now = SystemClock.uptimeMillis();
        DriveModel m = model;
        boolean more = m.frame(now);

        hardOver = m.mode == DriveModel.OVER && m.speed > m.limit + 10;
        int target = m.mode == DriveModel.ECO ? C_ECO : m.mode == DriveModel.SPORT ? C_SPORT
            : m.mode == DriveModel.OVER ? (hardOver ? C_RED : C_AMBER) : C_NORMAL;
        more |= fadeColor(target, now);
        pulse = 1f;
        if (hardOver) {
            pulse = 0.55f + 0.45f * (float) Math.sin(now / 140.0);
            more = true;
        }
        if (m.speed < 0.5f) displayed = 0;
        else if (Math.abs(m.speed - displayed) >= 0.7f) displayed = Math.round(m.speed);

        more |= draw(c, m, rgb(), now);

        if (more) postInvalidateOnAnimation();
        else m.idle();
    }

    /** Draw one frame. Return true to keep animating (e.g. a time-based effect of your own). */
    protected abstract boolean draw(Canvas c, DriveModel m, int col, long now);

    protected boolean showSpeed() {
        return model.hasFix() || model.demo;
    }

    // ------------------------------------------------------------------ shared pieces

    protected static String modeName(DriveModel m) {
        return m.mode == DriveModel.ECO ? "ECO" : m.mode == DriveModel.SPORT ? "SPORT"
            : m.mode == DriveModel.OVER ? "LIMIT " + m.limit : "NORMAL";
    }

    /** rpm rounded to 50, as "1,250". */
    protected static String rpmText(float rpmF) {
        int rpm = Math.round(rpmF / 50f) * 50;
        return rpm < 1000 ? String.valueOf(rpm) : (rpm / 1000) + "," + pad3(rpm % 1000);
    }

    /** Small "GPS" / "NO GPS" / "DEMO" status with a dot, right-aligned at (x, y). */
    protected void drawGpsDot(Canvas c, float x, float y) {
        fill.setColor(gps || model.demo ? MainActivity.ACCENT : 0xFF59616D);
        c.drawCircle(x, y, 3.5f * d, fill);
        small.setTextSize(10 * d);
        small.setTextAlign(Paint.Align.RIGHT);
        small.setColor(MainActivity.SUB);
        c.drawText(model.demo ? "DEMO" : gps ? "GPS" : "NO GPS", x - 8 * d, y + 3.5f * d, small);
    }

    /** Rounded gear chip ("N", "D1".."D4") centred at (x, y). */
    protected void drawGearChip(Canvas c, DriveModel m, int col, float x, float y, float textPx) {
        String g = m.neutral ? "N" : "D" + m.gear;
        bold.setTextSize(textPx);
        bold.setTextAlign(Paint.Align.CENTER);
        float gw = Math.max(textPx * 2.3f, bold.measureText(g) + textPx * 1.2f), gh = textPx * 1.6f;
        box.set(x - gw / 2f, y - gh / 2f, x + gw / 2f, y + gh / 2f);
        ring.setColor(col);
        ring.setStrokeWidth(1.5f * d);
        c.drawRoundRect(box, textPx * 0.45f, textPx * 0.45f, ring);
        bold.setColor(col);
        c.drawText(g, x, y + textPx * 0.36f, bold);
    }

    /** G-ball: the dot shows the force felt (pushed back when accelerating, outward in a bend). */
    protected void drawGBall(Canvas c, DriveModel m, int col, float gcx, float gcy, float gr, long now) {
        ring.setStrokeWidth(1.2f * d);
        ring.setColor(0x40FFFFFF);
        c.drawCircle(gcx, gcy, gr, ring);
        ring.setColor(0x26FFFFFF);
        c.drawCircle(gcx, gcy, gr * 0.5f, ring);
        c.drawLine(gcx - gr, gcy, gcx + gr, gcy, ring);
        c.drawLine(gcx, gcy - gr, gcx, gcy + gr, ring);
        // Outer ring = 0.4 g (hard braking / brisk cornering), inner ring = 0.2 g.
        float dx = DriveModel.clamp(-m.gLat / 0.4f, -1.1f, 1.1f) * gr;
        float dy = DriveModel.clamp(m.gLong / 0.4f, -1.1f, 1.1f) * gr;
        if (now - lastTrailMs > 80) {
            lastTrailMs = now;
            trailX[trailHead] = dx / gr;
            trailY[trailHead] = dy / gr;
            trailHead = (trailHead + 1) % TRAIL;
            if (trailCount < TRAIL) trailCount++;
        }
        float dot = Math.max(3f * d, gr * 0.11f);
        for (int i = 0; i < trailCount; i++) {
            int idx = (trailHead - 1 - i + TRAIL) % TRAIL;
            fill.setColor(withAlpha(col, (int) (0x60 * (1f - i / (float) TRAIL))));
            c.drawCircle(gcx + trailX[idx] * gr, gcy + trailY[idx] * gr, dot * 0.45f, fill);
        }
        fill.setColor(withAlpha(col, 0x50));
        c.drawCircle(gcx + dx, gcy + dy, dot * 1.8f, fill);
        fill.setColor(col);
        c.drawCircle(gcx + dx, gcy + dy, dot, fill);
        float gTot = (float) Math.hypot(m.gLong, m.gLat);
        small.setTextSize(10 * d);
        small.setTextAlign(Paint.Align.LEFT);
        small.setColor(MainActivity.SUB);
        c.drawText(fmt1(gTot) + "g", gcx + gr * 0.72f, gcy + gr + 2 * d, small);
    }

    /** 10-segment eco bar: red, amber, green. */
    protected void drawEcoBar(Canvas c, float eco, float x, float y, float w, float h) {
        int segs = 10;
        float gap = 4 * d, segW = (w - gap * (segs - 1)) / segs;
        int on = Math.round(eco / 10f);
        for (int i = 0; i < segs; i++) {
            float sx = x + i * (segW + gap);
            box.set(sx, y, sx + segW, y + h);
            int segCol = i < 3 ? C_RED : i < 6 ? C_AMBER : C_ECO;
            fill.setColor(i < on ? segCol : TRACK);
            c.drawRoundRect(box, Math.min(3 * d, h / 2f), Math.min(3 * d, h / 2f), fill);
        }
    }

    protected static void radial(Canvas c, float cx, float cy, float angleDeg, float r1, float r2, Paint p) {
        double a = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        c.drawLine(cx + r1 * cos, cy + r1 * sin, cx + r2 * cos, cy + r2 * sin, p);
    }

    // ------------------------------------------------------------------ colour

    /** Smoothly blend the current colour toward {@code target}. Returns true while still fading. */
    private boolean fadeColor(int target, long now) {
        float tr = (target >> 16) & 0xFF, tg = (target >> 8) & 0xFF, tb = target & 0xFF;
        if (colB < 0) {
            colR = tr;
            colG = tg;
            colB = tb;
            lastColorMs = now;
            return false;
        }
        float dt = Math.min((now - lastColorMs) / 1000f, 0.1f);
        lastColorMs = now;
        float k = 1f - (float) Math.exp(-dt / 0.2f);
        colR += (tr - colR) * k;
        colG += (tg - colG) * k;
        colB += (tb - colB) * k;
        boolean fading = Math.abs(tr - colR) + Math.abs(tg - colG) + Math.abs(tb - colB) > 3f;
        if (!fading) {
            colR = tr;
            colG = tg;
            colB = tb;
        }
        return fading;
    }

    private int rgb() {
        return 0xFF000000 | (Math.round(colR) << 16) | (Math.round(colG) << 8) | Math.round(colB);
    }

    // ------------------------------------------------------------------ helpers

    protected static Paint stroke() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        return p;
    }

    protected static Paint text(String family) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return p;
    }

    protected static int withAlpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    protected static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    protected static String fmt1(float v) {
        int t = Math.round(v * 10);
        return (t / 10) + "." + (t % 10);
    }

    protected static String pad2(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    protected static String pad3(int v) {
        return v < 10 ? "00" + v : v < 100 ? "0" + v : String.valueOf(v);
    }
}
