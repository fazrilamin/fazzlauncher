package com.fazrilamin.fazzlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Skin 3 (Twin dials): a classic car cluster with an analog speedometer (left) and a tachometer
 * (right, SIMULATED rpm) with real needles, the gear in the tachometer, and a bottom strip with
 * the G-ball, drive mode / hint / eco bar and the trip computer.
 */
public class TwinDialView extends DriveView {
    private static final float START = 150f, SWEEP = 240f;

    private final Paint track = stroke(), arc = stroke(), glow = stroke(), needle = stroke();
    private final Paint digits = text("sans-serif-light"), unit = text("sans-serif-medium");
    private final Paint label = text("sans-serif-condensed"), vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ovalL = new RectF(), ovalR = new RectF();

    private float r, sw, cy, cxL, cxR, stripTop;
    private int vignetteColor;
    private RadialGradient vigL, vigR;

    public TwinDialView(Context c, DriveModel model) {
        super(c, model);
        track.setColor(TRACK);
        track.setStrokeCap(Paint.Cap.BUTT);
        arc.setStrokeCap(Paint.Cap.BUTT);
        glow.setStrokeCap(Paint.Cap.BUTT);
        digits.setColor(MainActivity.TEXT);
        digits.setTextAlign(Paint.Align.CENTER);
        digits.setFontFeatureSettings("tnum");
        unit.setColor(MainActivity.SUB);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setLetterSpacing(0.15f);
        label.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float strip = h * 0.21f;
        stripTop = h - strip;
        r = Math.min(w * 0.215f, (stripTop - 26 * d) / 1.55f);
        sw = Math.max(5 * d, r * 0.035f);
        cy = 14 * d + r;
        cxL = w * 0.25f;
        cxR = w * 0.75f;
        ovalL.set(cxL - r, cy - r, cxL + r, cy + r);
        ovalR.set(cxR - r, cy - r, cxR + r, cy + r);
        track.setStrokeWidth(sw);
        arc.setStrokeWidth(sw);
        glow.setStrokeWidth(sw * 3f);
        needle.setStrokeWidth(Math.max(3 * d, r * 0.022f));
        label.setTextSize(Math.max(11 * d, r * 0.075f));
        vignetteColor = 0;
    }

    @Override
    protected boolean draw(Canvas c, DriveModel m, int col, long now) {
        if (r <= 0) return false;
        if (col != vignetteColor) {
            vignetteColor = col;
            vigL = new RadialGradient(cxL, cy, r * 1.15f, withAlpha(col, 0x18), 0, Shader.TileMode.CLAMP);
            vigR = new RadialGradient(cxR, cy, r * 1.15f, withAlpha(col, 0x18), 0, Shader.TileMode.CLAMP);
        }
        vignette.setShader(vigL);
        c.drawCircle(cxL, cy, r * 1.15f, vignette);
        vignette.setShader(vigR);
        c.drawCircle(cxR, cy, r * 1.15f, vignette);

        drawSpeedo(c, m, col);
        drawTacho(c, m, col);
        drawGpsDot(c, getWidth() - 12 * d, 12 * d);
        drawStrip(c, m, col, now);
        return false;
    }

    private void drawSpeedo(Canvas c, DriveModel m, int col) {
        c.drawArc(ovalL, START, SWEEP, false, track);
        float frac = DriveModel.clamp(m.speed / VMAX, 0f, 1f);
        if (frac > 0.002f) {
            glow.setColor(withAlpha(col, (int) (0x30 * pulse)));
            arc.setColor(col);
            c.drawArc(ovalL, START, SWEEP * frac, false, glow);
            c.drawArc(ovalL, START, SWEEP * frac, false, arc);
        }
        if (m.limit > 0 && m.limit < VMAX) {
            tick.setColor(C_AMBER);
            tick.setStrokeWidth(3 * d);
            radial(c, cxL, cy, START + SWEEP * m.limit / VMAX, r + sw * 0.8f, r + sw * 2.2f, tick);
        }
        float tOut = r - sw * 1.2f;
        for (int v = 0; v <= VMAX; v += 10) {
            boolean major = v % 20 == 0;
            float a = START + SWEEP * v / VMAX;
            tick.setStrokeWidth((major ? 2.4f : 1.4f) * d);
            tick.setColor(major ? 0xB0FFFFFF : TICK);
            radial(c, cxL, cy, a, tOut - (major ? r * 0.08f : r * 0.045f), tOut, tick);
            if (major) dialLabel(c, cxL, a, tOut - r * 0.17f, String.valueOf(v), 0xA0FFFFFF);
        }
        digits.setTextSize(r * 0.30f);
        c.drawText(showSpeed() ? String.valueOf(displayed) : "--", cxL, cy + r * 0.43f, digits);
        unit.setTextSize(Math.max(10 * d, r * 0.065f));
        c.drawText("KM/H", cxL, cy + r * 0.56f, unit);
        drawNeedle(c, cxL, START + SWEEP * frac, col);
    }

