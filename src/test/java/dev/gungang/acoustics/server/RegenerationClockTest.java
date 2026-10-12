package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegenerationClockTest {
    @Test void waitsFiveSecondsThenHealsEverySecond() {
        var clock=new RegenerationClock(0);
        for(int i=0;i<100;i++)assertFalse(clock.tick(i,true));
        assertTrue(clock.tick(100,true));
        for(int i=101;i<120;i++)assertFalse(clock.tick(i,true));
        assertTrue(clock.tick(120,true));
    }
    @Test void damageAndFullHealthRestartDelay() {
        var clock=new RegenerationClock(0);clock.damage(90);
        assertFalse(clock.tick(100,true));assertFalse(clock.tick(189,true));assertTrue(clock.tick(190,true));
        assertFalse(clock.tick(200,false));assertFalse(clock.tick(299,true));assertTrue(clock.tick(300,true));
    }
}
