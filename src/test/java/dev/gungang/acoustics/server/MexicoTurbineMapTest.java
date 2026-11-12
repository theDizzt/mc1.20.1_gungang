package dev.gungang.acoustics.server;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 13 멕시코 - 터빈: team deathmatch, prop hunt and control on the centre point's glass and wool floor (y 4). */
class MexicoTurbineMapTest {
    @Test void mapValidatesWithItsThreeModes() throws Exception {
        var map = new Gson().fromJson(Files.readString(Path.of("server_resource/config/gungang/server/maps/mexico-turbine.json")), ArenaMap.class).validate();
        assertEquals("13", map.id());
        assertEquals("2026-11-16", map.releaseDate());
        assertTrue(map.enabled());
        assertEquals(java.util.List.of("CONTROL", "DEATHMATCH"), LobbyRules.modes(map.normandy() != null, map.capture() != null, map.bomb() != null));
        assertTrue(PropHuntRules.supported(map));
        // Spawns stand inside their team areas (block bounds as given, the far edge included).
        assertTrue(map.alphaArea().contains(map.alphaSpawn().x(), map.alphaSpawn().y(), map.alphaSpawn().z()));
        assertTrue(map.bravoArea().contains(map.bravoSpawn().x(), map.bravoSpawn().y(), map.bravoSpawn().z()));
        assertTrue(map.alphaArea().contains(-305.5, 10, -191.5));
        // The point: standing on the y 4 floor anywhere over (-299, -157) ~ (-291, -139) counts.
        var point = map.capture().area();
        assertTrue(point.contains(-298.5, 5, -156.5));
        assertTrue(point.contains(-290.5, 5, -138.5));
        assertFalse(point.contains(-289.5, 5, -150));
        assertEquals(new ArenaMap.Area(-329, 2, -217, -261, 22, -79).minX(), map.previewArea().minX());
    }
}
