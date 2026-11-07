package com.fazrilamin.fazzlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;

/**
 * Futuristic drive cluster: glowing speed ring, segmented (simulated) RPM ring, gear + rpm readout,
 * and a bottom strip with a G-ball, eco/optimum block and trip computer.
 * Colour follows the drive mode: ECO green, NORMAL cyan, SPORT orange, over the limit amber/red.
 * All state comes from {@link DriveModel}.
 */
public class ClusterView extends View {
    static final int C_ECO = 0xFF4ADE80, C_NORMAL = 0xFF22D3EE, C_SPORT = 0xFFFB923C;
    static final int C_AMBER = 0xFFF6AD55, C_RED = 0xFFF43F5E;
    private static final int TRACK = 0xFF262D38, TICK = 0x40FFFFFF, TICK_MAJOR = 0x80FFFFFF;
    // Dial scales: match your car's own cluster (here 0-220 km/h, 0-8 x1000 rpm, red zone from 6,000).
    private static final float START = 150f, SWEEP = 240f, VMAX = 220f, RED_FROM_RPM = 6000f;
    private static final int RPM_SEGMENTS = 48, TRAIL = 24;

    final DriveModel model = new DriveModel();

    private final float d;
    private boolean gps;
    private int displayed;

    private final Paint track = stroke(), glowWide = stroke(), glowMid = stroke(), core = stroke(), tipSeg = stroke();
    private final Paint tick = stroke(), seg = stroke(), ring = stroke(), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digits = text("sans-serif-light"), unit = text("sans-serif-medium");
    private final Paint label = text("sans-serif-condensed"), small = text("sans-serif"), bold = text("sans-serif-medium");
    private final RectF oval = new RectF(), box = new RectF();

    private float cx, cy, r, sw, stripTop;
    private int vignetteColor;
    private float colR, colG, colB = -1;
    private long lastColorMs, lastTrailMs;
    private final float[] trailX = new float[TRAIL], trailY = new float[TRAIL];
    private int trailHead, trailCount;

    public ClusterView(Context c, SharedPreferences prefs) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        model.load(prefs);
        track.setColor(TRACK);
        ring.setStrokeWidth(1.2f * d);
        tick.setStrokeCap(Paint.Cap.BUTT);
        digits.setColor(MainActivity.TEXT);
        digits.setTextAlign(Paint.Align.CENTER);
        digits.setFontFeatureSettings("tnum");
        unit.setColor(MainActivity.SUB);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setLetterSpacing(0.15f);
        label.setColor(MainActivity.SUB);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(11 * d);
        small.setColor(MainActivity.SUB);
        bold.setFontFeatureSettings("tnum");
        small.setFontFeatureSettings("tnum");
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

