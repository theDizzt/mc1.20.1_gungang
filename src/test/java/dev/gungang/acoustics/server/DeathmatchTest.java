package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeathmatchTest {
    @Test void friendlyKillAwardsOpponentWithoutPersonalKillOrSuicidePoint() {
        var ledger=new MatchLedger();var a=UUID.randomUUID();var ally=UUID.randomUUID();var enemy=UUID.randomUUID();
        ledger.add(a,"a","ALPHA");ledger.add(ally,"ally","ALPHA");ledger.add(enemy,"enemy","BRAVO");
        ledger.damage(enemy,ally,10,1);ledger.death(ally,a,2,true);
        assertEquals(1,ledger.kills("BRAVO"));assertEquals(0,ledger.kills("ALPHA"));
        assertEquals(0,ledger.rows().stream().mapToInt(MatchSnapshot.Row::kills).sum());
        assertEquals(0,ledger.rows().stream().mapToInt(MatchSnapshot.Row::assists).sum());
        ledger.death(a,a,3,true);assertEquals(1,ledger.kills("BRAVO"));
        ledger.death(enemy,a,4,true);assertEquals(1,ledger.kills("ALPHA"));
    }
    @Test void defaultsAndValidation() {
        assertEquals(1200,DeathmatchRules.defaults().timeLimitSeconds());
        assertEquals(100,DeathmatchRules.defaults().winningKills());
        assertEquals(DeathmatchRules.defaults(),ServerFiles.GSON.fromJson("{}",DeathmatchRules.class));
        assertThrows(IllegalArgumentException.class,()->new DeathmatchRules(0,100));
        assertThrows(IllegalArgumentException.class,()->new DeathmatchRules(1200,0));
    }
    @Test void scoreLimitAndTimeout() {
        var r=DeathmatchRules.defaults();
        assertEquals("",r.winner(99,90,false));assertEquals("ALPHA",r.winner(100,90,false));
        assertEquals("BRAVO",r.winner(0,100,false));assertEquals("BRAVO",r.winner(44,59,true));
        assertEquals("ALPHA",r.winner(59,44,true));assertEquals("DRAW",r.winner(10,10,true));
    }
    @Test void captureAndHooksAreDisabledOnlyInDeathmatchCopy() {
        var original=ArenaMap.example();var arena=DeathmatchRules.defaults().arena(original).validate();
        assertNull(arena.capture());assertNull(arena.normandy());assertEquals(1200,arena.timeLimitSeconds());
        assertTrue(arena.onStart().isEmpty());assertTrue(arena.onPrepare().isEmpty());assertTrue(arena.onEnd().isEmpty());
        assertNotNull(original.capture());assertEquals(original.alphaSpawn(),arena.alphaSpawn());
        assertThrows(IllegalArgumentException.class,()->DeathmatchRules.defaults().arena(NormandyRules.example()));
    }
    @Test void teamKillsExcludeFriendlyFireAndSurviveRejoin() {
        var ledger=new MatchLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();var ally=UUID.randomUUID();
        ledger.add(a,"a","ALPHA");ledger.add(ally,"ally","ALPHA");ledger.add(b,"b","BRAVO");
        ledger.death(b,a,1);ledger.death(ally,a,2);ledger.death(a,a,3);
        assertEquals(1,ledger.kills("ALPHA"));assertEquals(0,ledger.kills("BRAVO"));
        ledger.add(a,"a","BRAVO");assertEquals(1,ledger.kills("ALPHA"));
        ledger.death(ally,a,4);assertEquals(1,ledger.kills("BRAVO"));
    }
    @Test void environmentalKillUsesRecentEnemyDamageCredit() {
        var ledger=new MatchLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();
        ledger.add(a,"a","ALPHA");ledger.add(b,"b","BRAVO");
        ledger.damage(a,b,10,1);ledger.death(b,null,50);assertEquals(1,ledger.kills("ALPHA"));
        ledger.damage(a,b,10,100);ledger.death(b,null,400);assertEquals(1,ledger.kills("ALPHA"));
    }
}
