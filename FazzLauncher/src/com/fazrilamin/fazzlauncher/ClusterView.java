package com.fazrilamin.fazzlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Skin 1 (Classic) drive cluster: glowing speed ring, segmented (simulated) RPM ring, gear + rpm readout,
 * and a bottom strip with a G-ball, eco/optimum block and trip computer.
 * Colour follows the drive mode: ECO green, NORMAL cyan, SPORT orange, over the limit amber/red.
 */
public class ClusterView extends DriveView {
    private static final float START = 150f, SWEEP = 240f;
    private static final int RPM_SEGMENTS = 48;

    private final Paint track = stroke(), glowWide = stroke(), glowMid = stroke(), core = stroke(), tipSeg = stroke();
    private final Paint seg = stroke(), vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digits = text("sans-serif-light"), unit = text("sans-serif-medium");
    private final Paint label = text("sans-serif-condensed");
    private final RectF oval = new RectF();

    private float cx, cy, r, sw, stripTop;
    private int vignetteColor;

    public ClusterView(Context c, DriveModel model) {
        super(c, model);
        track.setColor(TRACK);
        digits.setColor(MainActivity.TEXT);
        digits.setTextAlign(Paint.Align.CENTER);
        digits.setFontFeatureSettings("tnum");
        unit.setColor(MainActivity.SUB);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setLetterSpacing(0.15f);
        label.setColor(MainActivity.SUB);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(11 * d);
    }

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

    @Override
    protected boolean draw(Canvas c, DriveModel m, int col, long now) {
        if (r <= 0) return false;
        drawVignette(c, col);
        drawSpeedRing(c, m, col);
        drawRpmRing(c, m, col);
        drawCentre(c, m, col);
        drawGpsDot(c, getWidth() - 12 * d, 12 * d);
        drawStrip(c, m, col, now);
        return false;
    }

    private void drawVignette(Canvas c, int col) {
        if (col != vignetteColor) {
            vignetteColor = col;
            vignette.setShader(new RadialGradient(cx, cy, r * 1.3f, withAlpha(col, 0x1C), 0, Shader.TileMode.CLAMP));
        }
        c.drawCircle(cx, cy, r * 1.3f, vignette);
    }

    private void drawSpeedRing(Canvas c, DriveModel m, int col) {
        c.drawArc(oval, START, SWEEP, false, track);

        // Speed-limit marker outside the ring.
        if (m.limit > 0 && m.limit < VMAX) {
            float a = START + SWEEP * m.limit / VMAX;
            tick.setColor(C_AMBER);
            tick.setStrokeWidth(3 * d);
            radial(c, cx, cy, a, r + sw * 0.9f, r + sw * 2.1f, tick);
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
            radial(c, cx, cy, a, tOut - (major ? 11 : 6) * d, tOut, tick);
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
            radial(c, cx, cy, a, rr - half, rr + half, seg);
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

    private void drawCentre(Canvas c, DriveModel m, int col) {
        c.drawText(showSpeed() ? String.valueOf(displayed) : "--", cx, cy + r * 0.10f, digits);
        c.drawText("KM/H", cx, cy + r * 0.24f, unit);

        // Gear chip + rpm (simulated).
        float y = cy + r * 0.44f;
        drawGearChip(c, m, col, cx - 8 * d - 17 * d, y, 15 * d);
        String rs = rpmText(m.rpm);
        small.setTextSize(14 * d);
        small.setTextAlign(Paint.Align.LEFT);
        small.setColor(MainActivity.TEXT);
        c.drawText(rs, cx + 4 * d, y + small.getTextSize() * 0.36f, small);
        float rw = small.measureText(rs);
        small.setColor(MainActivity.SUB);
        small.setTextSize(11 * d);
        c.drawText(" rpm", cx + 4 * d + rw, y + small.getTextSize() * 0.36f, small);
    }

    private void drawStrip(Canvas c, DriveModel m, int col, long now) {
        float w = getWidth(), h = getHeight(), sh = h - stripTop;
        fill.setColor(MainActivity.STROKE);
        c.drawRect(8 * d, stripTop, w - 8 * d, stripTop + 1 * d, fill);

        // --- G-ball (real G from GPS)
        drawGBall(c, m, col, sh * 0.55f, stripTop + sh * 0.54f, sh * 0.36f, now);

        // --- Eco / optimum block
        float ex = sh * 1.15f, ew = w * 0.62f - ex;
        bold.setTextSize(16 * d);
        bold.setTextAlign(Paint.Align.LEFT);
        bold.setColor(col);
        bold.setLetterSpacing(0.12f);
        c.drawText(modeName(m), ex, stripTop + sh * 0.34f, bold);
        bold.setLetterSpacing(0f);
        small.setTextSize(13 * d);
        small.setTextAlign(Paint.Align.LEFT);
        small.setColor(MainActivity.TEXT);
        c.drawText(m.hint, ex, stripTop + sh * 0.56f, small);
        drawEcoBar(c, m.eco, ex, stripTop + sh * 0.70f, ew, 7 * d);

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
}
