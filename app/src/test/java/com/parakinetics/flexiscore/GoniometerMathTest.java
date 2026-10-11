package com.parakinetics.flexiscore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.parakinetics.flexiscore.assessment.ClassificationRules;
import com.parakinetics.flexiscore.math.GoniometerMath;
import com.parakinetics.flexiscore.math.StabilizationDetector;

import org.junit.Test;

public class GoniometerMathTest {

    @Test
    public void testPerpendicularRightAngle() {
        // Vertex at (0, 0, 0), Proximal at (0, 1, 0), Distal at (1, 0, 0) -> exact 90 degrees
        float angle = GoniometerMath.calculateAngle3D(
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f
        );
        assertEquals(90.0f, angle, 0.01f);
    }

    @Test
    public void testStraightLineAngle() {
        // 180-degree flat extension
        float angle = GoniometerMath.calculateAngle3D(
                -1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f
        );
        assertEquals(180.0f, angle, 0.01f);
    }

    @Test
    public void testBilateralDeficitCalculation() {
        // Unaffected: 150 deg, Affected: 112.5 deg -> 25% deficit
        float deficit = ClassificationRules.calculateBilateralDeficit(150.0f, 112.5f);
        assertEquals(25.0f, deficit, 0.01f);
    }

    @Test
    public void testSpineLeanTrigger() {
        // Torso tilted by excessive X-offset -> should trigger anti-cheat flag
        boolean cheated = ClassificationRules.isCompensatorySpineLean(
                0.65f, 0.30f,  // Shoulders
                0.50f, 0.70f   // Hips
        );
        assertTrue(cheated);
    }

    @Test
    public void testStabilizationDetectorLock() {
        StabilizationDetector detector = new StabilizationDetector();

        // Feed stable angles (< 2 deg/sec velocity)
        assertFalse(detector.update(140.0f, 1000L)); // Initial frame (no velocity yet)
        assertFalse(detector.update(140.2f, 1500L)); // Timer starts here at 1500ms
        assertFalse(detector.update(140.4f, 2000L)); // 500ms elapsed

        // 1500ms + 1200ms required threshold = 2700ms. We pass 2750ms to cross it.
        boolean stabilized = detector.update(140.5f, 2750L);

        assertTrue("Should be stabilized after 1200ms lock window", stabilized);
        assertEquals(140.5f, detector.getPeakAngle(), 0.01f);
    }
}