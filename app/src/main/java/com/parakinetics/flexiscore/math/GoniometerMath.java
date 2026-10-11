package com.parakinetics.flexiscore.math;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

public class GoniometerMath {

    /**
     * Calculates the 3D joint angle formed by three landmarks using Euclidean vector dot products.
     *
     * @param proximal P_proximal point (e.g., Shoulder)
     * @param vertex   P_joint pivot center (e.g., Elbow)
     * @param distal   P_distal point (e.g., Wrist)
     * @return Joint angle in degrees [0.0, 180.0]
     */
    public static float calculateAngle(NormalizedLandmark proximal, NormalizedLandmark vertex, NormalizedLandmark distal) {
        return calculateAngle3D(
                proximal.x(), proximal.y(), proximal.z(),
                vertex.x(), vertex.y(), vertex.z(),
                distal.x(), distal.y(), distal.z()
        );
    }

    /**
     * Pure coordinate calculation (used by MediaPipe calls and local unit tests).
     */
    public static float calculateAngle3D(
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz) {

        // Vector u = P_proximal - P_joint
        float ux = ax - bx;
        float uy = ay - by;
        float uz = az - bz;

        // Vector v = P_distal - P_joint
        float vx = cx - bx;
        float vy = cy - by;
        float vz = cz - bz;

        // Dot product u . v
        float dotProduct = (ux * vx) + (uy * vy) + (uz * vz);

        // Vector magnitudes ||u|| and ||v||
        double magnitudeU = Math.sqrt((ux * ux) + (uy * uy) + (uz * uz));
        double magnitudeV = Math.sqrt((vx * vx) + (vy * vy) + (vz * vz));

        if (magnitudeU == 0 || magnitudeV == 0) {
            return 0.0f;
        }

        // Clamp cosine value to prevent numerical precision errors outside [-1.0, 1.0]
        double cosTheta = dotProduct / (magnitudeU * magnitudeV);
        cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta));

        return (float) Math.toDegrees(Math.acos(cosTheta));
    }
}