    private void drawTacho(Canvas c, DriveModel m, int col) {
        c.drawArc(ovalR, START, SWEEP, false, track);
        float redFrac = RED_FROM_RPM / DriveModel.RPM_MAX;
        arc.setColor(0x80F43F5E);
        c.drawArc(ovalR, START + SWEEP * redFrac, SWEEP * (1f - redFrac), false, arc);
        float frac = DriveModel.clamp(m.rpm / DriveModel.RPM_MAX, 0f, 1f);
        boolean inRed = m.rpm >= RED_FROM_RPM;
        int litCol = inRed ? C_RED : col;
        glow.setColor(withAlpha(litCol, 0x30));
        arc.setColor(litCol);
        c.drawArc(ovalR, START, SWEEP * frac, false, glow);
        c.drawArc(ovalR, START, SWEEP * frac, false, arc);

        float tOut = r - sw * 1.2f;
        int top = Math.round(DriveModel.RPM_MAX / 1000f);
        for (int k = 0; k <= top * 2; k++) {       // a tick every 500 rpm
            boolean major = k % 2 == 0;
            float rpm = k * 500f;
            float a = START + SWEEP * rpm / DriveModel.RPM_MAX;
            boolean red = rpm >= RED_FROM_RPM;
            tick.setStrokeWidth((major ? 2.4f : 1.4f) * d);
            tick.setColor(red ? 0xC0F43F5E : major ? 0xB0FFFFFF : TICK);
            radial(c, cxR, cy, a, tOut - (major ? r * 0.08f : r * 0.045f), tOut, tick);
            if (major) dialLabel(c, cxR, a, tOut - r * 0.17f, String.valueOf(k / 2), red ? 0xC0F43F5E : 0xA0FFFFFF);
        }
        label.setColor(0x70FFFFFF);
        c.drawText("x1000 r/min", cxR, cy - r * 0.30f, label);

        drawGearChip(c, m, col, cxR, cy + r * 0.31f, r * 0.12f);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(Math.max(12 * d, r * 0.075f));
        small.setColor(MainActivity.TEXT);
        c.drawText(rpmText(m.rpm) + " rpm", cxR, cy + r * 0.56f, small);
        drawNeedle(c, cxR, START + SWEEP * frac, inRed ? C_RED : col);
    }

    private void dialLabel(Canvas c, float cx, float angle, float rad, String s, int color) {
        double ar = Math.toRadians(angle);
        label.setColor(color);
        c.drawText(s, cx + rad * (float) Math.cos(ar), cy + rad * (float) Math.sin(ar) + label.getTextSize() * 0.36f, label);
    }

    private void drawNeedle(Canvas c, float cx, float angle, int col) {
        needle.setColor(withAlpha(col, 0x50));
        needle.setStrokeWidth(Math.max(7 * d, r * 0.05f));
        radial(c, cx, cy, angle, r * 0.10f, r * 0.84f, needle);
        needle.setColor(mix(col, 0xFFFFFFFF, 0.35f));
        needle.setStrokeWidth(Math.max(3 * d, r * 0.02f));
        radial(c, cx, cy, angle, -r * 0.08f, r * 0.86f, needle);
        fill.setColor(MainActivity.PANEL);
        c.drawCircle(cx, cy, r * 0.07f, fill);
        ring.setColor(col);
        ring.setStrokeWidth(2 * d);
        c.drawCircle(cx, cy, r * 0.07f, ring);
    }

    private void drawStrip(Canvas c, DriveModel m, int col, long now) {
        float w = getWidth(), h = getHeight(), sh = h - stripTop;
        fill.setColor(MainActivity.STROKE);
        c.drawRect(8 * d, stripTop, w - 8 * d, stripTop + 1 * d, fill);

        float gr = sh * 0.34f;
        drawGBall(c, m, col, 14 * d + gr, stripTop + sh * 0.52f, gr, now);

        float ex = 14 * d + gr * 2 + 28 * d, ew = w * 0.58f - ex;
        bold.setTextSize(16 * d);
        bold.setTextAlign(Paint.Align.LEFT);
        bold.setColor(col);
        bold.setLetterSpacing(0.12f);
        c.drawText(modeName(m), ex, stripTop + sh * 0.36f, bold);
        bold.setLetterSpacing(0f);
        small.setTextAlign(Paint.Align.LEFT);
        small.setTextSize(14 * d);
        small.setColor(MainActivity.TEXT);
        c.drawText(m.hint, ex + bold.measureText(modeName(m)) + 22 * d, stripTop + sh * 0.36f, small);
        drawEcoBar(c, m.eco, ex, stripTop + sh * 0.56f, ew, 7 * d);

        float tx = w - 10 * d;
        small.setTextAlign(Paint.Align.RIGHT);
        small.setTextSize(10 * d);
        small.setColor(MainActivity.SUB);
        small.setLetterSpacing(0.15f);
        c.drawText("TRIP", tx, stripTop + sh * 0.26f, small);
        small.setLetterSpacing(0f);
        bold.setTextAlign(Paint.Align.RIGHT);
        bold.setTextSize(20 * d);
        bold.setColor(MainActivity.TEXT);
        c.drawText(fmt1((float) (m.tripM / 1000.0)) + " km", tx, stripTop + sh * 0.56f, bold);
        small.setTextSize(12 * d);
        small.setColor(MainActivity.SUB);
        long mins = m.tripMovingMs / 60_000L;
        c.drawText("AVG " + Math.round(m.tripAvgKmh()) + "  ·  MAX " + Math.round(m.tripMax) + "  ·  "
            + (mins / 60) + ":" + pad2((int) (mins % 60)) + " h", tx, stripTop + sh * 0.84f, small);
    }
}
