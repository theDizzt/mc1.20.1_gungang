package dev.gungang.acoustics.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** One visual event per actual attack, including misses and the wielder's third-person view. */
public final class BladeSwings {
    public static void send(LivingEntity owner,int motion){
        if(!(owner.level() instanceof ServerLevel level))return;
        var packet=new GungangNetwork.BladeSwing(owner.getId(),motion,owner.getYRot(),owner.getXRot());
        for(var viewer:level.players())if(viewer.distanceToSqr(owner)<48*48)GungangNetwork.send(viewer,packet);
    }
    private BladeSwings(){}
}
