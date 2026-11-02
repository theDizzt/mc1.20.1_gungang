package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotBombDecisionTest {
    @Test void waitsForSafetyUnlessFuseIsUrgentOrWorkMostlyDone(){
        assertFalse(BotBombDecision.defuse(800,100,-1,false));
        assertTrue(BotBombDecision.defuse(800,100,-1,true));
        assertTrue(BotBombDecision.defuse(150,100,-1,false));
        assertTrue(BotBombDecision.defuse(500,100,60,false));
    }
    @Test void neverStartsWhenCompletionWouldBeTooLate(){
        assertFalse(BotBombDecision.defuse(100,100,-1,true));
        assertFalse(BotBombDecision.defuse(40,100,60,false));
        assertTrue(BotBombDecision.defuse(41,100,60,false));
    }
    @Test void plantingCommitsWhileSafeOrTimeIsRunningOut(){
        assertTrue(BotBombDecision.commit(false,800,100,10,true));
        assertFalse(BotBombDecision.commit(false,800,100,10,false));
        assertTrue(BotBombDecision.commit(false,180,100,-1,false));
        assertFalse(BotBombDecision.commit(false,800,100,-1,true));
    }
}
