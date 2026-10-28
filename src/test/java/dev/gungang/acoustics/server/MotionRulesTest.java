package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MotionRulesTest {
    @Test void jumpPeaksFollowVanillaGravity(){
        for(double height:new double[]{8,16}){
            double velocity=MotionRules.jumpVelocity(height),y=0,peak=0;
            for(int tick=0;tick<100;tick++){y+=velocity;peak=Math.max(peak,y);velocity=(velocity-.08)*.98;}
            assertEquals(height,peak,1e-8);
        }
    }
    @Test void persistedSpeedIsBounded(){assertEquals(1,MotionRules.speedStep(-100));assertEquals(30,MotionRules.speedStep(100));assertEquals(10,MotionRules.speedStep(10));}
    @Test void curvesMeetEdgesAndReverseExactly(){
        for(int[] out:new int[][]{{1,0},{-1,0},{0,1}}){
            assertArrayEquals(new double[]{0,-.5},MotionRules.point(0,-1,out[0],out[1],0),1e-9);
            assertArrayEquals(new double[]{out[0]*.5,out[1]*.5},MotionRules.point(0,-1,out[0],out[1],1),1e-9);
            for(int i=0;i<=100;i++){double t=i/100d;assertArrayEquals(MotionRules.point(0,-1,out[0],out[1],t),MotionRules.point(out[0],out[1],0,-1,1-t),1e-9);}
        }
    }
}
