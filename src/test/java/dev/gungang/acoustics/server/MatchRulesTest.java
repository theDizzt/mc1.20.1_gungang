package dev.gungang.acoustics.server;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchRulesTest {
    @Test void progressAdvancesEveryTickAndContestsStopImmediately() {
        var game=new CaptureGame();
        for(int tick=1;tick<=10;tick++) { game.tick(true,false,2,30);assertEquals(tick*0.5f,game.progress()); }
        game.tick(true,true,2,30);assertEquals(5f,game.progress());
        game.tick(false,false,2,30);assertEquals(5f,game.progress());
        for(int i=0;i<600;i++)game.tick(true,false,3,30);
        assertEquals(100f,game.progress());
    }
    @Test void boundaryMarginDoesNotDelayOpponentsOrKeepDeadPlayersPresent() {
        var presence=new CapturePresence();var first=UUID.randomUUID();var enemy=UUID.randomUUID();
        var zone=new net.minecraft.world.phys.AABB(0,0,0,10,5,10);
        var outside=new net.minecraft.world.phys.AABB(10.05,1,1,10.65,3,2);
        assertFalse(presence.update(first,outside,zone));
        assertTrue(presence.update(first,new net.minecraft.world.phys.AABB(9.8,1,1,10.4,3,2),zone));
        assertTrue(presence.update(first,outside,zone));
        assertTrue(presence.update(enemy,new net.minecraft.world.phys.AABB(0.1,1,1,0.7,3,2),zone));
        presence.retain(java.util.Set.of(enemy));
        assertFalse(presence.update(first,outside,zone));
        assertFalse(presence.update(enemy,new net.minecraft.world.phys.AABB(10.2,1,1,10.8,3,2),zone));
    }
    @Test void captureContestsAndScoringMatchDatapack() {
        var game=new CaptureGame();
        for(int i=0;i<200;i++)game.tick(true,false,2,30);
        assertEquals(100,game.progress());
        int before=game.alphaScore();
        for(int i=0;i<60;i++)game.tick(true,true,2,30);
        assertEquals(100,game.progress()); assertEquals(before+2,game.alphaScore());
        for(int i=0;i<100;i++)game.tick(false,true,2,30);
        assertEquals(0,game.progress());
        game.tick(false,true,2,30); assertEquals(-0.5f,game.progress());
        float progress=game.progress();
        for(int i=0;i<50;i++)game.tick(false,false,2,30);
        assertEquals(progress,game.progress());
        for(int i=0;i<400;i++)game.tick(false,true,2,30);
        assertEquals(-100,game.progress()); assertTrue(game.bravoScore()>0);
        assertEquals("BRAVO",game.winner(game.bravoScore()));
    }
    @Test void ledgerCountsEnemiesAssistsAndExpiresOldHits() {
        var ledger=new MatchLedger(); var a=UUID.randomUUID();var b=UUID.randomUUID();var v=UUID.randomUUID();var s=UUID.randomUUID();
        ledger.add(a,"Alpha","ALPHA");ledger.add(b,"Helper","ALPHA");ledger.add(v,"Bravo","BRAVO");ledger.add(s,"Observer","SPECTATOR");
        ledger.damage(a,b,100,1);ledger.damage(s,v,100,1);ledger.damage(a,v,Double.NaN,1);
        ledger.damage(a,v,5,1);ledger.damage(b,v,3,2);ledger.death(v,a,3);
        var alpha=ledger.rows().stream().filter(r->r.id().equals(a.toString())).findFirst().orElseThrow();
        assertEquals(1,alpha.kills());assertEquals(5,alpha.damage());
        assertEquals(1,ledger.rows().stream().filter(r->r.id().equals(b.toString())).findFirst().orElseThrow().assists());
        ledger.damage(a,v,2,10);ledger.death(v,null,211);
        assertEquals(1,ledger.rows().stream().filter(r->r.id().equals(a.toString())).findFirst().orElseThrow().kills());
        for(int i=0;i<40;i++){ledger.played(a);ledger.played(s);}
        assertEquals(2,ledger.rows().stream().filter(r->r.id().equals(a.toString())).findFirst().orElseThrow().playSeconds());
        assertEquals(0,ledger.rows().stream().filter(r->r.id().equals(s.toString())).findFirst().orElseThrow().playSeconds());
    }
    @Test void mapsPermitUnlimitedTimeAndValidateCapture() {
        var json=ServerFiles.GSON.toJsonTree(ArenaMap.example()).getAsJsonObject();json.addProperty("timeLimitSeconds",0);
        assertEquals(0,ServerFiles.GSON.fromJson(json,ArenaMap.class).validate().timeLimitSeconds());
        json.getAsJsonObject("capture").addProperty("scoreIntervalTicks",0);
        assertThrows(IllegalArgumentException.class,()->ServerFiles.GSON.fromJson(json,ArenaMap.class).validate());
    }
    @Test void galmuriFontIncludesTimerCharacters() throws Exception {
        var font=java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,new java.io.File("font/Galmuri11-Bold.ttf"));
        assertEquals(-1,font.canDisplayUpTo("0123456789:"));
    }
}
