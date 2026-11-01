package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobbySnapshotTest {
    @Test void legacySnapshotSuppliesOptionsToTheScreenItself(){
        var state=ServerFiles.GSON.fromJson("{\"enabled\":true,\"phase\":\"LOBBY\"}",LobbySnapshot.class);
        assertEquals(TeamOptions.defaults(),state.options());
        assertEquals(GameOptions.defaults(),state.options().game());
        assertEquals("DEATHMATCH",state.selectedMode());
        assertTrue(state.maps().isEmpty());assertTrue(state.members().isEmpty());
    }
    @Test void explicitNullsCannotReachOptionControls(){
        var state=ServerFiles.GSON.fromJson("{\"options\":null,\"selectedMode\":null,\"phase\":null,\"maps\":null,\"members\":null}",LobbySnapshot.class);
        assertNotNull(state.options());assertNotNull(state.selectedMode());
        assertNotEquals("LOBBY",state.phase());
        assertNotNull(state.you());assertNotNull(state.selectedMap());
    }
    @Test void invalidOptionsAreRejectedRatherThanBecomingRenderableState(){
        assertThrows(RuntimeException.class,()->ServerFiles.GSON.fromJson("{\"options\":{\"game\":{\"minutes\":1441}}}",LobbySnapshot.class));
    }
}
