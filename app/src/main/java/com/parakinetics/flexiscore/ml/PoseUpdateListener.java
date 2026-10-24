package com.parakinetics.flexiscore.ml;

/** Receives results from {@link Poselandmarkerhelper}. Called on a BACKGROUND thread. */
public interface PoseUpdateListener {

    /** Called for every analysed frame - also with hasPose == false when nobody is visible. */
    void onPoseResult(Poseframe frame);

    void onError(String errorMessage);
}