package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.RapierDash;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class RapierDashMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void gungang$dashTravel(Vec3 input,CallbackInfo ci){
        if((Object)this instanceof Player p&&RapierDash.travel(p))ci.cancel();
    }
    @Inject(method="jumpFromGround",at=@At("HEAD"),cancellable=true)
    private void gungang$dashJump(CallbackInfo ci){
        if((Object)this instanceof Player p&&RapierDash.active(p))ci.cancel();
    }
}
