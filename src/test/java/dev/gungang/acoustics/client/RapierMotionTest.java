package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RapierMotionTest {
    @Test void bothAttacksReturnExactlyToTheSameGuard(){
        var guard=RapierMotion.sample(false,0);
        assertEquals(guard,RapierMotion.sample(false,1));
        assertEquals(guard,RapierMotion.sample(true,0));
        assertEquals(guard,RapierMotion.sample(true,1));
    }
    @Test void thrustDrawsBackThenExtendsAlongTheSightLine(){
        var guard=RapierMotion.sample(false,0);var draw=RapierMotion.sample(false,.16f);
        var hit=RapierMotion.sample(false,.36f);
        assertTrue(draw.z()>guard.z());assertTrue(hit.z()<guard.z()-.5f);
        assertTrue(Math.abs(hit.x())<guard.x()/2);assertEquals(0,hit.yaw(),.001f);
        assertEquals(hit,RapierMotion.sample(false,.46f));
    }
    @Test void dashSweepsOutwardInsteadOfRepeatingThrust(){
        var gather=RapierMotion.sample(true,.18f);var beat=RapierMotion.sample(true,.56f);
        assertTrue(beat.x()>gather.x()+.4f);assertTrue(beat.yaw()<gather.yaw()-70);
        assertTrue(beat.bodyYaw()>0);assertTrue(beat.armYaw()<-.8f);
    }
    @Test void extendedThrustTurnsTheBladeAlongTheForearm(){
        assertEquals(0,RapierMotion.wrist(false,0),.001f);
        var blade=new org.joml.Matrix4f().rotateX(RapierMotion.sample(false,.36f).armPitch())
                .rotateX(-(float)Math.PI/2).rotateY((float)Math.PI)
                .rotateX((float)Math.toRadians(RapierMotion.wrist(false,.36f)))
                .transformDirection(new org.joml.Vector3f(0,1,0));
        assertTrue(blade.z<-.99f,"The blade must point forward, not back through the player's torso");
        assertTrue(Math.abs(blade.y)<.01f);
        assertEquals(0,RapierMotion.wrist(false,1),.001f);
    }
    @Test void samplingIsContinuousAtEveryFrame(){
        for(boolean sweep:new boolean[]{false,true})for(int i=1;i<=1000;i++){
            var a=RapierMotion.sample(sweep,(i-1)/1000f);var b=RapierMotion.sample(sweep,i/1000f);
            assertTrue(Math.abs(a.x()-b.x())<.02f);assertTrue(Math.abs(a.z()-b.z())<.02f);
            assertTrue(Math.abs(a.yaw()-b.yaw())<2);
        }
    }
}
