package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentBalanceTest {
    private final ExtraGadgetConfig.M95 sniper=new ExtraGadgetConfig.M95(51,1.33,3,15,200,10,1,50,3,100,10,150,256,10);
    @Test void chargeBuysOnlyTheHighestAffordableTier(){
        double[] charges={0,9.99,10,49.99,50,99.99,100,149.99,150,200};
        double[] costs={0,0,10,10,50,50,100,100,150,150};
        int[] blocks={0,0,1,1,3,3,10,10,Integer.MAX_VALUE,Integer.MAX_VALUE};
        for(int i=0;i<charges.length;i++){
            var shot=M95ShotRules.select(charges[i],sniper);
            assertEquals(costs[i],shot.cost());assertEquals(blocks[i],shot.blocks());
        }
    }
    @Test void damageFalloffFollowsTheSelectedTierSetting(){
        assertFalse(M95ShotRules.select(99.99,sniper).ignoresFalloff());
        assertTrue(M95ShotRules.select(100,sniper).ignoresFalloff());
        assertTrue(M95ShotRules.select(200,sniper).ignoresFalloff());
        var custom=new ExtraGadgetConfig.M95(51,1.33,3,15,200,10,1,50,3,90,10,190,256,10);
        assertTrue(M95ShotRules.select(180,custom).ignoresFalloff());
    }
    @Test void eachPenetrationTierUsesItsOwnConfiguredPadding(){
        assertEquals(0,M95ShotRules.select(10,sniper).hitPadding());
        for(double charge:new double[]{50,100,150,200})assertEquals(.4,M95ShotRules.select(charge,sniper).hitPadding());
        var custom=new ExtraGadgetConfig.M95(51,1.33,3,15,200,10,1,50,3,100,10,150,256,10,.1,.25,.6,1.5);
        double[] charges={10,50,100,150},padding={.1,.25,.6,1.5};
        for(int i=0;i<charges.length;i++)assertEquals(padding[i],M95ShotRules.select(charges[i],custom).hitPadding());
        assertEquals(0,M95ShotRules.select(9,custom).hitPadding());
        assertEquals(.6,custom.withScopeDelay(15).thirdHitPadding());
    }
    @Test void squareZonesApplyOnlyTheInnermostDamageIncludingCorners(){
        var c=ExtraGadgetConfig.AIR_ASSAULT_DEFAULTS;
        assertEquals(60,AirAssaultRules.damage(0,0,c));assertEquals(60,AirAssaultRules.damage(-1,1,c));
        assertEquals(45,AirAssaultRules.damage(1.001,0,c));assertEquals(45,AirAssaultRules.damage(-2,-2,c));
        assertEquals(10,AirAssaultRules.damage(2.001,0,c));assertEquals(10,AirAssaultRules.damage(6,-6,c));
        assertEquals(-1,AirAssaultRules.damage(6.001,0,c));
    }
    @Test void zoneSizesAndDamageFollowModifiedValues(){
        var c=new ExtraGadgetConfig.AirAssault(4,80,8,30,16,5,2,25,.5,20,25,2);
        assertEquals(80,AirAssaultRules.damage(2,2,c));assertEquals(30,AirAssaultRules.damage(4,-4,c));
        assertEquals(5,AirAssaultRules.damage(8,8,c));assertEquals(-1,AirAssaultRules.damage(8.01,0,c));
    }
    @Test void diveHasSixStepsAtDefaultAndNeverDividesByZero(){
        for(int tick=1;tick<=6;tick++)assertEquals(7-tick,AirAssaultRules.remainingDiveTicks(.3,tick));
        assertEquals(1,AirAssaultRules.remainingDiveTicks(.3,20));
        assertEquals(10,AirAssaultRules.remainingDiveTicks(.5,1));
    }
    @Test void arrowIntervalAcceptsFractionalSecondsAndRoundsUp(){
        var c=new ExtraGadgetConfig.ArrowGrenade(20,8,5,30,.25,1.2,1.1);
        assertEquals(5,c.emissionIntervalTicks());
        assertEquals(2,new ExtraGadgetConfig.ArrowGrenade(20,8,5,30,.06,1.2,1.1).emissionIntervalTicks());
    }
}
