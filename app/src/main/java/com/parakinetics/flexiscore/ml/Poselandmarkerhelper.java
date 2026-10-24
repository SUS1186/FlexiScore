package com.parakinetics.flexiscore.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;

/**
 * Wraps the MediaPipe PoseLandmarker (LIVE_STREAM mode).
 * Feed it CameraX frames with {@link #detectLiveStream(ImageProxy)}; results arrive asynchronously
 * through the {@link PoseUpdateListener}.
 */
public class Poselandmarkerhelper {

    private static final String TAG = "PoseLandmarkerHelper";
    private static final String MODEL_FILE = "pose_landmarker_full.task";   // in app/src/main/assets

    private final PoseUpdateListener listener;
    private PoseLandmarker poseLandmarker;
    private long lastTimestampMs = 0;

    /** Blocking (loads the model, ~0.3-1 s) - construct it on a background thread. */
    public Poselandmarkerhelper(Context context, PoseUpdateListener listener) {
        this.listener = listener;

        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)
                .setDelegate(Delegate.CPU)          // CPU is the most compatible choice
                .build();

        PoseLandmarker.PoseLandmarkerOptions options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener(this::onResult)
                .setErrorListener(this::onMediaPipeError)
                .build();

        poseLandmarker = PoseLandmarker.createFromOptions(context, options);
    }

    /** Converts the CameraX frame to an upright bitmap and sends it to MediaPipe. Always closes the proxy. */
    public void detectLiveStream(ImageProxy imageProxy) {
        PoseLandmarker landmarker = poseLandmarker;
        if (landmarker == null) {
            imageProxy.close();
            return;
        }

        Bitmap upright;
        try {
            int w = imageProxy.getWidth();
            int h = imageProxy.getHeight();
            ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
            int paddedWidth = plane.getRowStride() / plane.getPixelStride();

            Bitmap padded = Bitmap.createBitmap(paddedWidth, h, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(plane.getBuffer());
            Bitmap raw = (paddedWidth == w) ? padded : Bitmap.createBitmap(padded, 0, 0, w, h);

            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
            upright = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), matrix, true);
        } catch (Exception e) {
            Log.e(TAG, "Frame conversion failed", e);
            return;
        } finally {
            imageProxy.close();
        }

        long ts = SystemClock.uptimeMillis();
        if (ts <= lastTimestampMs) ts = lastTimestampMs + 1;   // MediaPipe needs strictly increasing times
        lastTimestampMs = ts;

        try {
            MPImage mpImage = new BitmapImageBuilder(upright).build();
            landmarker.detectAsync(mpImage, ts);
        } catch (Exception e) {
            Log.e(TAG, "detectAsync failed", e);
        }
    }

    // --- MediaPipe callbacks (background thread) ---------------------------------------------

    private void onResult(PoseLandmarkerResult result, MPImage input) {
        Poseframe frame = new Poseframe();
        frame.imageWidth = input.getWidth();
        frame.imageHeight = input.getHeight();

        List<List<NormalizedLandmark>> all = result.landmarks();
        if (all == null || all.isEmpty() || all.get(0).size() < Poseframe.COUNT) {
            listener.onPoseResult(frame);            // nobody in view
            return;
        }

        List<NormalizedLandmark> lms = all.get(0);
        List<List<Landmark>> worlds = result.worldLandmarks();
        boolean useWorld = worlds != null && !worlds.isEmpty() && worlds.get(0).size() >= Poseframe.COUNT;

        for (int i = 0; i < Poseframe.COUNT; i++) {
            NormalizedLandmark lm = lms.get(i);
            frame.nx[i] = lm.x();
            frame.ny[i] = lm.y();
            frame.visibility[i] = lm.visibility().orElse(0f);

            if (useWorld) {
                Landmark wl = worlds.get(0).get(i);          // metres, true 3-D, not stretched by aspect ratio
                frame.world[i][0] = wl.x();
                frame.world[i][1] = wl.y();
                frame.world[i][2] = wl.z();
            } else {                                          // fallback: image-pixel units
                frame.world[i][0] = lm.x() * frame.imageWidth;
                frame.world[i][1] = lm.y() * frame.imageHeight;
                frame.world[i][2] = lm.z() * frame.imageWidth;
            }
        }
        frame.hasPose = true;
        listener.onPoseResult(frame);
    }

    private void onMediaPipeError(RuntimeException e) {
        Log.e(TAG, "MediaPipe error", e);
        listener.onError(e.getMessage() == null ? "Pose detection error" : e.getMessage());
    }

    public void close() {
        if (poseLandmarker != null) {
            poseLandmarker.close();
            poseLandmarker = null;
        }
    }
}