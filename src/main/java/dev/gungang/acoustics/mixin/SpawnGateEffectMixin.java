package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.SpawnGateProtection;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Splash/lingering harmful effects must respect the gate just like immediate damage. */
@Mixin(LivingEntity.class)
public abstract class SpawnGateEffectMixin {
    @Inject(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void gungang$gateEffect(MobEffectInstance effect,Entity source,CallbackInfoReturnable<Boolean> result){
        var target=(LivingEntity)(Object)this;
        if(!target.level().isClientSide&&!effect.getEffect().isBeneficial()&&SpawnGateProtection.separates(source,target))result.setReturnValue(false);
    }
}
