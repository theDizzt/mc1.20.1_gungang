package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbsorptionBarTest {
    @Test void fullHealthPlusAbsorptionSharesOneBar() {
        var layers = HudHealthBars.compose(1, 1, 4f / 24, 4f / 24);
        assertEquals(20f / 24, layers.health(), 0.00001);
        assertEquals(1, layers.absorptionEnd(), 0.00001);
        assertEquals(0xFFFFD36B, layers.colorAt(0.95f, 0xFFFFFFFF));
    }
    @Test void absorptionDamageKeepsGoldenTrailAndMissingHealthRemainsEmpty() {
        var layers = HudHealthBars.compose(0.5f, 0.5f, 2f / 22, 4f / 24);
        assertEquals(10f / 24, layers.health(), 0.00001);
        assertEquals(12f / 24, layers.absorptionEnd(), 0.00001);
        assertEquals(14f / 24, layers.absorptionTrailEnd(), 0.00001);
        assertEquals(0xFFB9903F, layers.colorAt(0.55f, 0xFFFFFFFF));
        assertEquals(0x8029353E, layers.colorAt(0.8f, 0xFFFFFFFF));
    }
    @Test void withoutAbsorptionPreservesOriginalHealthAndDamageTrail() {
        var layers = HudHealthBars.compose(0.4f, 0.7f, 0, 0);
        assertEquals(0.4f, layers.health());
        assertEquals(0.7f, layers.healthTrail());
        assertEquals(0xFFD6A06E, layers.colorAt(0.5f, 0xFFFFFFFF));
    }
}
