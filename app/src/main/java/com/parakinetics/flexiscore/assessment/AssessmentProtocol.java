package com.parakinetics.flexiscore.assessment;

import com.parakinetics.flexiscore.data.AssessmentResult;
import com.parakinetics.flexiscore.math.StabilizationDetector;

public class AssessmentProtocol {

    public enum Stage {
        MEASURING_LEFT,
        LEFT_COMPLETE,
        MEASURING_RIGHT,
        ASSESSMENT_FINISHED
    }

    private Stage currentStage = Stage.MEASURING_LEFT;
    private final StabilizationDetector detector = new StabilizationDetector();
    private final AssessmentResult currentResult = new AssessmentResult();

    public boolean processFrameAngle(float angle, long timestampMs, boolean cheatDetected) {
        if (cheatDetected) {
            currentResult.cheatDetected = true;
            return false;
        }

        boolean stabilized = detector.update(angle, timestampMs);

        if (stabilized) {
            if (currentStage == Stage.MEASURING_LEFT) {
                currentResult.leftPeakAngle = detector.getPeakAngle();
                currentStage = Stage.LEFT_COMPLETE;
                detector.reset();
                return true;
            } else if (currentStage == Stage.MEASURING_RIGHT) {
                currentResult.rightPeakAngle = detector.getPeakAngle();
                currentResult.symmetryDeficitPercent = ClassificationRules.calculateBilateralDeficit(
                        currentResult.leftPeakAngle,
                        currentResult.rightPeakAngle
                );
                currentStage = Stage.ASSESSMENT_FINISHED;
                return true;
            }
        }
        return false;
    }

    public void startRightMeasurement() {
        if (currentStage == Stage.LEFT_COMPLETE) {
            currentStage = Stage.MEASURING_RIGHT;
            detector.reset();
        }
    }

    public Stage getCurrentStage() {
        return currentStage;
    }

    public AssessmentResult getFinalResult() {
        return currentResult;
    }
}