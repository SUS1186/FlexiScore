package com.parakinetics.flexiscore.math;

import java.util.Arrays;

/**
 * Pure biomechanics math (no Android classes, so it is easy to unit-test).
 * All points are {x, y, z} float arrays in the SAME unit system.
 */
public final class Goniometermath {

    private Goniometermath() {}

    /**
     * Angle (degrees, 0..180) at {@code vertex} between the rays vertex->a and vertex->c.
     * For the elbow: a = shoulder, vertex = elbow, c = wrist.
     * 180 = fully straight arm, ~30-40 = fully bent arm.
     * Returns NaN if the points are degenerate.
     */
    public static float angleAtVertex(float[] a, float[] vertex, float[] c) {
        double ux = a[0] - vertex[0], uy = a[1] - vertex[1], uz = a[2] - vertex[2];
        double vx = c[0] - vertex[0], vy = c[1] - vertex[1], vz = c[2] - vertex[2];
        double nu = Math.sqrt(ux * ux + uy * uy + uz * uz);
        double nv = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (nu < 1e-6 || nv < 1e-6) return Float.NaN;
        double cos = (ux * vx + uy * vy + uz * vz) / (nu * nv);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return (float) Math.toDegrees(Math.acos(cos));
    }

    /**
     * Sideways lean of the trunk in degrees (0 = perfectly upright).
     * Uses the line from the hip-mid-point to the shoulder-mid-point versus the vertical (Y) axis.
     */
    public static float trunkLeanDegrees(float[] lShoulder, float[] rShoulder,
                                         float[] lHip, float[] rHip) {
        float sx = (lShoulder[0] + rShoulder[0]) / 2f;
        float sy = (lShoulder[1] + rShoulder[1]) / 2f;
        float hx = (lHip[0] + rHip[0]) / 2f;
        float hy = (lHip[1] + rHip[1]) / 2f;
        double dx = Math.abs(sx - hx);
        double dy = Math.abs(sy - hy);
        if (dx < 1e-9 && dy < 1e-9) return Float.NaN;
        return (float) Math.toDegrees(Math.atan2(dx, dy));
    }

    /** Median of the first n values of src (src is not modified). */
    public static float median(float[] src, int n) {
        if (n <= 0) return Float.NaN;
        float[] copy = Arrays.copyOf(src, n);
        Arrays.sort(copy);
        if (n % 2 == 1) return copy[n / 2];
        return (copy[n / 2 - 1] + copy[n / 2]) / 2f;
    }
}