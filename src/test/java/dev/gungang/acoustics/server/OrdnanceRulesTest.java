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
        var arrows=new Loadouts.Weapon("79","zeta:p92","old","[id: 79] 개발중...",null,"");
        assertTrue(OrdnanceLoadouts.upgradePlaceholder(arrows).itemNbt().contains("gungang:arrow_grenade"));
        var previous=new Loadouts.Weapon("79","gungang:arrow_grenade","custom","Custom damage",null,"왼손 아이템 좌클릭 후 복귀 매크로 키(기본 G)로 투척합니다. 주손에 든 경우 우클릭으로 던질 수 있습니다.","투척형",150d,100d,45d);
        var updated=OrdnanceLoadouts.upgradePlaceholder(previous);
        assertTrue(updated.usage().contains("기본 H"));assertEquals(45d,updated.cooldownSeconds());assertEquals("Custom damage",updated.description());
    }
    @Test void catalogHasRequestedIdsAndCooldowns() {
        var definitions=OrdnanceLoadouts.defaults();
        var grenade=definitions.stream().filter(w->w.id().equals("79")).findFirst().orElseThrow();
        assertEquals(30d,grenade.cooldownSeconds());assertTrue(grenade.itemNbt().contains("gungang:arrow_grenade"));
        assertEquals("81",definitions.get(0).id());assertEquals(36d,definitions.get(0).cooldownSeconds());
        assertEquals("87",definitions.get(1).id());assertEquals(240d,definitions.get(1).cooldownSeconds());
        var trap=definitions.stream().filter(w->w.id().equals("89")).findFirst().orElseThrow();
        assertEquals(36d,trap.cooldownSeconds());assertEquals("2026-10-21",trap.releaseDate());
        var axe=definitions.stream().filter(w->w.id().equals("80")).findFirst().orElseThrow();
        assertEquals(16d,axe.cooldownSeconds());assertTrue(axe.itemNbt().contains("gungang:hand_axe"));
    }
}
