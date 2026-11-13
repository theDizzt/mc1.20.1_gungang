package dev.gungang.acoustics.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatusIconsTest {
    @Test void everyGunGangIconHasItsTexture() {
        for (var icon : StatusIcons.Icon.values()) {
            String path = icon.texture.getPath();
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/gungang", path)), path);
        }
    }
    @Test void onlyEndingEffectsBlink() {
        assertEquals(1, StatusIcons.blink(-1, 7, 0));
        assertEquals(1, StatusIcons.blink(StatusIcons.ENDING + 1, 7, 0));
        float low = 1, high = 0;
        for (int t = 0; t < 10; t++) { float a = StatusIcons.blink(40, t, 0); low = Math.min(low, a); high = Math.max(high, a); }
        assertTrue(low < .6f && high > .95f, low + " .. " + high);
    }
    @Test void auxBarBackgroundIsSeventyPercentOpaque() {
        assertEquals(0xB3, AuxBar.BACKGROUND_ALPHA);
        assertEquals(Math.round(255 * .7f), AuxBar.BACKGROUND_ALPHA, 1);
        assertEquals(0xB3141511, AuxBar.translucent(0xFF141511));
    }
}
