package com.fazrilamin.fazzlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Skin 2 (Map) speed card: a big digital speed, a thin 0-220 speed bar with the limit marker,
 * the (simulated) gear and rpm, the drive mode with its hint (braking feedback etc.) and the eco bar.
 */
public class DigitalSpeedView extends DriveView {
    private final Paint digits = text("sans-serif-light"), unit = text("sans-serif-medium");

    public DigitalSpeedView(Context c, DriveModel model) {
        super(c, model);
        digits.setTextAlign(Paint.Align.CENTER);
        digits.setFontFeatureSettings("tnum");
        unit.setColor(MainActivity.SUB);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setLetterSpacing(0.15f);
    }

    @Override
    protected boolean draw(Canvas c, DriveModel m, int col, long now) {
        float w = getWidth(), h = getHeight(), pad = 18 * d;

        // Mode name (top left) and GPS status (top right).
        bold.setTextSize(15 * d);
        bold.setTextAlign(Paint.Align.LEFT);
        bold.setColor(col);
        bold.setLetterSpacing(0.12f);
        c.drawText(modeName(m), pad, pad + 10 * d, bold);
        bold.setLetterSpacing(0f);
        drawGpsDot(c, w - pad + 4 * d, pad + 5 * d);

        // Big speed.
        float size = Math.min(h * 0.40f, w * 0.34f);
        digits.setTextSize(size);
        digits.setColor(hardOver ? mix(MainActivity.TEXT, C_RED, 1f - pulse) : MainActivity.TEXT);
        float base = h * 0.54f;
        c.drawText(showSpeed() ? String.valueOf(displayed) : "--", w / 2f, base, digits);
        unit.setTextSize(Math.max(11 * d, size * 0.14f));
        c.drawText("KM/H", w / 2f, base + unit.getTextSize() * 1.5f, unit);

        // Speed bar 0..VMAX with the limit marker.
        float by = h * 0.72f, bh = 6 * d, bx = pad, bw = w - 2 * pad;
        box.set(bx, by, bx + bw, by + bh);
        fill.setColor(TRACK);
        c.drawRoundRect(box, bh / 2f, bh / 2f, fill);
        float frac = DriveModel.clamp(m.speed / VMAX, 0f, 1f);
        if (frac > 0.002f) {
            box.set(bx, by - 3 * d, bx + bw * frac, by + bh + 3 * d);
            fill.setColor(withAlpha(col, (int) (0x30 * pulse)));
            c.drawRoundRect(box, bh, bh, fill);
            box.set(bx, by, bx + Math.max(bh, bw * frac), by + bh);
            fill.setColor(col);
            c.drawRoundRect(box, bh / 2f, bh / 2f, fill);
        }
        if (m.limit > 0 && m.limit < VMAX) {
            float lx = bx + bw * m.limit / VMAX;
            tick.setColor(C_AMBER);
            tick.setStrokeWidth(3 * d);
            c.drawLine(lx, by - 6 * d, lx, by + bh + 6 * d, tick);
        }

        // Gear chip + rpm (simulated) on the left, hint on the right.
        float ry = h * 0.86f;
        drawGearChip(c, m, col, pad + 17 * d, ry, 14 * d);
        String rs = rpmText(m.rpm);
        small.setTextAlign(Paint.Align.LEFT);
        small.setTextSize(15 * d);
        small.setColor(MainActivity.TEXT);
        float rx = pad + 42 * d;
        c.drawText(rs, rx, ry + 5 * d, small);
        float rw = small.measureText(rs);
        small.setTextSize(11 * d);
        small.setColor(MainActivity.SUB);
        c.drawText(" rpm", rx + rw, ry + 5 * d, small);
        small.setTextAlign(Paint.Align.RIGHT);
        small.setTextSize(14 * d);
        small.setColor(MainActivity.TEXT);
        c.drawText(m.hint, w - pad, ry + 5 * d, small);

        // Eco bar along the bottom.
        drawEcoBar(c, m.eco, pad, h - pad * 0.55f - 5 * d, w - 2 * pad, 5 * d);
        return false;
    }
}
