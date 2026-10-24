package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;

import dev.gungang.acoustics.server.BotGadgetScore.Situation;
import org.junit.jupiter.api.Test;

class BotGadgetScoreTest {
    private static final float T=BotGadgetScore.THRESHOLD;
    private static float score(String kind,Situation s) {return BotGadgetScore.score(kind,s);}
    private static Situation fight(double distance,float health,long sinceHurt) {return new Situation(true,distance,health,0,sinceHurt,false,0,true);}
    private static Situation quiet(long sinceSeen,double goal) {return new Situation(false,Double.MAX_VALUE,1,sinceSeen,1000,true,goal,true);}

    @Test void throwablesPreferMidRange() {
        assertTrue(score("molotov",fight(13,1,1000))>T);
        assertTrue(score("molotov",fight(2,1,1000))<T);
        assertTrue(score("use",fight(30,1,1000))<T);
        assertEquals(0,score("molotov",quiet(0,0)));
    }
    @Test void trapsWaitForAQuietStretch() {
        assertTrue(score("landmine",quiet(20,0))<T);
        assertTrue(score("bear_trap",quiet(200,0))>T);
        assertEquals(0,score("landmine",fight(10,1,1000)));
    }
    @Test void coverAndShieldNeedPressure() {
        assertTrue(score("barrier",fight(10,.4f,10))>T);
        assertTrue(score("barrier",fight(10,.9f,10))<T);
        assertTrue(score("shield",fight(10,.4f,10))>T);
        assertEquals(0,score("shield",fight(10,.4f,200)));
    }
    @Test void healingScalesWithMissingHealth() {
        assertTrue(score("recovery_potion",fight(10,.3f,0))>T);
        assertTrue(score("recovery_potion",fight(10,.9f,0))<T);
    }
    @Test void scannerAndJetpackAreForEmptyStretches() {
        assertTrue(score("infrared_scanner",quiet(40,0))<T);
        assertTrue(score("infrared_scanner",quiet(120,0))>T);
        assertTrue(score("jetpack",quiet(0,40))>T);
        assertTrue(score("jetpack",quiet(0,12))<T);
    }
    @Test void lifeSupportIsPassive() {assertEquals(0,score("life_support",fight(5,.1f,0)));}
}
