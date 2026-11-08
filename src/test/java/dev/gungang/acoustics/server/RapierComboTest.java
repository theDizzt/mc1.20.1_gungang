package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RapierComboTest {
    @Test void successfulHitsUseAllFiveDamageTiersAndCap(){
        var combo=new RapierCombo(0,0);int now=100;
        for(int damage:new int[]{27,33,40,48,57,57,57}){
            combo=combo.hit(now);assertEquals(damage,RapierCombo.damage(combo.stacks()));
            assertEquals(now+60,combo.expires());now+=6;
        }
    }
    @Test void eachHitRefreshesThreeSecondsIncludingAtCap(){
        var combo=new RapierCombo(5,160).hit(159);
        assertEquals(5,combo.active(218));assertEquals(0,combo.active(219));
        assertEquals(1,combo.hit(219).stacks());assertEquals(279,combo.hit(219).expires());
    }
    @Test void missDoesNotRefreshAndClockResetCannotKeepStacks(){
        var combo=new RapierCombo(3,160);
        assertEquals(3,combo.active(159));assertEquals(0,combo.active(160));
        assertEquals(0,combo.active(10));assertEquals(1,combo.hit(10).stacks());
    }
}
