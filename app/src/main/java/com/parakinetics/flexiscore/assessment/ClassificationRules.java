package com.parakinetics.flexiscore.assessment;

import java.util.List;

public class ClassificationRules {

    public static final float MAX_LATERAL_SPINE_LEAN_DEG = 5.0f;
    public static final float MAX_PELVIC_TILT_DELTA_Y = 0.05f; // Normalized screen space
    public static final float MAX_EFFORT_VARIANCE_PERCENT = 5.0f;

    /**
     * Checks if compensatory lateral trunk leaning exceeds permissible IPC threshold.
     */
    public static boolean isCompensatorySpineLean(
            float shoulderMidX, float shoulderMidY,
            float hipMidX, float hipMidY) {

        float dx = shoulderMidX - hipMidX;
        float dy = hipMidY - shoulderMidY; // Invert Y because screen space 0 is top

        double deviationDeg = Math.toDegrees(Math.abs(Math.atan2(dx, dy)));
        return deviationDeg > MAX_LATERAL_SPINE_LEAN_DEG;
    }

    /**
     * Checks if pelvic tilt compensation is present via vertical disparity between hips.
     */
    public static boolean isPelvicTiltCompensated(float leftHipY, float rightHipY) {
        return Math.abs(leftHipY - rightHipY) > MAX_PELVIC_TILT_DELTA_Y;
    }

    /**
     * Calculates Bilateral Asymmetry Deficit % against the unaffected benchmark limb.
     */
    public static float calculateBilateralDeficit(float unaffectedAngle, float affectedAngle) {
        if (unaffectedAngle <= 0.0f) return 0.0f;
        return (Math.abs(unaffectedAngle - affectedAngle) / unaffectedAngle) * 100.0f;
    }

    /**
     * Checks consistency across 3 consecutive effort repetitions.
     * True if variance <= 5% (genuine effort); False if > 15% (faked restriction).
     */
    public static float calculateTrialVariance(List<Float> trials) {
        if (trials == null || trials.size() < 3) return 0.0f;

        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float sum = 0.0f;

        for (float val : trials) {
            if (val < min) min = val;
            if (val > max) max = val;
            sum += val;
        }

        float mean = sum / trials.size();
        if (mean == 0) return 0.0f;

        return ((max - min) / mean) * 100.0f;
    }
}