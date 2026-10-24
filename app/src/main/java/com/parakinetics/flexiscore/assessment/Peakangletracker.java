package com.parakinetics.flexiscore.assessment;

import com.parakinetics.flexiscore.math.Goniometermath;

import java.util.ArrayDeque;

/**
 * Tracks ONE joint (one arm). It
 *  1. removes noise  (median of last 5 frames + exponential smoothing),
 *  2. remembers min / max angle seen (peak flexion / peak extension),
 *  3. tells if the arm is really moving (angle range over the last ~1.2 s).
 *
 * A stationary arm therefore shows ROM ~ 0 and "STATIC" - it can no longer
 * look like it is moving.
 */
public class Peakangletracker {

    private static final int MEDIAN_WINDOW = 5;
    private static final int MIN_SAMPLES_BEFORE_RECORDING = 3;
    private static final float EMA_ALPHA = 0.5f;
    private static final long MOTION_WINDOW_MS = 1200;
    public static final float MOTION_THRESHOLD_DEG = 10f;

    private final float[] buffer = new float[MEDIAN_WINDOW];
    private int bufferCount = 0;
    private int bufferIndex = 0;

    private float smoothed = Float.NaN;
    private float min = Float.POSITIVE_INFINITY;
    private float max = Float.NEGATIVE_INFINITY;
    private int validFrames = 0;

    /** history entries: {timestampMs, angle} */
    private final ArrayDeque<float[]> history = new ArrayDeque<>();
    private long lastTimeMs = 0;

    /**
     * Feed one raw angle. Pass NaN when the joint is not visible this frame.
     * @return the smoothed angle, or NaN if the joint is currently not tracked.
     */
    public float update(float rawAngle, long timeMs) {
        lastTimeMs = timeMs;
        if (Float.isNaN(rawAngle)) {
            markLost();
            return Float.NaN;
        }

        buffer[bufferIndex] = rawAngle;
        bufferIndex = (bufferIndex + 1) % MEDIAN_WINDOW;
        if (bufferCount < MEDIAN_WINDOW) bufferCount++;

        float median = Goniometermath.median(buffer, bufferCount);
        smoothed = Float.isNaN(smoothed) ? median : smoothed + EMA_ALPHA * (median - smoothed);

        if (bufferCount >= MIN_SAMPLES_BEFORE_RECORDING) {
            validFrames++;
            if (smoothed < min) min = smoothed;
            if (smoothed > max) max = smoothed;
            history.addLast(new float[]{timeMs, smoothed});
        }
        while (!history.isEmpty() && timeMs - (long) history.peekFirst()[0] > MOTION_WINDOW_MS) {
            history.removeFirst();
        }
        return smoothed;
    }

    /** Joint not visible this frame: forget short-term smoothing, keep min/max. */
    public void markLost() {
        smoothed = Float.NaN;
        bufferCount = 0;
        bufferIndex = 0;
    }

    /** True if the angle changed by more than MOTION_THRESHOLD_DEG during the last ~1.2 s. */
    public boolean isMoving() {
        if (history.size() < 2) return false;
        // drop stale history (arm lost for a while)
        if (lastTimeMs - (long) history.peekLast()[0] > MOTION_WINDOW_MS) return false;
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        for (float[] e : history) {
            if (e[1] < lo) lo = e[1];
            if (e[1] > hi) hi = e[1];
        }
        return (hi - lo) >= MOTION_THRESHOLD_DEG;
    }

    public void reset() {
        bufferCount = 0;
        bufferIndex = 0;
        smoothed = Float.NaN;
        min = Float.POSITIVE_INFINITY;
        max = Float.NEGATIVE_INFINITY;
        validFrames = 0;
        history.clear();
    }

    public float getCurrent()  { return smoothed; }
    public float getMin()      { return validFrames > 0 ? min : 0f; }   // peak flexion (most bent)
    public float getMax()      { return validFrames > 0 ? max : 0f; }   // peak extension (most straight)
    public float getRom()      { return validFrames > 0 ? max - min : 0f; }
    public int getValidFrames(){ return validFrames; }
}