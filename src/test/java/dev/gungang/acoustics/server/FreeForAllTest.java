package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FreeForAllTest {
    @Test void defaultsAndMapRoundTrip(){
        var map=FreeForAllRules.example().validate();
        assertEquals(600,map.timeLimitSeconds());assertEquals(16,map.freeForAll().winningScore());
        var copy=ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(map),ArenaMap.class).validate();
        assertEquals(map,copy);assertTrue(DeathmatchRules.supported(copy));assertFalse(DeathmatchRules.exclusive(copy));
        assertNull(DeathmatchRules.defaults().arena(copy).validate().freeForAll());
        assertThrows(IllegalArgumentException.class,()->new FreeForAllRules(null,null,List.of()));
        assertThrows(IllegalArgumentException.class,()->new FreeForAllRules(0,16,map.freeForAll().spawns()));
    }
    @Test void killsSuicidesAndReachingScoreAgain(){
        var scores=new FreeForAllScores();var a=UUID.randomUUID();var b=UUID.randomUUID();
        scores.add(a);scores.add(b);
        scores.death(b,a);scores.death(a,b);
        assertEquals(List.of(a,b),scores.ranking());
        scores.death(a,a);scores.death(b,a);
        assertEquals(List.of(b,a),scores.ranking()); // b reached 1 before a regained it
        scores.death(a,null);scores.death(a,null);
        assertEquals(-1,scores.points(a));assertEquals(1,scores.points(b));
        scores.add(a);assertEquals(-1,scores.points(a));
    }
    @Test void goalAndTimeoutUseSameOrder(){
        var scores=new FreeForAllScores();var a=UUID.randomUUID();var b=UUID.randomUUID();scores.add(a);scores.add(b);
        assertNull(scores.winner(16,false));assertEquals(a,scores.winner(16,true));
        for(int i=0;i<15;i++)scores.death(a,b);
        assertNull(scores.winner(16,false));scores.death(a,b);assertEquals(b,scores.winner(16,false));
        assertEquals(b,scores.winner(16,true));
    }
    @Test void allPlayersAreEnemiesEvenWhenLobbyTeamsMatch(){
        var ledger=new MatchLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();
        ledger.add(a,"a",a.toString());ledger.add(b,"b",b.toString());
        ledger.damage(a,b,30,1);ledger.death(b,a,2,false);
        assertEquals(1,ledger.rows().stream().filter(r->r.id().equals(a.toString())).findFirst().orElseThrow().kills());
    }
}
