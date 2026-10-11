package com.parakinetics.flexiscore.math;

public class StabilizationDetector {

    private static final float VELOCITY_THRESHOLD_DEG_PER_SEC = 2.0f;
    private static final long REQUIRED_STABILITY_DURATION_MS = 1200L;

    private float previousAngle = -1.0f;
    private long previousTimestampMs = 0L;
    private long stabilizationStartTimeMs = -1L;
    private float peakAngle = 0.0f;
    private boolean isStabilized = false;

    /**
     * Evaluates a newly registered angle sample.
     *
     * @param currentAngle Angle in degrees
     * @param timestampMs  Timestamp in milliseconds
     * @return true if peak stretch has met the stabilization threshold
     */
    public boolean update(float currentAngle, long timestampMs) {
        if (currentAngle > peakAngle) {
            peakAngle = currentAngle;
        }

        if (previousAngle < 0) {
            previousAngle = currentAngle;
            previousTimestampMs = timestampMs;
            return false;
        }

        long dtMs = timestampMs - previousTimestampMs;
        if (dtMs <= 0) return false;

        float dtSec = dtMs / 1000.0f;
        float angularVelocity = Math.abs(currentAngle - previousAngle) / dtSec;

        previousAngle = currentAngle;
        previousTimestampMs = timestampMs;

        // Check if movement has decelerated below threshold
        if (angularVelocity < VELOCITY_THRESHOLD_DEG_PER_SEC) {
            if (stabilizationStartTimeMs == -1L) {
                stabilizationStartTimeMs = timestampMs;
            } else if ((timestampMs - stabilizationStartTimeMs) >= REQUIRED_STABILITY_DURATION_MS) {
                isStabilized = true;
                return true;
            }
        } else {
            // Motion detected, reset stabilization timer
            stabilizationStartTimeMs = -1L;
            isStabilized = false;
        }

        return false;
    }

    public float getPeakAngle() {
        return peakAngle;
    }

    public boolean isStabilized() {
        return isStabilized;
    }

    public void reset() {
        previousAngle = -1.0f;
        previousTimestampMs = 0L;
        stabilizationStartTimeMs = -1L;
        peakAngle = 0.0f;
        isStabilized = false;
    }
}