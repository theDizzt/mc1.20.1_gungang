package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BombGameTest {
    private BombGame game(){return new BombGame(BombRules.defaults(),210,true);}
    private void start(BombGame game){for(int i=0;i<600;i++)game.tick(2,2);assertEquals(BombGame.Phase.RUNNING,game.phase());}
    @Test void preparationAndDefaultTime(){
        var game=game();for(int i=0;i<599;i++)game.tick(0,0);
        assertEquals(BombGame.Phase.PREPARING,game.phase());game.tick(2,2);
        assertEquals(4200,game.remainingTicks());assertEquals("ALPHA",game.attacker());
    }
    @Test void timeoutAwardsDefenders(){var g=game();start(g);for(int i=0;i<4200;i++)g.tick(1,1);assertEquals("BRAVO",g.roundWinner());assertEquals("TIMEOUT",g.reason());}
    @Test void eliminationBeforePlant(){
        var g=game();start(g);g.tick(0,2);assertEquals("BRAVO",g.roundWinner());
        g=game();start(g);g.tick(2,0);assertEquals("ALPHA",g.roundWinner());
    }
    @Test void bombSurvivesAttackWipeAndRoundTimeout(){
        var g=game();start(g);for(int i=0;i<4199;i++)g.tick(1,1);
        assertTrue(g.plant(900));assertFalse(g.plant(900));
        for(int i=0;i<899;i++)g.tick(0,1);
        assertEquals(BombGame.Phase.RUNNING,g.phase());g.tick(0,1);
        assertEquals("ALPHA",g.roundWinner());assertEquals("EXPLODED",g.reason());
    }
    @Test void defuseAndNoSecondResolution(){
        var g=game();assertFalse(g.plant(900));assertFalse(g.defuse());start(g);assertTrue(g.plant(900));
        assertTrue(g.defuse());assertFalse(g.defuse());assertFalse(g.plant(900));assertEquals(1,g.bravoScore());
    }
    @Test void roundTitleIsThreeSecondsAndSidesSwap(){
        var g=game();start(g);g.tick(1,0);
        for(int i=0;i<59;i++)g.tick(1,0);assertEquals(1,g.round());assertEquals(BombGame.Phase.ROUND_END,g.phase());
        g.tick(1,0);assertEquals(2,g.round());assertEquals("BRAVO",g.attacker());assertEquals(600,g.remainingTicks());
    }
    @Test void firstToThreeEndsEarly(){
        var g=game();
        for(int round=1;round<=3;round++){
            start(g);g.tick(g.attacker().equals("ALPHA")?1:0,g.attacker().equals("ALPHA")?0:1);
            for(int i=0;i<60;i++)g.tick(1,1);
        }
        assertEquals("ALPHA",g.winner());assertEquals(3,g.alphaScore());assertEquals(BombGame.Phase.FINISHED,g.phase());assertEquals(3,g.round());
    }
    @Test void fiveRoundsMaximumAndBothInitialRoles(){
        var g=new BombGame(BombRules.defaults(),210,false);assertEquals("BRAVO",g.attacker());
        for(int round=1;round<=5;round++){
            start(g);g.tick(1,0);for(int i=0;i<60;i++)g.tick(1,1);
        }
        assertEquals(5,g.round());assertEquals("BRAVO",g.winner());assertEquals(2,g.alphaScore());assertEquals(3,g.bravoScore());
    }
    @Test void mapRoundtripAndDeathmatchConversion(){
        var gson=new com.google.gson.Gson();var source=gson.fromJson(gson.toJson(BombRules.example()),ArenaMap.class).validate();
        assertEquals(210,source.timeLimitSeconds());assertEquals(3,source.bomb().winsRequired());assertTrue(DeathmatchRules.supported(source));
        var tdm=DeathmatchRules.defaults().arena(source).validate();assertNull(tdm.bomb());assertNull(tdm.capture());
        assertEquals(source.bomb().attackSpawn(),tdm.alphaSpawn());assertEquals(source.bomb().defenseSpawn(),tdm.bravoSpawn());assertEquals(1200,tdm.timeLimitSeconds());
    }
    @Test void invalidRoundCountRejected(){
        var r=BombRules.defaults();assertThrows(IllegalArgumentException.class,()->new BombRules(4,1,30,r.attackSpawn(),r.attackArea(),r.defenseSpawn(),r.defenseArea(),r.pointA(),r.pointB()).validate());
    }
}
