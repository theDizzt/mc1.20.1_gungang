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
    @Test void repeatedCallbacksCannotStackHealing(){
        var clock=new RegenerationClock(0);
        assertTrue(clock.tick(100,true));
        for(int i=0;i<10;i++)assertFalse(clock.tick(100,true));
        assertFalse(clock.tick(119,true));assertTrue(clock.tick(120,true));
        assertFalse(clock.tick(120,true));
    }
    @Test void continuousDamageAlwaysPostponesRecovery(){
        var clock=new RegenerationClock(0);
        for(int i=0;i<400;i++){if(i%2==0)clock.damage(i);assertFalse(clock.tick(i,true));}
        assertFalse(clock.tick(497,true));assertTrue(clock.tick(498,true));
    }
    @Test void directHealthLossAlsoResetsTheWait(){
        var clock=new RegenerationClock(0);
        assertFalse(clock.tick(0,10,20));
        assertFalse(clock.tick(99,9,20));
        assertFalse(clock.tick(198,9,20));assertTrue(clock.tick(199,9,20));
        assertFalse(clock.tick(200,10,20));assertTrue(clock.tick(219,10,20));
    }
    @Test void damageEventDoesNotGetDelayedAgainByHealthObservation(){
        var clock=new RegenerationClock(0);clock.tick(0,10,20);
        clock.damage(40);assertFalse(clock.tick(41,9,20));
        assertFalse(clock.tick(139,9,20));assertTrue(clock.tick(140,9,20));
    }
}
