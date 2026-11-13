package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.TaczExplosionDamage;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DamageSource.class)
public abstract class TaczExplosionSourceMixin implements TaczExplosionDamage {
    @Unique private boolean gungang$taczExplosion;
    @Unique private float gungang$explosionDamage;

    @Override public void gungang$explosionHit(float amount){
        gungang$taczExplosion=true;
        gungang$explosionDamage=amount;
    }
    @Override public boolean gungang$isTaczExplosion(){return gungang$taczExplosion;}
    @Override public float gungang$explosionAmount(float fallback){
        return gungang$taczExplosion?gungang$explosionDamage:fallback;
    }
    @Inject(method="scalesWithDifficulty",at=@At("HEAD"),cancellable=true)
    private void gungang$noPlayerDifficulty(CallbackInfoReturnable<Boolean> cir){
        if(gungang$isTaczExplosion())cir.setReturnValue(false);
    }
}
