package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RapierEnergyTest {
    private static final int COST=RapierEnergy.DEFAULT_COST;
    @Test void delayAndRecoveryCadence(){
        int left=100-COST;
        var e=new RapierEnergy(100,0).spend(COST);assertEquals(left,e.amount());
        for(int i=0;i<59;i++)e=e.tick();assertEquals(left,e.amount());
        e=e.tick();assertEquals(left+1,e.amount());
        e=e.tick();assertEquals(left+1,e.amount());
        assertEquals(left+2,e.tick().amount());
    }
    @Test void consecutiveStepsDoNotOverdrawAndRestartDelay(){
        // Dash until the gauge cannot pay another one: whatever the cost, it never goes below zero.
        int dashes=100/COST;
        var e=new RapierEnergy(100,0);
        for(int i=0;i<dashes+1;i++)e=e.spend(COST);
        assertEquals(60,e.waitTicks());assertEquals(100-dashes*COST,e.amount());
        assertFalse(e.canStep(COST));assertEquals(e,e.spend(COST));
        for(int i=0;i<1000;i++)e=e.tick();assertEquals(new RapierEnergy(100,0),e);
    }
}
