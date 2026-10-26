package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RapierEnergyTest {
    @Test void delayAndRecoveryCadence(){
        var e=new RapierEnergy(100,0).spend();assertEquals(65,e.amount());
        for(int i=0;i<59;i++)e=e.tick();assertEquals(65,e.amount());
        e=e.tick();assertEquals(66,e.amount());
        for(int i=0;i<3;i++)e=e.tick();assertEquals(66,e.amount());
        assertEquals(67,e.tick().amount());
    }
    @Test void consecutiveStepsDoNotOverdrawAndRestartDelay(){
        var e=new RapierEnergy(100,0).spend();for(int i=0;i<70;i++)e=e.tick();
        e=e.spend();assertEquals(60,e.waitTicks());assertEquals(33,e.amount());
        assertFalse(e.canStep());assertEquals(e,e.spend());
        for(int i=0;i<1000;i++)e=e.tick();assertEquals(new RapierEnergy(100,0),e);
    }
}
