package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.BotEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive recovery has one clock, independent of regeneration potion/beacon amplifier. */
@Mixin(MobEffect.class)
public abstract class RegenerationEffectMixin {
    @Inject(method="applyEffectTick",at=@At("HEAD"),cancellable=true)
    private void gungang$oneRecoveryClock(LivingEntity entity,int amplifier,CallbackInfo ci){
        if((Object)this==MobEffects.REGENERATION&&(entity instanceof Player||entity instanceof BotEntity))ci.cancel();
    }
}
