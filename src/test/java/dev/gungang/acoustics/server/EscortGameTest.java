package dev.gungang.acoustics.server;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EscortGameTest {
    private EscortRules rules(){return new EscortRules(new ArenaMap.Area(0,0,0,100,20,100),null,null,null,null,null,null,null,null,null,List.of(0,1),null,null,null);}
    @Test void playersAccelerateButDefendersAlwaysStop(){
        var game=new EscortGame(rules(),240,100,List.of(50d));game.tick(1,false);assertEquals(.05,game.distance,1e-8);
        game.tick(5,false);assertEquals(.15,game.distance,1e-8);game.tick(5,true);assertEquals(.15,game.distance,1e-8);assertEquals("CONTESTED",game.motion);
    }
    @Test void checkpointStopsExactlyFiveSecondsAndAwardsOnce(){
        var game=new EscortGame(rules(),240,10,List.of(1d));while(game.completed==0)game.tick(1,false);
        int before=game.remainingTicks;assertEquals(100,game.stopTicks);assertEquals(1,game.distance,1e-8);
        assertTrue(before>240*20,"Bonus is granted immediately on arrival");
        for(int i=0;i<99;i++)game.tick(1,false);assertEquals(1,game.distance,1e-8);assertEquals(0,game.remainingTicks-(before-99));
        game.tick(1,false);assertEquals(before-100,game.remainingTicks);game.tick(1,false);assertTrue(game.distance>1);
        for(int i=0;i<400;i++)game.tick(0,false);assertEquals(1,game.completed);assertEquals(1,game.distance,1e-8);
    }
    @Test void rollbackWaitsAndCannotCrossCheckpoint(){
        var game=new EscortGame(rules(),240,10,List.of(5d));game.distance=3;
        for(int i=0;i<199;i++)game.tick(0,false);assertEquals(3,game.distance,1e-8);game.tick(0,false);assertTrue(game.distance<3);
        game.completed=1;game.distance=5.1;for(int i=0;i<200;i++)game.tick(0,false);assertEquals(5,game.distance,1e-8);
    }
    @Test void defendersAlonePushBackAtBaseSpeedToStart(){
        var game=new EscortGame(rules(),240,10,List.of(5d));game.completed=1;game.distance=5.02;
        game.tick(0,true);assertEquals("REVERSE",game.motion);assertEquals(0,game.multiplier,1e-8);assertEquals(4.97,game.distance,1e-8);
        for(int i=0;i<200;i++)game.tick(0,true);assertEquals(0,game.distance,1e-8);assertEquals(1,game.completed,"Passed checkpoints stay earned");
        game.distance=1;game.tick(1,true);assertEquals(1,game.distance,1e-8);assertEquals("CONTESTED",game.motion);
    }
    @Test void finalArrivalWinsAndTimeoutDefends(){
        var attack=new EscortGame(rules(),1,10,List.of(5d));attack.completed=1;attack.distance=9.99;attack.remainingTicks=1;attack.tick(1,false);assertEquals("ALPHA",attack.winner);
        var defend=new EscortGame(rules(),1,10,List.of(5d));for(int i=0;i<20;i++)defend.tick(0,false);
        assertTrue(defend.overtime());assertEquals(EscortGame.OVERTIME_TICKS,defend.overtimeTicks);
        for(int i=1;i<EscortGame.OVERTIME_TICKS;i++)defend.tick(0,false);assertEquals("",defend.winner);
        defend.tick(0,false);assertEquals("BRAVO",defend.winner);
    }
    @Test void earnedCheckpointBonusSurvivesLastTick(){
        var game=new EscortGame(rules(),1,10,List.of(1d));game.distance=.99;game.remainingTicks=1;game.tick(1,false);
        assertEquals(3600,game.remainingTicks);for(int i=0;i<100;i++)game.tick(0,true);assertEquals("",game.winner);assertEquals(3500,game.remainingTicks);
    }
    @Test void invalidSettingsRejected(){var r=new EscortRules(null,null,null,null,null,null,null,null,null,null,null,null,null,null);assertThrows(IllegalArgumentException.class,r::validate);rules().validate();}
    @Test void publishedExampleLoadsAndDefaultsToFourMinutes()throws Exception {
        var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("docs/server-examples/escort.json"))).getAsJsonObject();
        var gson=new com.google.gson.Gson();var map=gson.fromJson(json,ArenaMap.class).validate();assertEquals(240,map.timeLimitSeconds());assertTrue(DeathmatchRules.supported(map));assertFalse(DeathmatchRules.exclusive(map));
        json.remove("timeLimitSeconds");assertEquals(240,gson.fromJson(json,ArenaMap.class).validate().timeLimitSeconds());
    }
    @Test void overtimeRefillsWhileAttackersHoldTheCart(){
        var game=new EscortGame(rules(),1,10,List.of(5d));for(int i=0;i<20;i++)game.tick(0,false);
        for(int i=0;i<60;i++)game.tick(0,false);assertEquals(EscortGame.OVERTIME_TICKS-60,game.overtimeTicks);
        game.tick(1,true);assertEquals(EscortGame.OVERTIME_TICKS,game.overtimeTicks);assertEquals("",game.winner);
        game.tick(1,false);assertEquals(EscortGame.OVERTIME_TICKS,game.overtimeTicks);
        game.completed=1;game.distance=9.99;game.tick(1,false);assertEquals("ALPHA",game.winner);
    }
}
