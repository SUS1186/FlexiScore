package com.parakinetics.flexiscore.ui;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;
import com.parakinetics.flexiscore.R;
import com.parakinetics.flexiscore.assessment.Classificationrules;
import com.parakinetics.flexiscore.assessment.Peakangletracker;
import com.parakinetics.flexiscore.data.AssessmentResult;
import com.parakinetics.flexiscore.math.Goniometermath;
import com.parakinetics.flexiscore.ml.Poseframe;
import com.parakinetics.flexiscore.ml.Poselandmarkerhelper;
import com.parakinetics.flexiscore.ml.PoseUpdateListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live assessment screen.
 *
 * Flow (fully automatic - no tap needed):
 *   WAITING    -> looks for a person with both arms visible
 *   GET_READY  -> person held for 1 s -> 3-2-1 countdown
 *   TESTING    -> video recording + 15 s timer, both elbow angles measured from real pose landmarks
 *   FINISHING  -> recording is saved, then the report opens
 */
public class AssessmentActivity extends AppCompatActivity implements PoseUpdateListener {

    public static final String EXTRA_ATHLETE_NAME = "ATHLETE_NAME";
    public static final String EXTRA_ATHLETE_ID = "ATHLETE_ID";

    private static final String TAG = "AssessmentActivity";

    // ----------------------------------------------------------- settings you can tweak
    private static final long TEST_DURATION_MS = 15_000;     // length of the recorded test
    private static final long MIN_MANUAL_FINISH_MS = 4_000;  // "Stop & Report" allowed after this
    private static final int GET_READY_SECONDS = 3;          // countdown before recording starts
    private static final long DETECT_HOLD_MS = 1_000;        // person must be seen this long first
    private static final long LOST_CANCEL_COUNTDOWN_MS = 1_000;
    private static final long LOST_ABORT_TEST_MS = 3_000;    // person gone this long -> restart
    private static final long FINISH_WATCHDOG_MS = 6_000;    // never get stuck waiting for the video file
    private static final float MIN_VISIBILITY = 0.5f;
    // ---------------------------------------------------------------------------------

    private enum State { WAITING, GET_READY, TESTING, FINISHING }

    // views
    private PreviewView viewFinder;
    private Goniometeroverlayview overlay;
    private TextView tvStatus, tvRecTimer, tvCountdown;
    private TextView tvLeftAngle, tvLeftState, tvLeftRom;
    private TextView tvRightAngle, tvRightState, tvRightRom;
    private View layoutRec, dotRec;
    private LinearProgressIndicator progressTest;
    private Button btnFlip, btnFinish;

    // camera + AI
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private volatile Poselandmarkerhelper poseHelper;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;

