package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LobbyRandomTest {
    @Test void modesMatchLobbyButtons() {
        assertEquals(List.of("NORMANDY"), LobbyRules.modes(true, true, true));
        assertEquals(List.of("DEATHMATCH"), LobbyRules.modes(false, false, false));
        assertEquals(List.of("BOMB", "DEATHMATCH"), LobbyRules.modes(false, true, true));
        assertEquals(List.of("CONTROL", "DEATHMATCH"), LobbyRules.modes(false, true, false));
    }
    @Test void pickAvoidsCurrentWhenPossible() {
        var random = new Random(7);
        for (int i = 0; i < 50; i++) assertNotEquals("a", LobbyRules.pick(List.of("a", "b", "c"), "a", random));
        assertEquals("a", LobbyRules.pick(List.of("a"), "a", random));
        assertNull(LobbyRules.pick(List.<String>of(), "a", random));
    }
    @Test void pickCoversEveryOption() {
        var random = new Random(1);var seen = new java.util.HashSet<String>();
        for (int i = 0; i < 200; i++) seen.add(LobbyRules.pick(List.of("a", "b", "c"), null, random));
        assertEquals(java.util.Set.of("a", "b", "c"), seen);
    }
}
