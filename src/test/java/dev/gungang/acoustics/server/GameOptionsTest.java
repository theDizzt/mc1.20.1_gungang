package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameOptionsTest {
    @Test void absentOptionsKeepOriginalRules(){
        var options=ServerFiles.GSON.fromJson("{}",TeamOptions.class).game();
        assertEquals(GameOptions.defaults(),options);assertFalse(options.duplicatePrimary());assertTrue(options.duplicateSecondary());
        var map=PropHuntRules.example();assertSame(map,options.apply(map));
        assertEquals(new DeathmatchRules(321,12),options.deathmatch(new DeathmatchRules(321,12)));
    }
    @Test void timeAndScoreOverrideOnlyTheMatchCopy(){
        var base=FreeForAllRules.example();var options=new GameOptions(7,23,true,false);var changed=options.apply(base);
        assertEquals(420,changed.timeLimitSeconds());assertEquals(420,changed.freeForAll().timeLimitSeconds());assertEquals(23,changed.freeForAll().winningScore());
        assertEquals(600,base.timeLimitSeconds());assertEquals(16,base.freeForAll().winningScore());
        assertEquals(new DeathmatchRules(420,23),options.deathmatch(DeathmatchRules.defaults()));
    }
    @Test void propHuntOverridesSeekTimeOnly(){
        var base=PropHuntRules.example();var changed=new GameOptions(16,50,false,true).apply(base);
        assertEquals(16*60*20,PropHuntGame.huntTicks(changed));assertEquals(600,base.timeLimitSeconds());assertNotNull(changed.propHunt());
        assertThrows(IllegalArgumentException.class,()->new GameOptions(1,0,false,true).apply(base));
        assertThrows(IllegalArgumentException.class,()->new GameOptions(61,0,false,true).apply(base));
    }
    @Test void bombGoalIsWinsRequired(){
        var changed=new GameOptions(3,7,false,true).apply(BombRules.example());
        assertEquals(180,changed.timeLimitSeconds());assertEquals(7,changed.bomb().winsRequired());assertEquals(13,changed.bomb().rounds());
        assertThrows(IllegalArgumentException.class,()->new GameOptions(0,51,false,true).apply(BombRules.example()));
    }
    @Test void rejectsInvalidInputAndPreservesToggles(){
        assertThrows(IllegalArgumentException.class,()->new GameOptions(-1,1,false,true));
        assertThrows(IllegalArgumentException.class,()->new GameOptions(0,10001,false,true));
        var options=new GameOptions(8,40,true,false);assertEquals(options,ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(options),GameOptions.class));
    }
}
