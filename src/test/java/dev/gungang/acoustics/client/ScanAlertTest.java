package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScanAlertTest {
    @Test void vignetteLeavesTheMiddleClearAndDeepensTowardsTheCorners() {
        assertEquals(0, ScanAlert.edge(.5f, .5f, 1));
        // The crosshair area and most of the view stay untinted.
        assertEquals(0, ScanAlert.edge(.75f, .5f, 1));
        assertEquals(0, ScanAlert.edge(.5f, .2f, 1));
        int midEdge = ScanAlert.edge(1, .5f, 1), corner = ScanAlert.edge(1, 1, 1);
        assertTrue(midEdge > 0 && midEdge < corner);
        assertEquals((int) Math.round(ScanAlert.EDGE_ALPHA), corner, 1);
        // Weaker strength (between pulses, fading out) scales it down.
        assertTrue(ScanAlert.edge(1, 1, .45f) < corner);
        assertEquals(0, ScanAlert.edge(1, 1, 0));
    }
}
