package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotSettingsTest {
    @Test void defaultOffAndAutoBalancesOneBot(){
        var settings=BotSettings.defaults();assertFalse(settings.enabled());
        assertEquals("BRAVO",settings.chooseTeam(1,0));assertEquals("ALPHA",settings.chooseTeam(1,3));
        assertEquals("BRAVO",settings.chooseTeam(2,2));assertEquals("ALPHA",settings.team("ALPHA").chooseTeam(4,1));
    }
    @Test void presetsChangeCostWithoutEnablingOrChangingDifficulty(){
        var settings=BotSettings.defaults().team("ALPHA").weapon("rifle");
        var low=settings.preset("low");var high=settings.preset("high");
        assertFalse(low.enabled());assertEquals("ALPHA",low.team());assertEquals("rifle",high.weapon());
        assertTrue(low.thinkTicks()>high.thinkTicks());assertTrue(low.pathTicks()>high.pathTicks());assertTrue(low.range()<high.range());
        assertEquals(low.reactionTicks(),high.reactionTicks());assertEquals(low.aimError(),high.aimError());
    }
    @Test void rejectUnboundedWorkAndInvalidPersistedSettings(){
        for(String field:java.util.List.of("thinkTicks","pathTicks","range","reactionTicks","aimError")){
            assertThrows(IllegalArgumentException.class,()->BotSettings.defaults().set(field,0));
            assertThrows(IllegalArgumentException.class,()->BotSettings.defaults().set(field,9999));
        }
        assertThrows(IllegalArgumentException.class,()->BotSettings.defaults().set("aimError",Double.NaN));
        assertThrows(IllegalArgumentException.class,()->BotSettings.defaults().team("SPECTATOR"));
        var settings=BotSettings.defaults().enabled(true).preset("low").set("aimError",2.5);
        assertEquals(settings,ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(settings),BotSettings.class));
        assertThrows(Exception.class,()->ServerFiles.GSON.fromJson("{\"enabled\":true}",BotSettings.class));
    }
    @Test void botKillsAndDeathsContributeToTeamVictory(){
        var ledger=new MatchLedger();var bot=java.util.UUID.randomUUID();var human=java.util.UUID.randomUUID();
        ledger.add(bot,"Rookie","BRAVO");ledger.add(human,"Human","ALPHA");
        ledger.damage(bot,human,25,1);ledger.death(human,bot,2,true);
        assertEquals(1,ledger.kills("BRAVO"));
        ledger.death(bot,human,3,true);assertEquals(1,ledger.kills("ALPHA"));
        ledger.add(bot,"Rookie","BRAVO"); // respawn/off-on preserves score
        ledger.death(human,bot,4,true);assertEquals(2,ledger.kills("BRAVO"));
        assertEquals("BRAVO",new DeathmatchRules(60,2).winner(ledger.kills("ALPHA"),ledger.kills("BRAVO"),false));
    }
    @Test void oldConfigsKeepTuningAndNewControlsSurviveOtherCommands(){
        var legacy=ServerFiles.GSON.fromJson("{\"enabled\":true,\"team\":\"AUTO\",\"weapon\":\"rifle\",\"thinkTicks\":10,\"pathTicks\":20,\"range\":32,\"reactionTicks\":12,\"aimError\":3}",BotSettings.class);
        assertEquals(12,legacy.reactionTicks());assertEquals(1.0,legacy.movementSpeed());assertEquals("",legacy.secondary());
        var fast=legacy.secondary("pistol").preset("responsive").enabled(false).team("BRAVO").weapon("rifle2");
        assertEquals(4,fast.reactionTicks());assertEquals(1.1,fast.movementSpeed());assertEquals("pistol",fast.secondary());
        assertEquals(1.5,fast.set("movementSpeed",1.5).movementSpeed());
        assertThrows(IllegalArgumentException.class,()->fast.set("movementSpeed",0));
        assertThrows(IllegalArgumentException.class,()->fast.set("movementSpeed",2.1));
        assertThrows(IllegalArgumentException.class,()->fast.set("movementSpeed",Double.NaN));
        assertEquals(fast,ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(fast),BotSettings.class));
    }
}
