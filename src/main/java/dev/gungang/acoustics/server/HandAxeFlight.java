package dev.gungang.acoustics.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Launch state shared by the actual throw and the local guide. */
public final class HandAxeFlight {
    public static Vec3 origin(LivingEntity owner){return owner.getEyePosition().add(0,-.1,0);}
    public static Vec3 velocity(LivingEntity owner,double distance,double arc){
        var direction=Vec3.directionFromRotation(HandAxeRules.pitch(owner.getXRot(),arc),owner.getYRot()).normalize();
        var motion=owner.getDeltaMovement();
        return direction.scale((float)HandAxeRules.speed(distance,arc)).add(motion.x,owner.onGround()?0:motion.y,motion.z);
    }
    /** ThrowableProjectile applies drag/gravity after moving, using its pre-move water state. */
    public static Vec3 nextVelocity(Vec3 velocity,boolean water){return velocity.scale(water?(double).8f:HandAxeRules.DRAG).add(0,-HandAxeRules.GRAVITY,0);}
    private HandAxeFlight(){}
}
