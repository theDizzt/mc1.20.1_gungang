package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrdnanceRulesTest {
    @Test void orbitalDamageFallsLinearlyFromCenterToEdge() {
        assertEquals(256,OrdnanceRules.orbitalDamage(0));
        assertEquals(170,OrdnanceRules.orbitalDamage(12));
        assertEquals(84,OrdnanceRules.orbitalDamage(24));
        assertEquals(0,OrdnanceRules.orbitalDamage(24.01));
    }
    @Test void fireIsADiscAndBlastUsesDistanceNotABoundingCube() {
        assertTrue(OrdnanceRules.inFire(3,0,4));
        assertFalse(OrdnanceRules.inFire(4,0,4));
        assertFalse(OrdnanceRules.inFire(0,3,0));
        assertTrue(OrdnanceRules.inBlast(24,0,0));
        assertTrue(OrdnanceRules.inBlast(0,24,0));
        assertFalse(OrdnanceRules.inBlast(24,0,1));
        assertFalse(OrdnanceRules.inBlast(20,0,20));
    }
    @Test void upgradeOnlyKnownUnfinishedNumberedPlaceholders() {
        var placeholder=new Loadouts.Weapon("81","zeta:p92","old","[id: 81] 개발중...",null,"");
        assertTrue(OrdnanceLoadouts.upgradePlaceholder(placeholder).itemNbt().contains("gungang:molotov"));
        var custom=new Loadouts.Weapon("81","zeta:p92","custom","Custom weapon",null,"");
        assertSame(custom,OrdnanceLoadouts.upgradePlaceholder(custom));
        var other=new Loadouts.Weapon("82","zeta:p92","old","개발중",null,"");
        assertSame(other,OrdnanceLoadouts.upgradePlaceholder(other));
    }
    @Test void catalogHasRequestedIdsAndCooldowns() {
        var definitions=OrdnanceLoadouts.defaults();
        assertEquals("81",definitions.get(0).id());assertEquals(36d,definitions.get(0).cooldownSeconds());
        assertEquals("87",definitions.get(1).id());assertEquals(240d,definitions.get(1).cooldownSeconds());
    }
}
