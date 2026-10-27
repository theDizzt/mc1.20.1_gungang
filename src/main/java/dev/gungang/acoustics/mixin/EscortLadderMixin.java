package dev.gungang.acoustics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The escort cart's rear ladder is part of an entity model, so block-based climbing never sees it. */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class EscortLadderMixin {
    @Inject(method="onClimbable",at=@At("HEAD"),cancellable=true)
    private void gungang$cartLadder(CallbackInfoReturnable<Boolean> ci){
        if(dev.gungang.acoustics.server.EscortCart.onLadder((net.minecraft.world.entity.LivingEntity)(Object)this))ci.setReturnValue(true);
    }
}
