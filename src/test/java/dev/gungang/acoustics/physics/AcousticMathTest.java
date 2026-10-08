package dev.gungang.acoustics.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcousticMathTest {
    @Test void inverseDistanceHasNoNearFieldBoostAndFadesAtOriginalRange() {
        assertEquals(1, AcousticMath.distanceGain(0, 64, 2, 1), 1e-12);
        assertEquals(1, AcousticMath.distanceGain(2, 64, 2, 1), 1e-12);
        assertEquals(0.5, AcousticMath.distanceGain(4, 64, 2, 1), 1e-12);
        assertEquals(0.25, AcousticMath.distanceGain(8, 64, 2, 1), 1e-12);
        assertEquals(0, AcousticMath.distanceGain(64, 64, 2, 1));
        assertEquals(0, AcousticMath.distanceGain(80, 64, 2, 1));
        double previous = 1;
        for (double distance = 0; distance <= 64; distance += 0.01) {
            double current = AcousticMath.distanceGain(distance, 64, 2, 1);
            assertTrue(current >= 0 && current <= previous + 1e-12);
            previous = current;
        }
    }

    @Test void independentWallsMultiplyPressureTransmission() {
        assertEquals(0.1, AcousticMath.amplitude(20), 1e-12);
        assertEquals(AcousticMath.amplitude(18) * AcousticMath.amplitude(7), AcousticMath.amplitude(25), 1e-12);
        assertTrue(AcousticMath.amplitude(36) < AcousticMath.amplitude(18));
    }

    @Test void smoothingIsIndependentOfUpdateRateAndNeverOvershoots() {
        double once = AcousticMath.smooth(1, 0.1, 0.1, 0.2);
        double twice = AcousticMath.smooth(AcousticMath.smooth(1, 0.1, 0.05, 0.2), 0.1, 0.05, 0.2);
        assertEquals(once, twice, 1e-12);
        assertTrue(once > 0.1 && once < 1);
        assertEquals(0.1, AcousticMath.smooth(1, 0.1, 100, 0.2), 1e-12);
    }

    @Test void slabIntersectionMeasuresRealWallThickness() {
        double[] hit = AcousticMath.intersect(new double[]{-2, 0.5, 0.5}, new double[]{2, 0.5, 0.5},
                new double[]{0, 0, 0}, new double[]{0.125, 1, 1});
        assertNotNull(hit);
        assertEquals(0.125, (hit[1] - hit[0]) * 4, 1e-12);
    }

    @Test void diagonalRayHasLongerMaterialPath() {
        double[] hit = AcousticMath.intersect(new double[]{-1, -1, -1}, new double[]{2, 2, 2},
                new double[]{0, 0, 0}, new double[]{1, 1, 1});
        assertNotNull(hit);
        assertEquals(Math.sqrt(3), (hit[1] - hit[0]) * Math.sqrt(27), 1e-12);
    }

    @Test void touchingOrMissingASurfaceDoesNotCountAsTransmission() {
        double[] min = {0, 0, 0}, max = {1, 1, 1};
        assertNull(AcousticMath.intersect(new double[]{-1, 1, 0.5}, new double[]{2, 1, 0.5}, min, max));
        assertNull(AcousticMath.intersect(new double[]{-1, 2, 0.5}, new double[]{2, 2, 0.5}, min, max));
        assertNull(AcousticMath.intersect(new double[]{-1, 0.5, 0.5}, new double[]{0, 0.5, 0.5}, min, max));
    }

    @Test void reverseDirectionAndStartingInsideAreHandled() {
        double[] min = {0, 0, 0}, max = {1, 1, 1};
        double[] forward = AcousticMath.intersect(new double[]{-2, 0.5, 0.5}, new double[]{2, 0.5, 0.5}, min, max);
        double[] reverse = AcousticMath.intersect(new double[]{2, 0.5, 0.5}, new double[]{-2, 0.5, 0.5}, min, max);
        assertEquals(forward[1] - forward[0], reverse[1] - reverse[0], 1e-12);
        double[] inside = AcousticMath.intersect(new double[]{0.5, 0.5, 0.5}, new double[]{2, 0.5, 0.5}, min, max);
        assertEquals(0.5, (inside[1] - inside[0]) * 1.5, 1e-12);
    }
}
