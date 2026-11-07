package com.fazrilamin.fazzlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small vector icons drawn in code (media controls, app grid, close) so no image assets are needed. */
public class Glyph extends View {
    static final int PLAY = 0, PAUSE = 1, NEXT = 2, PREV = 3, GRID = 4, CLOSE = 5;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int type;

    public Glyph(Context c, int type, int color, int circleColor) {
        super(c);
        this.type = type;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        bg.setColor(circleColor);
    }

    void setType(int t) {
        if (t != type) {
            type = t;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float m = Math.min(w, h), s = m * 0.2f;
        if (bg.getColor() != 0) c.drawCircle(cx, cy, m / 2f, bg);
        p.setStyle(Paint.Style.FILL);
        path.reset();
        switch (type) {
            case PLAY:
                path.moveTo(cx - s * 0.7f, cy - s);
                path.lineTo(cx + s * 1.0f, cy);
                path.lineTo(cx - s * 0.7f, cy + s);
                path.close();
                c.drawPath(path, p);
                break;
            case PAUSE:
                c.drawRoundRect(cx - s * 0.8f, cy - s, cx - s * 0.2f, cy + s, s * 0.15f, s * 0.15f, p);
                c.drawRoundRect(cx + s * 0.2f, cy - s, cx + s * 0.8f, cy + s, s * 0.15f, s * 0.15f, p);
                break;
            case NEXT:
                path.moveTo(cx - s, cy - s);
                path.lineTo(cx + s * 0.5f, cy);
                path.lineTo(cx - s, cy + s);
                path.close();
                c.drawPath(path, p);
                c.drawRoundRect(cx + s * 0.55f, cy - s, cx + s * 0.95f, cy + s, s * 0.1f, s * 0.1f, p);
                break;
            case PREV:
                path.moveTo(cx + s, cy - s);
                path.lineTo(cx - s * 0.5f, cy);
                path.lineTo(cx + s, cy + s);
                path.close();
                c.drawPath(path, p);
                c.drawRoundRect(cx - s * 0.95f, cy - s, cx - s * 0.55f, cy + s, s * 0.1f, s * 0.1f, p);
                break;
            case GRID:
                float gap = s * 0.9f, dot = s * 0.26f;
                for (int i = -1; i <= 1; i++)
                    for (int j = -1; j <= 1; j++) c.drawCircle(cx + i * gap, cy + j * gap, dot, p);
                break;
            case CLOSE:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.3f);
                c.drawLine(cx - s, cy - s, cx + s, cy + s, p);
                c.drawLine(cx + s, cy - s, cx - s, cy + s, p);
                break;
        }
    }
}
