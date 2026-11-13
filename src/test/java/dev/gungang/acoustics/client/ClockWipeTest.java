package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClockWipeTest {
    /** Area of the fan, by the shoelace formula over centre → edge points. */
    private static double area(float fraction, float w, float h) {
        var a = ClockWipe.angles(fraction, w, h);
        double sum = 0;
        for (int i = 0; i + 1 < a.length; i++) {
            float[] p = ClockWipe.edge(a[i], w, h), q = ClockWipe.edge(a[i + 1], w, h);
            sum += Math.abs(p[0] * q[1] - p[1] * q[0]) / 2;
        }
        return sum;
    }
    @Test void fullShadeCoversTheWholeSlotAndClearsToNothing() {
        assertEquals(32 * 30, area(1, 32, 30), .01);
        assertEquals(32 * 30 / 2.0, area(.5f, 32, 30), .01);
        assertEquals(32 * 30 / 4.0, area(.25f, 32, 30), 1);
        assertEquals(0, area(0, 32, 30), 1e-6);
    }
    @Test void theRemainingShadeEndsAtTwelveOClockSoItClearsClockwise() {
        var a = ClockWipe.angles(.25f, 32, 30);
        // A quarter left: the shade spans 9 o'clock round to 12 o'clock.
        assertEquals(Math.PI * 1.5, a[0], 1e-9);
        assertEquals(Math.PI * 2, a[a.length - 1], 1e-9);
        float[] top = ClockWipe.edge(0, 32, 30), right = ClockWipe.edge(Math.PI / 2, 32, 30);
        assertArrayEquals(new float[]{0, -15}, top, 1e-5f);
        assertArrayEquals(new float[]{16, 0}, right, 1e-5f);
    }
}
