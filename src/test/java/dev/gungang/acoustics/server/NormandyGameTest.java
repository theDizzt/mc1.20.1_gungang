package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NormandyGameTest {
    @Test void sequentialCaptureAndFinalVictory() {
        var game=new NormandyGame(NormandyRules.defaults());
        for(int stage=0;stage<4;stage++) {
            assertEquals(stage,game.stage());
            for(int i=0;i<399;i++) assertFalse(game.tick(true,false));
            assertTrue(game.tick(true,false));
        }
        assertTrue(game.complete()); assertEquals("BRAVO",game.winner(true));
        assertFalse(game.tick(true,false));
    }
    @Test void contestedFreezesAndResetsDecay() {
        var game=new NormandyGame(NormandyRules.defaults());
        for(int i=0;i<200;i++)game.tick(true,false);
        for(int i=0;i<1000;i++)game.tick(true,true);
        assertEquals(50f,game.percent());
        for(int i=0;i<408;i++)game.tick(false,true);
        assertEquals(50f,game.percent());
        game.tick(false,true); assertEquals(49.75f,game.percent());
        game.tick(true,true);
        for(int i=0;i<408;i++)game.tick(false,false);
        assertEquals(49.75f,game.percent());
    }
    @Test void defenseWinsTimeoutEvenWithPartialCapture() {
        var game=new NormandyGame(NormandyRules.defaults());
        for(int i=0;i<600;i++)game.tick(true,false);
        assertEquals(1,game.stage()); assertEquals("ALPHA",game.winner(true));
        assertEquals("",game.winner(false));
        assertEquals(-30,game.point().attackSpawn().x());
    }
    @Test void decayCannotBecomeNegative() {
        var game=new NormandyGame(NormandyRules.defaults());
        for(int i=0;i<10000;i++)game.tick(false,false);
        assertEquals(0,game.percent()); assertEquals(0,game.stage());
    }
    @Test void exampleAndLegacyMapsValidate() {
        assertNotNull(NormandyRules.example().validate());
        assertNull(ArenaMap.example().validate().normandy());
        var rules=NormandyRules.defaults();
        assertThrows(IllegalArgumentException.class,()->new NormandyRules(0,400,10,rules.attackLaunch(),rules.points()).validate());
    }
    @Test void configAndSnapshotRoundTrip() {
        var gson=new com.google.gson.Gson();
        var map=gson.fromJson(gson.toJson(NormandyRules.example()),ArenaMap.class).validate();
        assertEquals(900,map.timeLimitSeconds());
        assertEquals(4,map.normandy().points().size());
        var point=new MatchSnapshot.Capture("C",-50,true,-1,4,"NORMANDY",2);
        assertEquals(point,gson.fromJson(gson.toJson(point),MatchSnapshot.Capture.class));
    }
}
