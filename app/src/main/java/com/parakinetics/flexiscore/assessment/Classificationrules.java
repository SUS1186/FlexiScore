package com.parakinetics.flexiscore.assessment;

import com.parakinetics.flexiscore.data.AssessmentResult;

import java.util.Locale;

/**
 * Screening rules for an ELBOW FLEXION/EXTENSION test.
 *
 * Range of motion (ROM) = peak extension - peak flexion, measured per arm.
 * A healthy elbow moves through roughly 130-150 degrees.
 *
 * NOTE: these thresholds are screening heuristics for this project. The result is an
 * INDICATIVE class only, never an official IPC classification.
 * (IPC athletics arm-impairment classes: T/F45 = both arms affected, T/F46 = one arm affected.)
 */
public final class Classificationrules {

    public static final float NORMAL_ROM_DEG = 120f;        // below this the arm is "restricted"
    public static final float SEVERE_ROM_DEG = 60f;         // below this the restriction is "severe"
    public static final float ASYMMETRY_LIMIT_PERCENT = 15f;
    public static final int MIN_VALID_FRAMES = 45;          // each arm must be tracked for ~2 s
    public static final float LEAN_LIMIT_DEG = 15f;         // trunk lean counted as compensation
    public static final float LEAN_VIOLATION_LIMIT_PERCENT = 25f;

    private Classificationrules() {}

    /** Reads leftMin/leftMax/rightMin/rightMax/frames/lean and fills in everything else. */
    public static void evaluate(AssessmentResult r) {
        r.leftRom = r.leftFrames > 0 ? Math.max(0f, r.leftMax - r.leftMin) : 0f;
        r.rightRom = r.rightFrames > 0 ? Math.max(0f, r.rightMax - r.rightMin) : 0f;

        float bigger = Math.max(r.leftRom, r.rightRom);
        r.symmetryDeficitPercent = bigger > 0f ? Math.abs(r.leftRom - r.rightRom) / bigger * 100f : 0f;

        r.cheatDetected = r.leanViolationPercent > LEAN_VIOLATION_LIMIT_PERCENT;

        r.inconclusive = r.leftFrames < MIN_VALID_FRAMES || r.rightFrames < MIN_VALID_FRAMES;
        if (r.inconclusive) {
            r.micMet = false;
            r.micStatus = "INCONCLUSIVE - arm not tracked long enough";
            String which = r.leftFrames < MIN_VALID_FRAMES && r.rightFrames < MIN_VALID_FRAMES ? "Both arms were"
                    : r.leftFrames < MIN_VALID_FRAMES ? "The left arm was" : "The right arm was";
            r.classificationNote = which + " not visible for enough of the test. "
                    + "Repeat it with both arms fully in view, 2-3 m from the camera, in good light.";
            appendCheat(r);
            return;
        }

        boolean leftLimited = r.leftRom < NORMAL_ROM_DEG;
        boolean rightLimited = r.rightRom < NORMAL_ROM_DEG;
        boolean severe = Math.min(r.leftRom, r.rightRom) < SEVERE_ROM_DEG;

        if (leftLimited && rightLimited) {
            r.micMet = true;
            r.micStatus = "MIC MET - both elbows restricted";
            r.classificationNote = "Indicative class: T45 / F45 (both arms affected)"
                    + (severe ? " - severe restriction in at least one arm." : ".");
        } else if (leftLimited || rightLimited) {
            String side = leftLimited ? "Left" : "Right";
            float rom = leftLimited ? r.leftRom : r.rightRom;
            r.micMet = true;
            r.micStatus = "MIC MET - " + side.toLowerCase(Locale.US) + " elbow restricted";
            r.classificationNote = String.format(Locale.US,
                    "Indicative class: T46 / F46 (one arm affected). %s arm ROM %.0f deg vs normal >= %.0f deg%s",
                    side, rom, NORMAL_ROM_DEG, rom < SEVERE_ROM_DEG ? " - severe restriction." : ".");
        } else if (r.symmetryDeficitPercent > ASYMMETRY_LIMIT_PERCENT) {
            r.micMet = false;
            r.micStatus = "BORDERLINE - asymmetry noted";
            r.classificationNote = String.format(Locale.US,
                    "Both arms reach a normal range, but they differ by %.0f%%. Below the minimum "
                            + "impairment criteria - retest recommended.", r.symmetryDeficitPercent);
        } else {
            r.micMet = false;
            r.micStatus = "MIC NOT MET - normal range of motion";
            r.classificationNote = "Both elbows show full, symmetric movement. "
                    + "Not eligible for an arm-impairment class (screening result).";
        }
        appendCheat(r);
    }

    private static void appendCheat(AssessmentResult r) {
        if (r.cheatDetected) {
            r.classificationNote += String.format(Locale.US,
                    "  WARNING: trunk compensation detected (max lean %.0f deg) - repeat the test "
                            + "with an upright torso.", r.maxTrunkLeanDeg);
        }
    }
}