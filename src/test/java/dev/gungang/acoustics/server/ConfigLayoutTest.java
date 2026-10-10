package dev.gungang.acoustics.server;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigLayoutTest {
    @TempDir Path root;
    @Test void readsOnlyServerMapsAndNeverImportsLegacyFiles() throws Exception {
        Files.createDirectories(root.resolve("maps"));
        Files.createDirectories(root.resolve("server/maps"));
        Files.writeString(root.resolve("maps/broken.json"),"invalid legacy config must be ignored");
        var json=ServerFiles.GSON.toJsonTree(ArenaMap.example()).getAsJsonObject();
        json.addProperty("id","new_arena");json.addProperty("enabled",true);
        Files.writeString(root.resolve("server/maps/new.json"),ServerFiles.GSON.toJson(json));
        var maps=ServerFiles.loadMaps(root);
        assertEquals(1,maps.size());assertEquals("new_arena",maps.get(0).id());
        assertFalse(Files.exists(root.resolve("server/maps/broken.json")));
        Files.delete(root.resolve("server/maps/new.json"));
        assertTrue(ServerFiles.loadMaps(root).isEmpty());
        assertFalse(Files.exists(root.resolve("server/maps/example.json")));
    }
    @Test void firstRunCreatesOnlyDisabledExampleInServerMaps() throws Exception {
        assertTrue(ServerFiles.loadMaps(root).isEmpty());
        assertTrue(Files.exists(root.resolve("server/maps/example.json")));
        assertFalse(Files.exists(root.resolve("maps")));
    }
}
