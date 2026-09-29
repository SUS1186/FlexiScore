package com.parakinetics.flexiscore.ml;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public interface PoseUpdateListener {
    void onPoseLandmarks(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight);
    void onError(String errorMessage);
}