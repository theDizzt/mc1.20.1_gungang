package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandAxeRulesTest {
    @org.junit.jupiter.api.Test void headAndBodyDamage() {
        assertEquals(66,HandAxeRules.damage(66,false));
        assertEquals(99,HandAxeRules.damage(66,true));
        assertTrue(HandAxeRules.headshot(1.62,0,1.8,1.62));
        assertFalse(HandAxeRules.headshot(1.0,0,1.8,1.62));
        assertTrue(HandAxeRules.headshot(11.62,10,1.8,11.62));
        assertFalse(HandAxeRules.headshot(12,10,1.8,11.62));
    }
    @Test void chargeScalesDistanceWithinBounds() {
        assertEquals(5,HandAxeRules.distance(0,5,16,1),1e-9);
        assertEquals(10.5,HandAxeRules.distance(10,5,16,1),1e-9);
        assertEquals(16,HandAxeRules.distance(20,5,16,1),1e-9);
        assertEquals(16,HandAxeRules.distance(400,5,16,1),1e-9);
    }
    @Test void levelThrowLandsAtRequestedDistance() {
        for(double arc:new double[]{0,30})for(double d:new double[]{5,10.5,16,24})assertEquals(d,HandAxeRules.range(HandAxeRules.speed(d,arc),arc),.01);
        assertTrue(HandAxeRules.speed(16,30)>HandAxeRules.speed(5,30));
    }
    @Test void arcRaisesLaunchWithoutPassingVertical() {
        assertEquals(-30,HandAxeRules.pitch(0,30),1e-6);
        assertEquals(-89,HandAxeRules.pitch(-80,30),1e-6);
    }
}
