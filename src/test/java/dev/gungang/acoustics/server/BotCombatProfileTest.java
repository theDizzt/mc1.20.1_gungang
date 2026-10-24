package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotCombatProfileTest {
    @Test void positionsMatchWeaponRole() {
        var sniper=BotCombatProfile.of("sniper","");var shotgun=BotCombatProfile.of("shotgun","");
        assertTrue(sniper.waypointCost("SNIPER")<sniper.waypointCost("ROUTE"));
        assertTrue(shotgun.waypointCost("OBJECTIVE")<shotgun.waypointCost("SNIPER"));
        assertTrue(BotCombatProfile.RIFLE.waypointCost("ROUTE")<BotCombatProfile.RIFLE.waypointCost("SNIPER"));
    }
    @Test void gunPackTypeOverridesBroadLobbyCategory() {
        assertTrue(BotCombatProfile.of("shotgun","근접무기").far()<BotCombatProfile.RIFLE.near());
        assertTrue(BotCombatProfile.of("smg","피스톨").strafe()>BotCombatProfile.of("pistol","").strafe());
        assertTrue(BotCombatProfile.of("sniper","소총").near()>=BotCombatProfile.RIFLE.far());
    }
    @Test void missingMetadataUsesCategoryOrConservativeDefault() {
        assertEquals(BotCombatProfile.of("sniper",""),BotCombatProfile.of(null,"저격총"));
        assertEquals(BotCombatProfile.RIFLE,BotCombatProfile.of("custom_type","미분류"));
        assertEquals(BotCombatProfile.RIFLE,BotCombatProfile.of(null,null));
    }
    @Test void allProfilesHaveOrderedDistancesAndBoundedExposure() {
        for(String type:new String[]{"shotgun","smg","pistol","sniper","dmr","mg","rpg","rifle"}) {
            var p=BotCombatProfile.of(type,"");
            assertTrue(p.near()>0&&p.near()<p.far()&&p.far()<p.engage(),type);
            assertTrue(p.strafe()>0&&p.strafe()<=1,type);
            assertTrue(p.exposureTicks()>=20&&p.exposureTicks()<=40,type);
        }
    }
}