    // threading: frames come from a background thread, everything else runs on the main thread
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicReference<Poseframe> latestFrame = new AtomicReference<>();
    private final Runnable consumeFrame = () -> {
        Poseframe f = latestFrame.getAndSet(null);
        if (f != null) handlePose(f);
    };
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            onTick();
            mainHandler.postDelayed(this, 100);
        }
    };

    // state
    private State state = State.WAITING;
    private final Peakangletracker leftTracker = new Peakangletracker();
    private final Peakangletracker rightTracker = new Peakangletracker();
    private long presenceSince = 0;
    private long lastPresentTime = 0;
    private long lastFrameTime = 0;
    private long countdownEnd = 0;
    private long testStart = 0;
    private long finishingSince = 0;
    private int leanSamples = 0, leanViolations = 0;
    private float maxLean = 0f;

    // recording / navigation
    private int recordingCounter = 0;
    private boolean pendingNavigate = false;
    private boolean navigated = false;
    private Uri recordedVideoUri = null;
    private AssessmentResult pendingResult = null;

    // ---------------------------------------------------------------- permissions
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                if (hasCameraPermission()) {
                    initPoseAndCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required for the assessment.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // ---------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assessment);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewFinder = findViewById(R.id.viewFinder);
        overlay = findViewById(R.id.overlay);
        tvStatus = findViewById(R.id.tvStatus);
        tvRecTimer = findViewById(R.id.tvRecTimer);
        tvCountdown = findViewById(R.id.tvCountdown);
        layoutRec = findViewById(R.id.layoutRec);
        dotRec = findViewById(R.id.dotRec);
        progressTest = findViewById(R.id.progressTest);
        tvLeftAngle = findViewById(R.id.tvLeftAngle);
        tvLeftState = findViewById(R.id.tvLeftState);
        tvLeftRom = findViewById(R.id.tvLeftRom);
        tvRightAngle = findViewById(R.id.tvRightAngle);
        tvRightState = findViewById(R.id.tvRightState);
        tvRightRom = findViewById(R.id.tvRightRom);
        btnFlip = findViewById(R.id.btnFlip);
        btnFinish = findViewById(R.id.btnFinish);

        cameraExecutor = Executors.newSingleThreadExecutor();

        btnFlip.setOnClickListener(v -> flipCamera());
        btnFinish.setOnClickListener(v -> {
            if (state != State.TESTING) return;
            long elapsed = SystemClock.elapsedRealtime() - testStart;
            if (elapsed < MIN_MANUAL_FINISH_MS) {
                Toast.makeText(this, "Keep moving your arms a little longer...", Toast.LENGTH_SHORT).show();
            } else {
                finishTest();
            }
        });
        updateButtons();
        setStatus("Starting camera...", R.color.white);

        if (hasCameraPermission()) {
            initPoseAndCamera();
        } else {
            List<String> perms = new ArrayList<>();
            perms.add(Manifest.permission.CAMERA);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);   // needed to save video on Android 9-
            }
            permissionLauncher.launch(perms.toArray(new String[0]));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mainHandler.post(ticker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mainHandler.removeCallbacks(ticker);
        if (state == State.TESTING || state == State.GET_READY) {
            abortToWaiting(null);          // app went to background -> cancel cleanly
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (activeRecording != null) {
            activeRecording.close();
            activeRecording = null;
        }
        final Poselandmarkerhelper helper = poseHelper;
        poseHelper = null;
        if (cameraExecutor != null) {
            // close the model on the analysis thread so it never runs at the same time as a frame
            if (helper != null) cameraExecutor.execute(helper::close);
            cameraExecutor.shutdown();
        }
    }

    // ---------------------------------------------------------------- setup
    private void initPoseAndCamera() {
        setStatus("Loading AI model...", R.color.white);
        cameraExecutor.execute(() -> {
            try {
                poseHelper = new Poselandmarkerhelper(getApplicationContext(), this);
                runOnUiThread(this::bindCamera);
            } catch (Exception e) {
                Log.e(TAG, "Could not load pose model", e);
                runOnUiThread(() -> {
                    setStatus("AI model failed to load", R.color.fs_red);
                    Toast.makeText(this, "Pose model error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void bindCamera() {
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                rebindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera init failed", e);
                setStatus("Camera error", R.color.fs_red);
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private CameraSelector selectorFor(int facing) {
        return new CameraSelector.Builder().requireLensFacing(facing).build();
    }

    private void rebindUseCases() {
        if (cameraProvider == null || isDestroyed()) return;

        CameraSelector selector = selectorFor(lensFacing);
        try {
            if (!cameraProvider.hasCamera(selector)) {       // e.g. device without a front camera
                lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                        ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
                selector = selectorFor(lensFacing);
            }
        } catch (CameraInfoUnavailableException ignored) { }

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, selector, preview, analysis, videoCapture);
        } catch (Exception first) {
            Log.w(TAG, "Preview+Analysis+Video not supported, retrying without video", first);
            videoCapture = null;
            try {
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);
                Toast.makeText(this, "Video recording is not supported on this device - measuring only.",
                        Toast.LENGTH_LONG).show();
            } catch (Exception second) {
                Log.e(TAG, "Camera bind failed", second);
                setStatus("Camera error", R.color.fs_red);
                return;
            }
        }
        setStatus("Stand 2-3 m in front of the camera", R.color.fs_amber);
    }

    private void analyzeFrame(@NonNull ImageProxy proxy) {
        Poselandmarkerhelper helper = poseHelper;
        if (helper == null) {
            proxy.close();
            return;
        }
        helper.detectLiveStream(proxy);
    }

    private void flipCamera() {
        if (state != State.WAITING) return;
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
        overlay.clear();
        resetTrackers();
        presenceSince = 0;
        rebindUseCases();
    }

    // ---------------------------------------------------------------- PoseUpdateListener (background thread)
    @Override
    public void onPoseResult(Poseframe frame) {
        // keep only the newest frame so the UI thread never falls behind
        if (latestFrame.getAndSet(frame) == null) {
            mainHandler.post(consumeFrame);
        }
    }

    @Override
    public void onError(String errorMessage) {
        runOnUiThread(() -> Log.e(TAG, "Pose error: " + errorMessage));
    }

    // ---------------------------------------------------------------- per-frame logic (main thread)
    private void handlePose(Poseframe f) {
        if (state == State.FINISHING) return;
        long now = SystemClock.elapsedRealtime();
        lastFrameTime = now;

        boolean present = f.hasPose && f.bothArmsVisible(MIN_VISIBILITY);
        if (present) lastPresentTime = now;

        // --- real elbow angles from the 3-D landmarks (NaN if that arm is not visible)
        float rawLeft = armAngle(f, Poseframe.L_SHOULDER, Poseframe.L_ELBOW, Poseframe.L_WRIST);
        float rawRight = armAngle(f, Poseframe.R_SHOULDER, Poseframe.R_ELBOW, Poseframe.R_WRIST);
        float left = leftTracker.update(rawLeft, now);
        float right = rightTracker.update(rawRight, now);

        // --- trunk lean (anti-cheat)
        float lean = Float.NaN;
        if (f.torsoVisible(MIN_VISIBILITY)) {
            lean = Goniometermath.trunkLeanDegrees(
                    f.world[Poseframe.L_SHOULDER], f.world[Poseframe.R_SHOULDER],
                    f.world[Poseframe.L_HIP], f.world[Poseframe.R_HIP]);
        }
        boolean leanBad = !Float.isNaN(lean) && lean > Classificationrules.LEAN_LIMIT_DEG;
        if (state == State.TESTING && !Float.isNaN(lean)) {
            leanSamples++;
            if (leanBad) leanViolations++;
            if (lean > maxLean) maxLean = lean;
        }

        // --- UI
        boolean mirror = lensFacing == CameraSelector.LENS_FACING_FRONT;
        overlay.setPose(f, mirror, left, right, leanBad);
        updateArmCard(tvLeftAngle, tvLeftState, tvLeftRom, leftTracker, left);
        updateArmCard(tvRightAngle, tvRightState, tvRightRom, rightTracker, right);

        switch (state) {
            case WAITING:
                if (present) {
                    if (presenceSince == 0) presenceSince = now;
                    setStatus("Person detected - hold still...", R.color.fs_green);
                    if (now - presenceSince >= DETECT_HOLD_MS) startGetReady(now);
                } else {
                    presenceSince = 0;
                    if (f.hasPose) setStatus("Show BOTH arms fully - step back a little", R.color.fs_amber);
                    else setStatus("No person detected - stand 2-3 m in front of the camera", R.color.fs_amber);
                }
                break;

            case GET_READY:
                setStatus("Get ready - bend & straighten both elbows when recording starts", R.color.fs_cyan);
                break;

            case TESTING:
                if (leanBad) {
                    setStatus("Keep your body upright - do not lean!", R.color.fs_red);
                } else if (!present) {
                    setStatus("Keep both arms in view", R.color.fs_amber);
                } else if (!leftTracker.isMoving() && !rightTracker.isMoving()) {
                    setStatus("Bend and straighten your elbows fully", R.color.fs_amber);
                } else {
                    setStatus("Recording - keep going!", R.color.fs_green);
                }
                break;

            default:
                break;
        }
    }

    private float armAngle(Poseframe f, int shoulder, int elbow, int wrist) {
        if (!f.armVisible(shoulder, elbow, wrist, MIN_VISIBILITY)) return Float.NaN;
        return Goniometermath.angleAtVertex(f.world[shoulder], f.world[elbow], f.world[wrist]);
    }

    private void updateArmCard(TextView tvAngle, TextView tvState, TextView tvRom,
                               Peakangletracker tracker, float angle) {
        if (Float.isNaN(angle)) {
            tvAngle.setText("--");
            tvState.setText("NOT VISIBLE");
            tvState.setTextColor(ContextCompat.getColor(this, R.color.fs_red));
        } else {
            tvAngle.setText(String.format(Locale.getDefault(), "%.0f\u00B0", angle));
            boolean moving = tracker.isMoving();
            tvState.setText(moving ? "\u25CF MOVING" : "\u25CF STILL");
            tvState.setTextColor(ContextCompat.getColor(this, moving ? R.color.fs_green : R.color.fs_amber));
        }
        boolean showRom = state == State.TESTING || state == State.FINISHING;
        tvRom.setText(showRom
                ? String.format(Locale.getDefault(), "ROM %.0f\u00B0", tracker.getRom())
                : "ROM --");
    }

    // ---------------------------------------------------------------- timer tick (every 100 ms)
    private void onTick() {
        long now = SystemClock.elapsedRealtime();

        switch (state) {
            case WAITING:
                // if the AI stops delivering frames, don't keep a stale "person detected"
                if (lastFrameTime != 0 && now - lastFrameTime > 800) {
                    presenceSince = 0;
                }
                break;

            case GET_READY: {
                if (now - lastPresentTime > LOST_CANCEL_COUNTDOWN_MS) {
                    state = State.WAITING;
                    presenceSince = 0;
                    tvCountdown.setVisibility(View.GONE);
                    updateButtons();
                    return;
                }
                long remaining = countdownEnd - now;
                if (remaining <= 0) {
                    beginTest(now);
                } else {
                    tvCountdown.setText(String.valueOf((remaining + 999) / 1000));
                }
                break;
            }

            case TESTING: {
                long elapsed = now - testStart;
                int secs = (int) (Math.min(elapsed, TEST_DURATION_MS) / 1000);
                int total = (int) (TEST_DURATION_MS / 1000);
                tvRecTimer.setText(String.format(Locale.getDefault(), "REC %02d:%02d / %02d:%02d",
                        secs / 60, secs % 60, total / 60, total % 60));
                dotRec.setAlpha(((elapsed / 500) % 2 == 0) ? 1f : 0.25f);          // blinking REC dot
                progressTest.setProgress((int) Math.min(1000, elapsed * 1000 / TEST_DURATION_MS));

                if (elapsed >= TEST_DURATION_MS) {
                    finishTest();
                } else if (now - lastPresentTime > LOST_ABORT_TEST_MS) {
                    abortToWaiting("Person left the frame - assessment restarted");
                }
                break;
            }

            case FINISHING:
                if (!navigated && finishingSince != 0 && now - finishingSince > FINISH_WATCHDOG_MS) {
                    if (pendingResult != null) goToReport(pendingResult);   // video never finalised - go on without it
                }
                break;
        }
    }

    // ---------------------------------------------------------------- state transitions
    private void startGetReady(long now) {
        state = State.GET_READY;
        countdownEnd = now + GET_READY_SECONDS * 1000L;
        tvCountdown.setText(String.valueOf(GET_READY_SECONDS));
        tvCountdown.setVisibility(View.VISIBLE);
        buzz(40);
        updateButtons();
    }

    private void beginTest(long now) {
        resetTrackers();
        state = State.TESTING;
        testStart = now;
        tvCountdown.setVisibility(View.GONE);
        layoutRec.setVisibility(View.VISIBLE);
        progressTest.setProgress(0);
        progressTest.setVisibility(View.VISIBLE);
        tvRecTimer.setText(String.format(Locale.getDefault(), "REC 00:00 / 00:%02d", TEST_DURATION_MS / 1000));
        startVideoRecording();
        buzz(90);
        updateButtons();
    }

    private void finishTest() {
        if (state != State.TESTING) return;
        long now = SystemClock.elapsedRealtime();
        state = State.FINISHING;
        finishingSince = now;
        buzz(200);
        setStatus("Saving video and analysing...", R.color.fs_cyan);
        pendingResult = buildResult(now);
        updateButtons();

        if (activeRecording != null) {
            pendingNavigate = true;
            activeRecording.stop();            // result continues in onVideoFinalized()
            activeRecording = null;
        } else {
            goToReport(pendingResult);
        }
    }

    /** Cancel the current attempt and go back to waiting for a person. msg may be null. */
    private void abortToWaiting(@Nullable String msg) {
        pendingNavigate = false;
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
        state = State.WAITING;
        presenceSince = 0;
        resetTrackers();
        layoutRec.setVisibility(View.INVISIBLE);
        progressTest.setVisibility(View.GONE);
        tvCountdown.setVisibility(View.GONE);
        updateButtons();
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void resetTrackers() {
        leftTracker.reset();
        rightTracker.reset();
        leanSamples = 0;
        leanViolations = 0;
        maxLean = 0f;
    }

    private void updateButtons() {
        btnFinish.setEnabled(state == State.TESTING);
        btnFlip.setEnabled(state == State.WAITING);
    }

    // ---------------------------------------------------------------- video recording
    private void startVideoRecording() {
        recordedVideoUri = null;
        if (videoCapture == null) return;

        try {
            String name = "FlexiScore_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FlexiScore");
            }

            MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(
                    getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(values)
                    .build();

            final int myId = ++recordingCounter;
            PendingRecording pending = videoCapture.getOutput().prepareRecording(this, options);   // no audio
            activeRecording = pending.start(ContextCompat.getMainExecutor(this), event -> {
                if (event instanceof VideoRecordEvent.Finalize) {
                    VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                    if (myId != recordingCounter) return;                 // an older recording - ignore
                    if (!fin.hasError()) {
                        recordedVideoUri = fin.getOutputResults().getOutputUri();
                    } else {
                        Log.e(TAG, "Video finalize error " + fin.getError());
                        recordedVideoUri = null;
                    }
                    if (pendingNavigate) onVideoFinalized();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Could not start recording", e);
            activeRecording = null;
            Toast.makeText(this, "Could not start video recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void onVideoFinalized() {
        pendingNavigate = false;
        if (pendingResult != null) {
            pendingResult.videoUri = recordedVideoUri != null ? recordedVideoUri.toString() : null;
            goToReport(pendingResult);
        }
    }

    // ---------------------------------------------------------------- result + navigation
    private AssessmentResult buildResult(long now) {
        AssessmentResult r = new AssessmentResult();
        r.athleteName = getIntent().getStringExtra(EXTRA_ATHLETE_NAME);
        r.athleteId = getIntent().getStringExtra(EXTRA_ATHLETE_ID);
        if (r.athleteName == null) r.athleteName = "Athlete";
        if (r.athleteId == null) r.athleteId = "N/A";

        r.leftMin = leftTracker.getMin();
        r.leftMax = leftTracker.getMax();
        r.leftFrames = leftTracker.getValidFrames();
        r.rightMin = rightTracker.getMin();
        r.rightMax = rightTracker.getMax();
        r.rightFrames = rightTracker.getValidFrames();

        r.maxTrunkLeanDeg = maxLean;
        r.leanViolationPercent = leanSamples > 0 ? 100f * leanViolations / leanSamples : 0f;
        r.durationMs = Math.min(now - testStart, TEST_DURATION_MS);

        Classificationrules.evaluate(r);       // ROM, deficit, cheat flag, MIC + class
        return r;
    }

    private void goToReport(AssessmentResult result) {
        if (navigated) return;
        navigated = true;
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra(ReportActivity.EXTRA_RESULT, result);
        startActivity(intent);
        finish();
    }

    // ---------------------------------------------------------------- small helpers
    private void setStatus(String text, int colorRes) {
        if (!text.contentEquals(tvStatus.getText())) tvStatus.setText(text);
        tvStatus.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    @SuppressWarnings("deprecation")
    private void buzz(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) { }
    }
}