package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotRouteMemoryTest {
    @Test void repeatedHitsIncreaseRiskButItDecaysAndExpires() {
        var memory=new BotRouteMemory();memory.hit(0,64,0,.1,100);
        double first=memory.risk(0,64,0,100);
        memory.hit(1,64,0,.1,110);
        assertTrue(memory.risk(1,64,0,110)>first);
        assertTrue(memory.risk(1,64,0,410)<memory.risk(1,64,0,110));
        assertEquals(0,memory.risk(1,64,0,710));
    }
    @Test void riskIsLocalToItsFloorAndClearRemovesIt() {
        var memory=new BotRouteMemory();memory.hit(0,64,0,.5,0);
        assertTrue(memory.risk(0,64,0,0)>memory.risk(4,64,0,0));
        assertEquals(0,memory.risk(6,64,0,0));
        assertEquals(0,memory.risk(0,67,0,0));
        memory.clear();assertEquals(0,memory.risk(0,64,0,0));
    }
    @Test void memoryIsBoundedAndRejectsNonDamage() {
        var memory=new BotRouteMemory();memory.hit(0,64,0,0,0);memory.hit(0,64,0,Double.NaN,0);
        assertEquals(0,memory.risk(0,64,0,0));
        for(int i=0;i<=BotRouteMemory.LIMIT;i++)memory.hit(i*10,64,0,.1,i);
        assertEquals(0,memory.risk(0,64,0,BotRouteMemory.LIMIT));
        assertTrue(memory.risk(BotRouteMemory.LIMIT*10,64,0,BotRouteMemory.LIMIT)>0);
    }
}
