package dev.gungang.acoustics.server;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameOptionsTest {
    private static GameOptions with(GameOptions.Control c,GameOptions.Normandy n,GameOptions.PropHunt p,GameOptions.Escort e,GameOptions.Bomb b,GameOptions.Deathmatch d,GameOptions.FreeForAll f){
        return new GameOptions(null,c,n,p,e,b,d,f);
    }
    @Test void absentOptionsKeepOriginalRules(){
        var options=ServerFiles.GSON.fromJson("{}",TeamOptions.class).game();
        assertEquals(GameOptions.defaults(),options);assertFalse(options.common().duplicatePrimary());assertTrue(options.common().duplicateSecondary());
        for(var map:List.of(PropHuntRules.example(),FreeForAllRules.example(),BombRules.example(),ArenaMap.example()))
            for(var mode:List.of("CONTROL","PROP_HUNT","FREE_FOR_ALL","BOMB"))assertSame(map,options.apply(map,mode));
        assertEquals(new DeathmatchRules(321,12),options.deathmatch(new DeathmatchRules(321,12)));
        assertNull(options.preparation("CONTROL"));assertNull(options.common().respawnSeconds());
    }
    @Test void legacyTopLevelDuplicateFlagsMoveIntoCommon(){
        var options=ServerFiles.GSON.fromJson("{\"minutes\":0,\"targetScore\":0,\"duplicatePrimary\":true,\"duplicateSecondary\":false}",GameOptions.class);
        assertTrue(options.common().duplicatePrimary());assertFalse(options.common().duplicateSecondary());
        assertEquals(options,ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(options),GameOptions.class));
    }
    @Test void onlyTheSelectedModesSectionApplies(){
        var base=FreeForAllRules.example();
        var options=with(new GameOptions.Control(999,null,null,null),null,null,null,null,null,new GameOptions.FreeForAll(420,10,5,23));
        var changed=options.apply(base,"FREE_FOR_ALL");
        assertEquals(420,changed.timeLimitSeconds());assertEquals(420,changed.freeForAll().timeLimitSeconds());assertEquals(23,changed.freeForAll().winningScore());
        assertEquals(600,base.timeLimitSeconds());assertEquals(16,base.freeForAll().winningScore());
        // The opening grace counts toward the time limit, so it must be shorter.
        assertThrows(IllegalArgumentException.class,()->with(null,null,null,null,null,null,new GameOptions.FreeForAll(60,60,null,null)).apply(base,"FREE_FOR_ALL"));
    }
    @Test void controlTimeScoreAndPreparation(){
        var options=with(new GameOptions.Control(300,12,20,50),null,null,null,null,null,null);
        var changed=options.apply(ArenaMap.example(),"CONTROL");
        assertEquals(300,changed.timeLimitSeconds());assertEquals(50,changed.capture().winningScore());assertEquals(12,options.preparation("CONTROL"));
        assertNull(options.preparation("DEATHMATCH"));
    }
    @Test void deathmatchTimeAndTarget(){
        var options=with(null,null,null,null,null,new GameOptions.Deathmatch(420,null,23),null);
        assertEquals(new DeathmatchRules(420,23),options.deathmatch(DeathmatchRules.defaults()));
    }
    @Test void randomWeaponsPlayFifteenMinutesUnlessTimed(){
        var random=GameOptions.WeaponMode.RANDOM;
        var tdm=with(null,null,null,null,null,new GameOptions.Deathmatch(null,null,null,random,null,null),null);
        assertTrue(tdm.randomWeapons("DEATHMATCH"));assertFalse(tdm.randomWeapons("FREE_FOR_ALL"));
        assertEquals(GameOptions.RANDOM_TIME_SECONDS,tdm.deathmatch(DeathmatchRules.defaults()).timeLimitSeconds());
        assertArrayEquals(new int[]{GameOptions.ROTATION_SECONDS,GameOptions.CHOICE_SECONDS},tdm.weaponRotation("DEATHMATCH"));
        var timed=with(null,null,null,null,null,new GameOptions.Deathmatch(420,null,null,random,120,10),null);
        assertEquals(420,timed.deathmatch(DeathmatchRules.defaults()).timeLimitSeconds());assertArrayEquals(new int[]{120,10},timed.weaponRotation("DEATHMATCH"));
        var ffa=with(null,null,null,null,null,null,new GameOptions.FreeForAll(null,null,null,null,random,null,null));
        assertEquals(GameOptions.RANDOM_TIME_SECONDS,ffa.apply(FreeForAllRules.example(),"FREE_FOR_ALL").timeLimitSeconds());
        // Classic keeps the map's time.
        assertFalse(GameOptions.defaults().randomWeapons("DEATHMATCH"));
        // The choice opens before the change, so it must be shorter than the interval.
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Deathmatch(null,null,null,random,30,30));
        assertThrows(IllegalArgumentException.class,()->new GameOptions.FreeForAll(null,null,null,null,random,20,null));
        var json=ServerFiles.GSON.toJson(timed);assertEquals(timed,ServerFiles.GSON.fromJson(json,GameOptions.class));
    }
    @Test void normandyCaptureTimePerPoint(){
        var rules=NormandyRules.defaults();
        var options=with(null,new GameOptions.Normandy(null,null,Arrays.asList(10,null,30)),null,null,null,null,null);
        assertArrayEquals(new int[]{200,rules.captureTicks(),600,rules.captureTicks()},options.normandyTicks(rules));
        var game=new NormandyGame(rules,options.normandyTicks(rules));
        for(int i=0;i<199;i++)assertFalse(game.tick(true,false));
        assertTrue(game.tick(true,false));assertEquals(1,game.stage());
    }
    @Test void bombRoundsSwapAndPreparation(){
        var options=with(null,null,null,null,new GameOptions.Bomb(180,10,6,3,2.5,30.0,7.0),null,null);
        var changed=options.apply(BombRules.example(),"BOMB");
        assertEquals(180,changed.timeLimitSeconds());assertEquals(6,changed.bomb().rounds());assertEquals(3,changed.bomb().swapEveryRounds());
        assertEquals(10,changed.bomb().preparationSeconds());assertEquals(4,changed.bomb().winsRequired());
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Bomb(null,null,1,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Bomb(null,null,null,0,null,null,null));
        assertThrows(IllegalArgumentException.class,()->with(null,null,null,null,new GameOptions.Bomb(null,null,4,5,null,null,null),null,null).apply(BombRules.example(),"BOMB"));
        // Fewer rounds than the map's swap interval: the swap shrinks to the round count.
        var shortGame=with(null,null,null,null,new GameOptions.Bomb(null,null,2,null,null,null,null),null,null).apply(BombRules.example(),"BOMB");
        assertEquals(2,shortGame.bomb().rounds());assertTrue(shortGame.bomb().swapEveryRounds()<=2);
    }
    @Test void propHuntChangesNeedEnoughTime(){
        assertTrue(GameOptions.PropHunt.allowed(600,9));assertFalse(GameOptions.PropHunt.allowed(600,10));
        assertFalse(GameOptions.PropHunt.allowed(119,1));assertTrue(GameOptions.PropHunt.allowed(120,1));assertTrue(GameOptions.PropHunt.allowed(60,0));
        assertEquals(60,GameOptions.PropHunt.interval(600,9));assertEquals(270,GameOptions.PropHunt.interval(600,2));
        var base=PropHuntRules.example();
        assertSame(base,with(null,null,new GameOptions.PropHunt(300,30,List.of(),4,GameOptions.Choice.FIRST_COME,false),null,null,null,null).apply(base,"PROP_HUNT"));
        assertThrows(IllegalArgumentException.class,()->with(null,null,new GameOptions.PropHunt(300,null,List.of(),5,null,null),null,null,null,null).apply(base,"PROP_HUNT"));
        assertThrows(IllegalArgumentException.class,()->new GameOptions.PropHunt(59,null,null,null,null,null));
        assertEquals(List.of("01","23"),new GameOptions.PropHunt(null,null,List.of("1"," 23","01",""),null,null,null).excluded());
        assertThrows(IllegalArgumentException.class,()->new GameOptions.PropHunt(null,null,List.of("stone"),null,null,null));
    }
    @Test void rangesAreChecked(){
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Control(29,null,null,null));
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Deathmatch(null,null,10001));
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Common(null,null,0,null,null));
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Common(null,null,null,null,.9));
        // The bigger team's health: 50~100 percent.
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Common(null,null,null,null,null,49));
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Common(null,null,null,null,null,101));
        assertDoesNotThrow(()->new GameOptions.Common(null,null,null,null,null,50));
        assertEquals(75,new com.google.gson.Gson().fromJson("{\"handicapLargeHealth\":75}",GameOptions.Common.class).handicapLargeHealth());
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Escort(null,null,null,null,null,.99,null,null));
        assertThrows(IllegalArgumentException.class,()->new GameOptions.Escort(null,null,null,null,null,null,null,61));
    }
    @Test void roundTripsThroughJson(){
        var options=new GameOptions(new GameOptions.Common(true,false,8,true,1.5),new GameOptions.Control(300,20,15,80),new GameOptions.Normandy(900,40,Arrays.asList(20,null,25)),
                new GameOptions.PropHunt(480,45,List.of("05"),3,GameOptions.Choice.OFF,false),new GameOptions.Escort(240,30,Arrays.asList(120,null,60),10,1.2,1.75,.4,3),
                new GameOptions.Bomb(150,20,8,4,4.5,35.0,8.0),new GameOptions.Deathmatch(600,30,75),new GameOptions.FreeForAll(600,20,10,25));
        assertEquals(options,ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(options),GameOptions.class));
    }
    @Test void escortSpeedReachesItsTopWithFourEscorts(){
        assertEquals(0,EscortGame.multiplier(1.75,0),1e-9);assertEquals(1,EscortGame.multiplier(1.75,1),1e-9);
        assertEquals(1.25,EscortGame.multiplier(1.75,2),1e-9);assertEquals(1.75,EscortGame.multiplier(1.75,4),1e-9);assertEquals(1.75,EscortGame.multiplier(1.75,9),1e-9);
        assertEquals(1,EscortGame.multiplier(1,4),1e-9);
    }
}
