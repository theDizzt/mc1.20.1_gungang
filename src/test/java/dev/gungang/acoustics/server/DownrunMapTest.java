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
    @Test void suppliedMapIsValidAndDeathmatchOnly() throws Exception {
        var map = new Gson().fromJson(Files.readString(Path.of("server_resource/config/gungang/server/maps/brazil-downrun.json")), ArenaMap.class).validate();
        assertTrue(map.enabled());
        assertTrue(DeathmatchRules.exclusive(map));
        assertEquals(238, map.alphaArea().minX());
        assertEquals(405, map.bravoArea().maxX());
        var arena = DeathmatchRules.defaults().arena(map).validate();
        assertEquals(map.alphaSpawn(), arena.alphaSpawn());
        assertEquals(map.bravoSpawn(), arena.bravoSpawn());
        assertNull(arena.capture());
        assertNull(arena.bomb());
    }
}
