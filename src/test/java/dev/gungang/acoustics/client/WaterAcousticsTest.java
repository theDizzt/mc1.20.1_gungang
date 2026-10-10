package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterAcousticsTest {
    @Test void dryPathIsUnchanged() {
        var dry = WaterAcoustics.transmission(0, 0, false);
        assertEquals(1, dry.gain());
        assertEquals(1, dry.highFrequency());
    }
    @Test void boundariesAndWaterPathAttenuateAndSubmersionFilters() {
        var near = WaterAcoustics.transmission(2, 0, true);
        var far = WaterAcoustics.transmission(40, 0, true);
        var boundary = WaterAcoustics.transmission(2, 1, true);
        assertTrue(far.gain() < near.gain());
        assertTrue(far.highFrequency() < near.highFrequency());
        assertTrue(boundary.gain() < near.gain());
        assertTrue(boundary.highFrequency() < near.highFrequency());
        assertTrue(near.highFrequency() < 0.5);
        assertTrue(boundary.gain() > 0);
    }
}
