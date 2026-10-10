package dev.gungang.acoustics.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FpsAcousticsTest {
    @Test void headVisibleAboveAHillReleasesOcclusionEvenWithHiddenFeet() {
        assertEquals(0, BodyVisibility.leastLoss(point -> point[1] > 0.85 ? 0 : 24));
    }
    @Test void aSliverOfAnExposedShoulderReleasesOcclusion() {
        assertEquals(0, BodyVisibility.leastLoss(point -> point[0] > 0.90 && point[1] > 0.6 ? 0 : 30));
    }
    @Test void aFullyHiddenBodyUsesItsLeastObstructedPath() {
        assertEquals(6, BodyVisibility.leastLoss(point -> point[1] > 0.85 ? 6 : 18));
        assertEquals(18, BodyVisibility.leastLoss(point -> 18));
    }
    @Test void moreWallsRemainDistinguishableAndApproachTheSixteenPercentFloor() {
        double one = AcousticMath.wallGain(6, 16);
        double two = AcousticMath.wallGain(12, 16);
        double three = AcousticMath.wallGain(18, 16);
        assertTrue(one > 0.55 && one < 0.60);
        assertTrue(one > two && two > three && three > 0.16);
        assertEquals(1, AcousticMath.wallGain(0, 16));
        assertEquals(0.16, AcousticMath.wallGain(10000, 16), 1e-10);
    }
    @Test void floorDoesNotDefeatDistanceOrMuteControls() {
        assertEquals(0, 0 * AcousticMath.wallGain(40, 16));
        assertEquals(0, AcousticMath.distanceGain(64, 64, 2, 1) * AcousticMath.wallGain(40, 16));
        assertEquals(1, AcousticMath.wallGain(40, 100));
        assertEquals(AcousticMath.amplitude(40), AcousticMath.wallGain(40, 0));
    }
}
