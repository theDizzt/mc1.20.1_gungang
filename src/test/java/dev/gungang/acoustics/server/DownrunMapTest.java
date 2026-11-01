package dev.gungang.acoustics.server;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DownrunMapTest {
    @Test void shippedMapIdsMatchSelectionOrder() throws Exception {
        var names=java.util.List.of("ilios","lijang","normandy","busan","brazil-downrun","italy");
        for(int i=0;i<names.size();i++){
            var map=new Gson().fromJson(Files.readString(Path.of("server_resource/config/gungang/server/maps/"+names.get(i)+".json")),ArenaMap.class).validate();
            assertEquals("0"+(i+1),map.id());
            assertNotNull(map.previewArea());
        }
    }
    @Test void italyAllowsFlatSitesAndTeamDeathmatch() throws Exception {
        var map=new Gson().fromJson(Files.readString(Path.of("server_resource/config/gungang/server/maps/italy.json")),ArenaMap.class).validate();
        var b=map.bomb().areaB();assertEquals(12,b.minY());assertEquals(12,b.maxY());
        assertTrue(b.contains(-75,12,-423));assertTrue(b.contains(-54,12,-400));
        assertFalse(b.contains(-53,12,-400));assertFalse(b.contains(-60,13,-410));
        assertEquals(13,map.bomb().pointB().y());
        assertEquals(map.bomb().attackSpawn(),DeathmatchRules.defaults().arena(map).alphaSpawn());
    }
    @Test void suppliedMapOffersFreeForAllAndTeamDeathmatch() throws Exception {
        var map = new Gson().fromJson(Files.readString(Path.of("server_resource/config/gungang/server/maps/brazil-downrun.json")), ArenaMap.class).validate();
        assertTrue(map.enabled());
        assertEquals(23, map.freeForAll().spawns().size());
        assertTrue(DeathmatchRules.supported(map));
        assertFalse(DeathmatchRules.exclusive(map));
        assertTrue(PropHuntRules.supported(map));
        assertNull(DeathmatchRules.defaults().arena(map).freeForAll());
        assertEquals(238, map.alphaArea().minX());
        assertEquals(405, map.bravoArea().maxX());
        var arena = DeathmatchRules.defaults().arena(map).validate();
        assertEquals(map.alphaSpawn(), arena.alphaSpawn());
        assertEquals(map.bravoSpawn(), arena.bravoSpawn());
        assertNull(arena.capture());
        assertNull(arena.bomb());
    }
    @Test void kingsRowEscortUsesItsOwnBasesPerCheckpoint() throws Exception {
        var map=new Gson().fromJson(Files.readString(Path.of("server_resource/config/gungang/server/maps/kings-row.json")),ArenaMap.class).validate();
        assertTrue(map.enabled());assertEquals(300,map.timeLimitSeconds());assertEquals(120,map.escort().checkpointBonusSeconds());
        // Team deathmatch keeps the south base; escort defenders start at the first defense point.
        assertTrue(DeathmatchRules.defaults().arena(map).bravoArea().contains(216.5,5,-412.5));
        var escort=map.escortStart();
        assertTrue(escort.bravoArea().contains(305,5,-278));assertTrue(escort.alphaArea().contains(200,5,-190));
        // Checkpoint 3 adds no entry: attackers keep spawning at the checkpoint 2 base to the end.
        assertEquals(2,map.escort().respawns().size());
        assertTrue(map.escort().respawns().get(0).alphaArea().contains(292,5,-195));
        assertEquals(2,map.escort().respawns().get(1).checkpoint());assertTrue(map.escort().respawns().get(1).alphaArea().contains(230,5,-283));
    }
}
