package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoundVolumeTest {
    @Test void matchesFootstepTokensAcrossSurfaces() {
        assertEquals(0.5, SoundVolume.scale("block.stone.step", false, 50, 200));
        assertEquals(0.5, SoundVolume.scale("entity.player.footsteps", false, 50, 200));
        assertEquals(1, SoundVolume.scale("block.stone.break", false, 50, 200));
        assertEquals(1, SoundVolume.scale("doorstep.ambient", false, 50, 200));
    }
    @Test void recognizesTaczAndVanillaWeaponsWithoutChangingOtherEffects() {
        assertEquals(2, SoundVolume.scale("custom_event", true, 50, 200));
        for (String path : new String[]{"entity.arrow.shoot", "item.crossbow.loading_start", "entity.player.attack.strong", "rifle/reload"})
            assertEquals(2, SoundVolume.scale(path, false, 50, 200));
        assertEquals(1, SoundVolume.scale("entity.player.hurt", false, 50, 200));
    }
    @Test void supportsMuteOriginalAndBoostWithoutChangingAcousticBypass() {
        assertEquals(0, SoundVolume.scale("block.grass.step", false, 0, 100));
        assertEquals(1, SoundVolume.scale("gun", true, 100, 100));
        assertEquals(3, SoundVolume.scale("gun", true, 100, 300));
        var frame = AcousticFrame.volumeOnly(2);
        assertFalse(frame.enabled());
        assertFalse(frame.manageDistance());
        assertEquals(2, frame.volumeScale());
        assertEquals(1, AcousticFrame.BYPASS.volumeScale());
    }
}
