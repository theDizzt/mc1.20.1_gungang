package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.HandAxeItem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A hand axe chop needs a draw-back and recovery, so its swing lasts 4 ticks longer (6 -> 10 without haste). */
@Mixin(LivingEntity.class)
public abstract class HandAxeSwingMixin {
    @Inject(method="getCurrentSwingDuration",at=@At("RETURN"),cancellable=true)
    private void gungang$axeSwing(CallbackInfoReturnable<Integer> cir) {
        if(HandAxeItem.is(((LivingEntity)(Object)this).getMainHandItem()))cir.setReturnValue(cir.getReturnValueI()+4);
    }
}
