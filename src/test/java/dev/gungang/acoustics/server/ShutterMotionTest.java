package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShutterMotionTest {
    @Test void opensAndClosesAtOneBlockPerSecond(){
        assertEquals(.5,ShutterMotion.lift(0,10,true,3),1e-8);
        assertEquals(3,ShutterMotion.lift(0,1000,true,3),1e-8);
        assertEquals(2.5,ShutterMotion.lift(3,10,false,3),1e-8);
        assertEquals(0,ShutterMotion.lift(3,1000,false,3),1e-8);
    }
    @Test void reversesFromCurrentPositionWithoutJump(){
        double current=ShutterMotion.lift(0,17,true,4);
        assertEquals(current,ShutterMotion.lift(current,0,false,4),1e-8);
        assertEquals(current-.25,ShutterMotion.lift(current,5,false,4),1e-8);
    }
    @Test void bottomRowsClearBeforeHigherRows(){
        assertEquals(1,ShutterMotion.bottom(100,100,1.5));
        assertEquals(.5,ShutterMotion.bottom(100,101,1.5));
        assertEquals(0,ShutterMotion.bottom(100,102,1.5));
        assertEquals(1,ShutterMotion.bottom(100,102,3));
    }
    @Test void fractionalFramesInterpolateAndNegativeElapsedDoesNotRewind(){
        assertEquals(.525,ShutterMotion.lift(0,10.5,true,2),1e-8);
        assertEquals(.5,ShutterMotion.lift(.5,-5,true,2),1e-8);
    }
}
