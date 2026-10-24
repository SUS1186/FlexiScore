package com.parakinetics.flexiscore.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.parakinetics.flexiscore.ml.Poseframe;

import java.util.Locale;

/**
 * Transparent view drawn on top of the camera preview.
 * Draws both arms (shoulder - elbow - wrist), a swept arc + number at each elbow and the torso line.
 * Assumes the PreviewView uses its default FILL_CENTER scale type.
 */
public class Goniometeroverlayview extends View {

    private static final float MIN_VIS = 0.5f;
    private static final int COLOR_LEFT = 0xFF00E5FF;    // cyan
    private static final int COLOR_RIGHT = 0xFFE040FB;   // purple
    private static final int COLOR_TORSO_OK = 0x99FFFFFF;
    private static final int COLOR_TORSO_BAD = 0xFFFF1744;

    private Poseframe frame;
    private boolean mirror;
    private float leftAngle = Float.NaN;
    private float rightAngle = Float.NaN;
    private boolean leanBad;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final float density;

    public Goniometeroverlayview(Context context) { this(context, null); }

    public Goniometeroverlayview(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    public Goniometeroverlayview(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(5f * density);

        dotPaint.setStyle(Paint.Style.FILL);

        arcFill.setStyle(Paint.Style.FILL);
        arcStroke.setStyle(Paint.Style.STROKE);
        arcStroke.setStrokeWidth(3f * density);

        textPaint.setTextSize(20f * density);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(4f * density, 0, 0, Color.BLACK);
        setWillNotDraw(false);
    }

    /** Called for every frame (main thread). Angles may be NaN. */
    public void setPose(Poseframe frame, boolean mirror, float leftAngle, float rightAngle, boolean leanBad) {
        this.frame = (frame != null && frame.hasPose) ? frame : null;
        this.mirror = mirror;
        this.leftAngle = leftAngle;
        this.rightAngle = rightAngle;
        this.leanBad = leanBad;
        invalidate();
    }

    public void clear() {
        frame = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (frame == null || frame.imageWidth <= 0 || frame.imageHeight <= 0) return;

        float vw = getWidth(), vh = getHeight();
        float scale = Math.max(vw / frame.imageWidth, vh / frame.imageHeight);   // FILL_CENTER
        float offX = (vw - frame.imageWidth * scale) / 2f;
        float offY = (vh - frame.imageHeight * scale) / 2f;

        drawTorso(canvas, scale, offX, offY);
        drawArm(canvas, Poseframe.L_SHOULDER, Poseframe.L_ELBOW, Poseframe.L_WRIST, COLOR_LEFT, leftAngle, scale, offX, offY);
        drawArm(canvas, Poseframe.R_SHOULDER, Poseframe.R_ELBOW, Poseframe.R_WRIST, COLOR_RIGHT, rightAngle, scale, offX, offY);
    }

    private float sx(int i, float scale, float offX) {
        float x = mirror ? 1f - frame.nx[i] : frame.nx[i];
        return x * frame.imageWidth * scale + offX;
    }

    private float sy(int i, float scale, float offY) {
        return frame.ny[i] * frame.imageHeight * scale + offY;
    }

    private void drawTorso(Canvas canvas, float scale, float offX, float offY) {
        if (!frame.torsoVisible(MIN_VIS)) return;
        float shx = (sx(Poseframe.L_SHOULDER, scale, offX) + sx(Poseframe.R_SHOULDER, scale, offX)) / 2f;
        float shy = (sy(Poseframe.L_SHOULDER, scale, offY) + sy(Poseframe.R_SHOULDER, scale, offY)) / 2f;
        float hx = (sx(Poseframe.L_HIP, scale, offX) + sx(Poseframe.R_HIP, scale, offX)) / 2f;
        float hy = (sy(Poseframe.L_HIP, scale, offY) + sy(Poseframe.R_HIP, scale, offY)) / 2f;

        linePaint.setColor(leanBad ? COLOR_TORSO_BAD : COLOR_TORSO_OK);
        linePaint.setStrokeWidth((leanBad ? 6f : 3f) * density);
        canvas.drawLine(sx(Poseframe.L_SHOULDER, scale, offX), sy(Poseframe.L_SHOULDER, scale, offY),
                sx(Poseframe.R_SHOULDER, scale, offX), sy(Poseframe.R_SHOULDER, scale, offY), linePaint);
        canvas.drawLine(shx, shy, hx, hy, linePaint);
    }

    private void drawArm(Canvas canvas, int s, int e, int w, int color, float angle,
                         float scale, float offX, float offY) {
        if (!frame.armVisible(s, e, w, MIN_VIS)) return;

        float x1 = sx(s, scale, offX), y1 = sy(s, scale, offY);
        float x2 = sx(e, scale, offX), y2 = sy(e, scale, offY);
        float x3 = sx(w, scale, offX), y3 = sy(w, scale, offY);

        // swept arc at the elbow: starts at the upper arm, sweeps by the measured angle
        if (!Float.isNaN(angle)) {
            float radius = 38f * density;
            arcRect.set(x2 - radius, y2 - radius, x2 + radius, y2 + radius);
            float startDeg = (float) Math.toDegrees(Math.atan2(y1 - y2, x1 - x2));
            float cross = (x1 - x2) * (y3 - y2) - (y1 - y2) * (x3 - x2);
            float sweep = (cross >= 0 ? 1f : -1f) * Math.max(0f, Math.min(180f, angle));

            arcFill.setColor((color & 0x00FFFFFF) | 0x55000000);
            canvas.drawArc(arcRect, startDeg, sweep, true, arcFill);
            arcStroke.setColor(color);
            canvas.drawArc(arcRect, startDeg, sweep, false, arcStroke);
        }

        // arm segments
        linePaint.setColor(color);
        linePaint.setStrokeWidth(5f * density);
        canvas.drawLine(x1, y1, x2, y2, linePaint);
        canvas.drawLine(x2, y2, x3, y3, linePaint);

        // joints
        dotPaint.setColor(color);
        canvas.drawCircle(x1, y1, 7f * density, dotPaint);
        canvas.drawCircle(x3, y3, 7f * density, dotPaint);
        dotPaint.setColor(Color.WHITE);
        canvas.drawCircle(x2, y2, 9f * density, dotPaint);
        dotPaint.setColor(color);
        canvas.drawCircle(x2, y2, 5f * density, dotPaint);

        // angle number, pushed away from the arm
        if (!Float.isNaN(angle)) {
            float dirX = (x1 + x3) / 2f - x2;
            float dirY = (y1 + y3) / 2f - y2;
            float len = (float) Math.hypot(dirX, dirY);
            float tx = x2, ty = y2 - 52f * density;
            if (len > 1f) {
                tx = x2 - dirX / len * 62f * density;
                ty = y2 - dirY / len * 62f * density;
            }
            textPaint.setColor(color);
            canvas.drawText(String.format(Locale.getDefault(), "%.0f\u00B0", angle),
                    tx, ty + textPaint.getTextSize() / 3f, textPaint);
        }
    }
}