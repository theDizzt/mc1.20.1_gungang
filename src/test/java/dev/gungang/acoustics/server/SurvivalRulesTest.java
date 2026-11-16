package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SurvivalRulesTest {
    @Test void passiveDecayStopsAtOne() {
        float reserve=30;
        for(int interval=0;interval<29;interval++)reserve=SurvivalRules.drain(reserve);
        assertEquals(1,reserve);assertEquals(1,SurvivalRules.drain(reserve));
        assertFalse(SurvivalRules.recovered(SurvivalRules.LIFE_HEALTH));assertTrue(SurvivalRules.recovered(1));
    }
    @Test void damageDuringSupportDoesNotMakeLaterDecayLethal() {
        float reserve=50;
        for(int interval=0;interval<20;interval++)reserve=SurvivalRules.drain(reserve);
        assertEquals(30,reserve);
        reserve-=19;
        for(int interval=0;interval<29;interval++)reserve=SurvivalRules.drain(reserve);
        assertEquals(1,reserve);
    }
    @Test void decayDoesNotRestoreReserveSpentByExternalDamage() {
        assertEquals(0,SurvivalRules.drain(0));
        assertEquals(.5f,SurvivalRules.drain(.5f));
    }
}
