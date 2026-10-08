package dev.gungang.acoustics.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigInputTest {
    @Test void acceptsRangeBoundariesAndRejectsNonFiniteOrOutOfRangeNumbers() {
        assertEquals(0.25, ConfigInput.decimal(" 0.25 ", 0.25, 16));
        assertEquals(16, ConfigInput.decimal("16", 0.25, 16));
        for (String invalid : List.of("", "NaN", "Infinity", "-1", "16.01", "abc")) {
            assertThrows(IllegalArgumentException.class, () -> ConfigInput.decimal(invalid, 0.25, 16));
        }
    }
    @Test void rejectsFractionalAndOverflowingIntegerOptions() {
        assertEquals(128, ConfigInput.integer("128", 1, 128));
        for (String invalid : List.of("1.5", "0", "129", "9999999999999999")) {
            assertThrows(IllegalArgumentException.class, () -> ConfigInput.integer(invalid, 1, 128));
        }
    }
    @Test void parsesListsAndAllowsExplicitClearing() {
        assertEquals(List.of(), ConfigInput.entries("  ", false));
        assertEquals(List.of("tacz:*", "minecraft:entity.player.hurt"), ConfigInput.entries(" tacz:*, minecraft:entity.player.hurt ", false));
        assertEquals(List.of("minecraft:glass=7.5", "minecraft:stone=120"), ConfigInput.entries("minecraft:glass=7.5,minecraft:stone=120", true));
    }
    @Test void rejectsMalformedListsInsteadOfSilentlyDroppingEntries() {
        for (String invalid : List.of("tacz:*,", "tacz", "TACZ:gun", "tacz:gu*")) {
            assertThrows(IllegalArgumentException.class, () -> ConfigInput.entries(invalid, false));
        }
        for (String invalid : List.of("minecraft:stone=121", "minecraft:stone=-1", "minecraft:stone=NaN", "minecraft:*=18")) {
            assertThrows(IllegalArgumentException.class, () -> ConfigInput.entries(invalid, true));
        }
    }
}
