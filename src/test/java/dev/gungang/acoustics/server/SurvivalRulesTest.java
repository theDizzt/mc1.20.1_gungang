package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SurvivalRulesTest {
    @Test void reserveDrainsExactlyAfterThirtyIntervalsWithoutNegativeHealth() {
        float reserve=30;
        for(int interval=0;interval<29;interval++)reserve=SurvivalRules.drain(reserve);
        assertEquals(1,reserve);assertEquals(0,SurvivalRules.drain(reserve));assertEquals(0,SurvivalRules.drain(.5f));
        assertFalse(SurvivalRules.recovered(SurvivalRules.LIFE_HEALTH));assertTrue(SurvivalRules.recovered(1));
    }
}
