package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RapierEnergyTest {
    private static final int COST=RapierEnergy.DEFAULT_COST;
    @Test void delayAndRecoveryCadence(){
        var e=new RapierEnergy(100,0).spend(COST);assertEquals(67,e.amount());
        for(int i=0;i<59;i++)e=e.tick();assertEquals(67,e.amount());
        e=e.tick();assertEquals(68,e.amount());
        e=e.tick();assertEquals(68,e.amount());
        assertEquals(69,e.tick().amount());
    }
    @Test void consecutiveStepsDoNotOverdrawAndRestartDelay(){
        var e=new RapierEnergy(100,0).spend(COST).spend(COST).spend(COST);assertEquals(60,e.waitTicks());assertEquals(1,e.amount());
        assertFalse(e.canStep(COST));assertEquals(e,e.spend(COST));
        for(int i=0;i<1000;i++)e=e.tick();assertEquals(new RapierEnergy(100,0),e);
    }
}
