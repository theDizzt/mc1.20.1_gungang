package dev.gungang.acoustics.server;

import com.google.gson.JsonParser;
import dev.gungang.acoustics.AcousticConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerRulesTest {
    @Test void readyRequiresBothTeamsAndIgnoresSpectators() {
        var alpha = new LobbyRules.Readiness("ALPHA", true);
        var bravo = new LobbyRules.Readiness("BRAVO", true);
        assertTrue(LobbyRules.allReady(List.of(alpha, bravo, new LobbyRules.Readiness("SPECTATOR", false))));
        assertFalse(LobbyRules.allReady(List.of(alpha)));
        assertFalse(LobbyRules.allReady(List.of(alpha, bravo, new LobbyRules.Readiness("UNASSIGNED", false))));
        assertFalse(LobbyRules.allReady(List.of(alpha, new LobbyRules.Readiness("BRAVO", false))));
    }
    @Test void mapsRoundTripAndValidateAreas() {
        var example = ArenaMap.example();
        assertEquals(example, ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(example), ArenaMap.class).validate());
        assertTrue(example.alphaArea().contains(0.5,65,0.5));
        assertFalse(example.alphaArea().contains(6,65,0.5));
        assertThrows(IllegalArgumentException.class, () -> new ArenaMap.Area(0,0,0,1,1,1).validate(example.alphaSpawn()));
        assertThrows(IllegalArgumentException.class, () -> new ArenaMap.Spawn(Double.NaN,65,0,0,0).validate());
    }
    @Test void serverOverrideValidatesAndDoesNotSetLocalConfig() {
        var values = AcousticPolicy.defaults();
        values.addProperty("footstepVolume", 175);
        try {
            AcousticPolicy.receive(values);
            assertEquals(175, AcousticPolicy.get(AcousticConfig.FOOTSTEP_VOLUME));
            assertTrue(AcousticPolicy.locked(AcousticConfig.FOOTSTEP_VOLUME));
            assertFalse(AcousticPolicy.locked(AcousticConfig.SHOOTER_HUD));
        } finally { AcousticPolicy.clear(); }
        assertFalse(AcousticPolicy.locked(AcousticConfig.FOOTSTEP_VOLUME));
        assertThrows(IllegalArgumentException.class, () -> AcousticPolicy.parse(JsonParser.parseString("{\"weaponVolume\":301}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> AcousticPolicy.parse(JsonParser.parseString("{\"updatesPerTick\":1.5}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> AcousticPolicy.parse(JsonParser.parseString("{\"shooterHud\":false}").getAsJsonObject()));
    }
}
