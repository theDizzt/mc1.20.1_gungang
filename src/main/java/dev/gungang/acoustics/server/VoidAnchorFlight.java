package dev.gungang.acoustics.server;

import net.minecraft.world.phys.Vec3;

/** Shared by the server projectile and the local landing preview. */
public final class VoidAnchorFlight {
    public static Vec3 launch(Vec3 look) {
        return look.scale(2).add(0,.4,0);
    }
    public static Vec3 step(Vec3 velocity,double distance) {
        if(distance>=40-1e-7)return new Vec3(0,Math.min(-.5,velocity.y),0);
        double horizontal=velocity.horizontalDistance(),remaining=Math.max(0,40-distance);
        double factor=horizontal>remaining&&horizontal>0?remaining/horizontal:1;
        return new Vec3(velocity.x*factor,velocity.y,velocity.z*factor);
    }
    public static Vec3 next(Vec3 velocity,double distance) {
        double falling=Math.max(-2,velocity.y-.05);
        // Even an upward throw must descend as soon as its horizontal range is exhausted.
        if(distance>=40-1e-7)return new Vec3(0,Math.min(-.5,falling),0);
        return new Vec3(velocity.x,falling,velocity.z);
    }
    private VoidAnchorFlight() { }
}
