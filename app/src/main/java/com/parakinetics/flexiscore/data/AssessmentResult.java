package com.parakinetics.flexiscore.data;

import java.io.Serializable;

public class AssessmentResult implements Serializable {
    public String athleteId = "DEMO-ATHLETE-01";
    public String jointType = "ELBOW_FLEXION";
    public float leftPeakAngle = 0.0f;
    public float rightPeakAngle = 0.0f;
    public float symmetryDeficitPercent = 0.0f;
    public boolean cheatDetected = false;
    public String classificationNote = "Pending Assessment";
    public long timestamp = System.currentTimeMillis();
}