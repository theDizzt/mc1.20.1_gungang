package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShieldRulesTest {
    @Test void consumesOnlyBlockedDamageAndPassesOverflow() {
        var first=ShieldRules.absorb(75,25);assertEquals(50,first.durability());assertEquals(0,first.damage());
        var broken=ShieldRules.absorb(first.durability(),80);assertEquals(0,broken.durability());assertEquals(30,broken.damage());
        assertEquals(20,ShieldRules.absorb(0,20).damage());
        assertEquals(0,ShieldRules.absorb(75,75).damage());
    }
    @Test void onlyFrontHemisphereIsProtected() {
        assertTrue(ShieldRules.inFront(0,1,0,5));assertTrue(ShieldRules.inFront(0,1,5,1));
        assertFalse(ShieldRules.inFront(0,1,0,-5));assertFalse(ShieldRules.inFront(0,1,5,0));
        assertFalse(ShieldRules.inFront(0,1,0,0));assertTrue(ShieldRules.inFront(-1,0,-5,0));
    }
}
