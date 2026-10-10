package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthBarAnimationTest {
    @Test void easesDamageAndHealingAndFinishesExactly() {
        var bar = new HealthBarAnimation();
        assertEquals(1f, bar.update(20, 20, 0));
        assertEquals(1f, bar.update(10, 20, 0.1));
        assertEquals(0.75f, bar.update(10, 20, 0.16), 0.0001f);
        assertEquals(0.5f, bar.update(10, 20, 0.36));
        assertEquals(0.5f, bar.update(20, 20, 0.4));
        assertEquals(0.75f, bar.update(20, 20, 0.575), 0.0001f);
        assertEquals(1f, bar.update(20, 20, 0.76));
    }

    @Test void interruptedAnimationStartsAtVisibleValue() {
        var bar = new HealthBarAnimation();
        bar.update(20, 20, 0);
        bar.update(0, 20, 0.1);
        float mid = bar.update(0, 20, 0.2);
        assertEquals(mid, bar.update(20, 20, 0.2));
        assertTrue(bar.update(20, 20, 0.3) > mid);
        assertEquals(1f, bar.update(20, 20, 0.56));
    }

    @Test void independentOfFrameRateAndNoProgressWhenTimeIsPaused() {
        var slow = new HealthBarAnimation();
        var fast = new HealthBarAnimation();
        slow.update(20, 20, 0);
        fast.update(20, 20, 0);
        slow.update(0, 20, 0.1);
        fast.update(0, 20, 0.1);
        for (int i = 1; i < 20; i++) fast.update(0, 20, 0.1 + i * 0.005);
        float expected = slow.update(0, 20, 0.2);
        assertEquals(expected, fast.update(0, 20, 0.2));
        assertEquals(expected, fast.update(0, 20, 0.2));
    }

    @Test void resetsForMaximumChangesClockResetAndLongAbsence() {
        var bar = new HealthBarAnimation();
        bar.update(20, 20, 10);
        assertEquals(0.5f, bar.update(20, 40, 10.1));
        assertEquals(0.25f, bar.update(10, 40, 0));
        assertEquals(1f, bar.update(40, 40, 2));
    }
    @Test void damageTrailHoldsThenDrainsAndHealingClearsIt() {
        var bar = new HealthBarAnimation();
        bar.update(20, 20, 0);
        bar.update(10, 20, 0.1);
        bar.update(10, 20, 0.18);
        assertEquals(1f, bar.trail(0.18));
        bar.update(10, 20, 0.3);
        assertTrue(bar.trail(0.3) > 0.5f);
        bar.update(10, 20, 0.95);
        assertEquals(0.5f, bar.trail(0.95));
        bar.update(0, 20, 1);
        float value = bar.update(20, 20, 1.1);
        assertEquals(value, bar.trail(1.1));
    }
}
