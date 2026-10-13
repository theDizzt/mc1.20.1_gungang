package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudHealthTest {
    @Test void includesTemporaryAndRecoveredHealthWithoutLifeSupportSentinel() {
        assertEquals("30",HudHealth.label(.001f,100,30));
        assertEquals("35",HudHealth.label(5.001f,100,30));
        assertEquals("26",HudHealth.label(20,100,5.5f));
        assertEquals("1",HudHealth.label(.1f,100,0));
        assertEquals("0",HudHealth.label(0,100,0));
    }
    @Test void displaysHealthPointsAndModifiedMaximumHealth() {
        assertEquals("20", HudHealth.label(20, 20));
        assertEquals("8", HudHealth.label(7.5f, 40));
        assertEquals("1", HudHealth.label(0.1f, 20));
        assertEquals("0", HudHealth.label(0, 20));
        assertEquals(0.1875f, HudHealth.fraction(7.5f, 40));
    }
    @Test void keepsTheBarInsideItsBounds() {
        assertEquals(0, HudHealth.fraction(-1, 20));
        assertEquals(1, HudHealth.fraction(25, 20));
        assertEquals(0, HudHealth.fraction(20, 0));
        assertEquals(0, HudHealth.fraction(Float.NaN, 20));
        assertEquals("0", HudHealth.number(Float.NaN));
    }
}
