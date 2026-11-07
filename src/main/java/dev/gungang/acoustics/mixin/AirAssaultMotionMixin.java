package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.AirAssaultMotion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class AirAssaultMotionMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void gungang$airAssaultTravel(Vec3 input,CallbackInfo ci) {
        var entity=(LivingEntity)(Object)this;
        // The spear moves tethered mobs through collision resolution; AI must not add a second movement.
        if(!(entity instanceof Player)&&!entity.level().isClientSide&&entity.getPersistentData().hasUUID("VoidAnchorTether")) {
            entity.setDeltaMovement(Vec3.ZERO);ci.cancel();return;
        }
        if((Object)this instanceof Player p&&(dev.gungang.acoustics.server.VoidAnchorPull.travel(p)||AirAssaultMotion.travel(p)))ci.cancel();
    }
    @Inject(method="jumpFromGround",at=@At("HEAD"),cancellable=true)
    private void gungang$airAssaultJump(CallbackInfo ci) {
        if((Object)this instanceof Player p&&(dev.gungang.acoustics.server.VoidAnchorPull.active(p)||AirAssaultMotion.active(p)))ci.cancel();
    }
}
