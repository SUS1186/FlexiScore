package com.parakinetics.flexiscore.data;

import java.io.Serializable;

/** Everything measured in one assessment. Passed from AssessmentActivity to ReportActivity. */
public class AssessmentResult implements Serializable {
    private static final long serialVersionUID = 2L;

    public String athleteName = "Athlete";
    public String athleteId = "N/A";
    public String jointType = "ELBOW_FLEXION";

    // Left arm (interior elbow angle: 180 = straight, small = bent)
    public float leftMin;      // peak flexion
    public float leftMax;      // peak extension
    public float leftRom;      // max - min
    public int leftFrames;

    // Right arm
    public float rightMin;
    public float rightMax;
    public float rightRom;
    public int rightFrames;

    public float symmetryDeficitPercent;

    // Anti-cheat
    public float maxTrunkLeanDeg;
    public float leanViolationPercent;
    public boolean cheatDetected;

    public long durationMs;
    public String videoUri;           // null if recording failed / unsupported

    // Classification output (filled by ClassificationRules)
    public boolean inconclusive;
    public boolean micMet;
    public String micStatus = "Pending";
    public String classificationNote = "Pending assessment";

    public long timestamp = System.currentTimeMillis();
}