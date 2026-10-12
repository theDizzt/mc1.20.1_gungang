package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeamOptionsTest {
    @Test void missingFieldsUseDefaults() {
        var options=ServerFiles.GSON.fromJson("{}",TeamOptions.class);
        assertEquals(TeamOptions.defaults(),options);
        assertEquals(5,options.respawnSeconds());
    }
    @Test void rgbAndDurationSurviveSnapshotSerialization() {
        var options=new TeamOptions("#12AB34","#fedcba",8);
        assertEquals(options,ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(options),TeamOptions.class));
        assertEquals(0xFF12AB34,options.color("ALPHA"));
        assertEquals(0xFFFEDCBA,options.color("BRAVO"));
    }
    @Test void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,()->new TeamOptions("red","#5555FF",5));
        assertThrows(IllegalArgumentException.class,()->new TeamOptions(null,null,0));
        assertThrows(IllegalArgumentException.class,()->new TeamOptions(null,null,301));
    }
}
