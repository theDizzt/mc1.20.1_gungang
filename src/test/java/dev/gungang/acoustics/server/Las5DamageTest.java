package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Las5DamageTest {
    @Test void scalesWithGauge(){
        assertEquals(2,Las5Damage.damage(0,100));
        assertEquals(3.18f,Las5Damage.damage(59,100),.00001f);
        assertEquals(63.6f,20*Las5Damage.damage(59,100),.0001f);
        assertEquals(4,Las5Damage.damage(100,100));
        assertEquals(3.18f,Las5Damage.damage(118,200),.00001f);
    }
    @Test void clampsInvalidHeat(){
        assertEquals(2,Las5Damage.damage(-10,100));
        assertEquals(4,Las5Damage.damage(150,100));
        assertEquals(2,Las5Damage.damage(Float.NaN,100));
        assertEquals(2,Las5Damage.damage(50,0));
    }
}
