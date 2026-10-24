package com.parakinetics.flexiscore.ml;

/**
 * One analysed camera frame, already converted into plain arrays.
 * (Immutable after creation, so it is safe to hand between threads.)
 */
public class Poseframe {

    public static final int COUNT = 33;

    // MediaPipe BlazePose landmark indices
    public static final int L_SHOULDER = 11, R_SHOULDER = 12;
    public static final int L_ELBOW = 13, R_ELBOW = 14;
    public static final int L_WRIST = 15, R_WRIST = 16;
    public static final int L_HIP = 23, R_HIP = 24;

    /** false when nobody was found in the frame. */
    public boolean hasPose = false;

    /** Size of the (rotated) image the pose was found in. */
    public int imageWidth;
    public int imageHeight;

    /** Normalised image coordinates 0..1 (used for drawing the overlay). */
    public final float[] nx = new float[COUNT];
    public final float[] ny = new float[COUNT];

    /** 3-D coordinates in one consistent unit system (metres) - used for angle maths. */
    public final float[][] world = new float[COUNT][3];

    /** 0..1 confidence that the landmark is really visible. */
    public final float[] visibility = new float[COUNT];

    public boolean armVisible(int shoulder, int elbow, int wrist, float minVis) {
        return hasPose
                && visibility[shoulder] >= minVis
                && visibility[elbow] >= minVis
                && visibility[wrist] >= minVis;
    }

    public boolean bothArmsVisible(float minVis) {
        return armVisible(L_SHOULDER, L_ELBOW, L_WRIST, minVis)
                && armVisible(R_SHOULDER, R_ELBOW, R_WRIST, minVis);
    }

    public boolean torsoVisible(float minVis) {
        return hasPose
                && visibility[L_SHOULDER] >= minVis && visibility[R_SHOULDER] >= minVis
                && visibility[L_HIP] >= minVis && visibility[R_HIP] >= minVis;
    }
}