    // ------------------------------------------------------------------ layout

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float strip = h * 0.25f;
        stripTop = h - strip;
        sw = Math.max(6 * d, Math.min(w, h) * 0.022f);
        r = Math.min(w / 2f - sw * 2.4f, (stripTop - sw * 4f) / 1.5f);
        cx = w / 2f;
        cy = (stripTop - 1.5f * r) / 2f + r;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        track.setStrokeWidth(sw);
        core.setStrokeWidth(sw);
        tipSeg.setStrokeWidth(sw);
        glowMid.setStrokeWidth(sw * 2.0f);
        glowWide.setStrokeWidth(sw * 3.4f);
        seg.setStrokeWidth(Math.max(3 * d, r * 0.022f));
        digits.setTextSize(r * 0.46f);
        unit.setTextSize(r * 0.075f);
        vignetteColor = 0;
    }

    // ------------------------------------------------------------------ draw

    @Override
    protected void onDraw(Canvas c) {
        if (r <= 0) return;
        long now = SystemClock.uptimeMillis();
        boolean more = model.frame(now);
        DriveModel m = model;

        boolean hardOver = m.mode == DriveModel.OVER && m.speed > m.limit + 10;
        int target = m.mode == DriveModel.ECO ? C_ECO : m.mode == DriveModel.SPORT ? C_SPORT
            : m.mode == DriveModel.OVER ? (hardOver ? C_RED : C_AMBER) : C_NORMAL;
        more |= fadeColor(target, now);
        int col = rgb();
        float pulse = 1f;
        if (hardOver) {
            pulse = 0.55f + 0.45f * (float) Math.sin(now / 140.0);
            more = true;
        }

        // Readout with hysteresis so it doesn't flicker between two integers.
        if (m.speed < 0.5f) displayed = 0;
        else if (Math.abs(m.speed - displayed) >= 0.7f) displayed = Math.round(m.speed);
        boolean showSpeed = m.hasFix() || m.demo;

        drawVignette(c, col);
        drawSpeedRing(c, m, col, pulse);
        drawRpmRing(c, m, col);
        drawCentre(c, m, col, showSpeed);
        drawGpsDot(c);
        drawStrip(c, m, col, now);

        if (more) postInvalidateOnAnimation();
        else model.idle();
    }

    private void drawVignette(Canvas c, int col) {
        if (col != vignetteColor) {
            vignetteColor = col;
            vignette.setShader(new RadialGradient(cx, cy, r * 1.3f, withAlpha(col, 0x1C), 0, Shader.TileMode.CLAMP));
        }
        c.drawCircle(cx, cy, r * 1.3f, vignette);
    }

    private void drawSpeedRing(Canvas c, DriveModel m, int col, float pulse) {
        c.drawArc(oval, START, SWEEP, false, track);

        // Speed-limit marker outside the ring.
        if (m.limit > 0 && m.limit < VMAX) {
            float a = START + SWEEP * m.limit / VMAX;
            tick.setColor(C_AMBER);
            tick.setStrokeWidth(3 * d);
            radial(c, a, r + sw * 0.9f, r + sw * 2.1f, tick);
        }

        float frac = DriveModel.clamp(m.speed / VMAX, 0f, 1f);
        float lit = SWEEP * frac;
        if (lit > 0.4f) {
            glowWide.setColor(withAlpha(col, (int) (0x1A * pulse)));
            glowMid.setColor(withAlpha(col, (int) (0x3A * pulse)));
            core.setColor(col);
            c.drawArc(oval, START, lit, false, glowWide);
            c.drawArc(oval, START, lit, false, glowMid);
            c.drawArc(oval, START, lit, false, core);
            float tipLen = Math.min(14f, lit);
            tipSeg.setColor(mix(col, 0xFFFFFFFF, 0.5f));
            c.drawArc(oval, START + lit - tipLen, tipLen, false, tipSeg);
        }
        // Needle tip: halo + white dot.
        double ta = Math.toRadians(START + lit);
        float tx = cx + r * (float) Math.cos(ta), ty = cy + r * (float) Math.sin(ta);
        fill.setColor(withAlpha(col, (int) (0x55 * pulse)));
        c.drawCircle(tx, ty, sw * 1.5f, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(tx, ty, sw * 0.5f, fill);

        // Ticks every 10 km/h, labels every 20.
        float tOut = r - sw * 1.3f;
        for (int v = 0; v <= VMAX; v += 10) {
            boolean major = v % 20 == 0;
            float a = START + SWEEP * v / VMAX;
            tick.setStrokeWidth((major ? 2.2f : 1.4f) * d);
            tick.setColor(v <= m.speed && m.speed > 0.5f ? withAlpha(col, 0xD0) : major ? TICK_MAJOR : TICK);
            radial(c, a, tOut - (major ? 11 : 6) * d, tOut, tick);
            if (major) {
                double ar = Math.toRadians(a);
                float lr = tOut - 22 * d;
                c.drawText(String.valueOf(v), cx + lr * (float) Math.cos(ar),
                    cy + lr * (float) Math.sin(ar) + label.getTextSize() * 0.36f, label);
            }
        }
    }

    private void drawRpmRing(Canvas c, DriveModel m, int col) {
        float rr = r * 0.6f, half = 6.5f * d;
        float lit = m.rpm / DriveModel.RPM_MAX * RPM_SEGMENTS;
        int redFrom = (int) Math.floor(RED_FROM_RPM / DriveModel.RPM_MAX * RPM_SEGMENTS);
        for (int i = 0; i < RPM_SEGMENTS; i++) {
            float a = START + SWEEP * (i + 0.5f) / RPM_SEGMENTS;
            boolean on = i < lit;
            boolean red = i >= redFrom;
            seg.setColor(red ? (on ? C_RED : 0x40F43F5E) : on ? col : 0x26FFFFFF);
            radial(c, a, rr - half, rr + half, seg);
        }
        // "x1000" scale numbers 0..8 just inside the ring.
        int top = Math.round(DriveModel.RPM_MAX / 1000f);
        float lr = rr - half - 11 * d;
        for (int k = 0; k <= top; k++) {
            double ar = Math.toRadians(START + SWEEP * k / (float) top);
            label.setColor(k * 1000 >= RED_FROM_RPM ? 0xA0F43F5E : 0x70FFFFFF);
            c.drawText(String.valueOf(k), cx + lr * (float) Math.cos(ar),
                cy + lr * (float) Math.sin(ar) + label.getTextSize() * 0.36f, label);
        }
        label.setColor(MainActivity.SUB);
    }

    private void drawCentre(Canvas c, DriveModel m, int col, boolean showSpeed) {
        c.drawText(showSpeed ? String.valueOf(displayed) : "--", cx, cy + r * 0.10f, digits);
        c.drawText("KM/H", cx, cy + r * 0.24f, unit);

        // Gear chip + rpm (simulated).
        float y = cy + r * 0.44f;
        String g = m.neutral ? "N" : "D" + m.gear;
        bold.setTextSize(15 * d);
        bold.setTextAlign(Paint.Align.CENTER);
        float gw = Math.max(34 * d, bold.measureText(g) + 18 * d), gh = 24 * d;
        float gx = cx - 8 * d - gw / 2f;
        box.set(gx - gw / 2f, y - gh / 2f, gx + gw / 2f, y + gh / 2f);
        ring.setColor(col);
        ring.setStrokeWidth(1.5f * d);
        c.drawRoundRect(box, 7 * d, 7 * d, ring);
        bold.setColor(col);
        c.drawText(g, gx, y + bold.getTextSize() * 0.36f, bold);

        int rpm = Math.round(m.rpm / 50f) * 50;
        small.setTextSize(14 * d);
        small.setTextAlign(Paint.Align.LEFT);
        small.setColor(MainActivity.TEXT);
        String rs = rpm < 1000 ? String.valueOf(rpm) : (rpm / 1000) + "," + pad3(rpm % 1000);
        c.drawText(rs, cx + 4 * d, y + small.getTextSize() * 0.36f, small);
        float rw = small.measureText(rs);
        small.setColor(MainActivity.SUB);
        small.setTextSize(11 * d);
        c.drawText(" rpm", cx + 4 * d + rw, y + small.getTextSize() * 0.36f, small);
    }

    private void drawGpsDot(Canvas c) {
        float x = getWidth() - 12 * d, y = 12 * d;
        fill.setColor(gps || model.demo ? MainActivity.ACCENT : 0xFF59616D);
        c.drawCircle(x, y, 3.5f * d, fill);
        small.setTextSize(10 * d);
        small.setTextAlign(Paint.Align.RIGHT);
        small.setColor(MainActivity.SUB);
        c.drawText(model.demo ? "DEMO" : gps ? "GPS" : "NO GPS", x - 8 * d, y + 3.5f * d, small);
    }

    private void drawStrip(Canvas c, DriveModel m, int col, long now) {
        float w = getWidth(), h = getHeight(), sh = h - stripTop;
        fill.setColor(MainActivity.STROKE);
        c.drawRect(8 * d, stripTop, w - 8 * d, stripTop + 1 * d, fill);

        // --- G-ball (real G from GPS)
        float gcx = sh * 0.55f, gcy = stripTop + sh * 0.54f, gr = sh * 0.36f;
        ring.setStrokeWidth(1.2f * d);
        ring.setColor(0x40FFFFFF);
        c.drawCircle(gcx, gcy, gr, ring);
        ring.setColor(0x26FFFFFF);
        c.drawCircle(gcx, gcy, gr * 0.5f, ring);
        c.drawLine(gcx - gr, gcy, gcx + gr, gcy, ring);
        c.drawLine(gcx, gcy - gr, gcx, gcy + gr, ring);
        // The dot shows the force felt: pushed back when accelerating, outward in a bend.
        float dx = DriveModel.clamp(-m.gLat / 0.5f, -1.1f, 1.1f) * gr;
        float dy = DriveModel.clamp(m.gLong / 0.5f, -1.1f, 1.1f) * gr;
        if (now - lastTrailMs > 80) {
            lastTrailMs = now;
            trailX[trailHead] = dx;
            trailY[trailHead] = dy;
            trailHead = (trailHead + 1) % TRAIL;
            if (trailCount < TRAIL) trailCount++;
        }
        for (int i = 0; i < trailCount; i++) {
            int idx = (trailHead - 1 - i + TRAIL) % TRAIL;
            fill.setColor(withAlpha(col, (int) (0x60 * (1f - i / (float) TRAIL))));
            c.drawCircle(gcx + trailX[idx], gcy + trailY[idx], 2f * d, fill);
        }
        fill.setColor(withAlpha(col, 0x50));
        c.drawCircle(gcx + dx, gcy + dy, 8 * d, fill);
        fill.setColor(col);
        c.drawCircle(gcx + dx, gcy + dy, 4.5f * d, fill);
        float gTot = (float) Math.hypot(m.gLong, m.gLat);
        small.setTextSize(10 * d);
        small.setTextAlign(Paint.Align.LEFT);
        small.setColor(MainActivity.SUB);
        c.drawText(fmt1(gTot) + "g", gcx + gr * 0.72f, gcy + gr + 2 * d, small);

        // --- Eco / optimum block
        float ex = sh * 1.15f, ew = w * 0.62f - ex;
        String modeName = m.mode == DriveModel.ECO ? "ECO" : m.mode == DriveModel.SPORT ? "SPORT"
            : m.mode == DriveModel.OVER ? "LIMIT " + m.limit : "NORMAL";
        bold.setTextSize(16 * d);
        bold.setTextAlign(Paint.Align.LEFT);
        bold.setColor(col);
        bold.setLetterSpacing(0.12f);
        c.drawText(modeName, ex, stripTop + sh * 0.34f, bold);
        bold.setLetterSpacing(0f);
        small.setTextSize(13 * d);
        small.setColor(MainActivity.TEXT);
        c.drawText(m.hint, ex, stripTop + sh * 0.56f, small);
        int segs = 10;
        float gap = 4 * d, segW = (ew - gap * (segs - 1)) / segs, by = stripTop + sh * 0.70f, bh = 7 * d;
        int on = Math.round(m.eco / 10f);
        for (int i = 0; i < segs; i++) {
            float x = ex + i * (segW + gap);
            box.set(x, by, x + segW, by + bh);
            int segCol = i < 3 ? C_RED : i < 6 ? C_AMBER : C_ECO;
            fill.setColor(i < on ? segCol : TRACK);
            c.drawRoundRect(box, 3 * d, 3 * d, fill);
        }

        // --- Trip computer (real GPS distance)
        float tx = w - 10 * d;
        small.setTextAlign(Paint.Align.RIGHT);
        small.setTextSize(10 * d);
        small.setColor(MainActivity.SUB);
        small.setLetterSpacing(0.15f);
        c.drawText("TRIP", tx, stripTop + sh * 0.24f, small);
        small.setLetterSpacing(0f);
        bold.setTextAlign(Paint.Align.RIGHT);
        bold.setTextSize(20 * d);
        bold.setColor(MainActivity.TEXT);
        c.drawText(fmt1((float) (m.tripM / 1000.0)) + " km", tx, stripTop + sh * 0.48f, bold);
        small.setTextSize(12 * d);
        small.setColor(MainActivity.SUB);
        c.drawText("AVG " + Math.round(m.tripAvgKmh()) + "  ·  MAX " + Math.round(m.tripMax), tx,
            stripTop + sh * 0.68f, small);
        long mins = m.tripMovingMs / 60_000L;
        c.drawText((mins / 60) + ":" + pad2((int) (mins % 60)) + " h", tx, stripTop + sh * 0.86f, small);
    }

    // ------------------------------------------------------------------ helpers

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

    private void radial(Canvas c, float angleDeg, float r1, float r2, Paint p) {
        double a = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        c.drawLine(cx + r1 * cos, cy + r1 * sin, cx + r2 * cos, cy + r2 * sin, p);
    }

    private static Paint stroke() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        return p;
    }

    private static Paint text(String family) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return p;
    }

    private static int withAlpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static String fmt1(float v) {
        int t = Math.round(v * 10);
        return (t / 10) + "." + (t % 10);
    }

    private static String pad2(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static String pad3(int v) {
        return v < 10 ? "00" + v : v < 100 ? "0" + v : String.valueOf(v);
    }
}
