package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotBallisticsTest {
    @Test void slowerWeaponsNeedMoreLeadAndDropCompensation() {
        var fast=BotBallistics.flight(30,15,.05,1);
        var slow=BotBallistics.flight(30,3,.05,1);
        assertEquals(2,fast.ticks(),1e-8);assertEquals(10,slow.ticks(),1e-8);
        assertEquals(.05,fast.drop(),1e-8);assertEquals(2.25,slow.drop(),1e-8);
    }
    @Test void dragAndFractionalArrivalMatchDiscreteMotion() {
        var flight=BotBallistics.flight(2.5,2,.1,.5);
        assertEquals(1.5,flight.ticks(),1e-8);assertEquals(.05,flight.drop(),1e-8);
        assertTrue(BotBallistics.flight(20,2,.05,.99).ticks()>10);
    }
    @Test void unknownOrUnreachableFlightIsBounded() {
        assertEquals(0,BotBallistics.flight(30,0,0,1).ticks());
        assertEquals(80,BotBallistics.flight(100,1,.05,.5).ticks());
    }
}
