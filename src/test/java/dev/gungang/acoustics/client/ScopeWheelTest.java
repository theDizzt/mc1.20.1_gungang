package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScopeWheelTest {
    @Test void stepsByMagnificationAndStopsAtTheEnds() {
        float[] mk5={5,25,1.25f};
        assertEquals(1,ScopeWheel.step(mk5,0,true));
        assertEquals(1,ScopeWheel.step(mk5,1,true));
        assertEquals(0,ScopeWheel.step(mk5,1,false));
        assertEquals(2,ScopeWheel.step(mk5,0,false));
        assertEquals(2,ScopeWheel.step(mk5,2,false));
        float[] elcan={4.25f,1.25f};
        assertEquals(1,ScopeWheel.step(elcan,0,false));
        assertEquals(0,ScopeWheel.step(elcan,1,true));
    }
}
