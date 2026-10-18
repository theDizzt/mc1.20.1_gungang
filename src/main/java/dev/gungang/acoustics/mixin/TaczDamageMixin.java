package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.AdrenalineDamage;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TaCZ targets read getDamage directly without firing LivingDamageEvent. */
@Pseudo
@Mixin(targets="com.tacz.guns.entity.EntityKineticBullet",remap=false)
public abstract class TaczDamageMixin {
    @Inject(method="getDamage",at=@At("RETURN"),cancellable=true,require=0)
    private void gungang$damage(Vec3 position,CallbackInfoReturnable<Float> cir) {
        var bullet=(Projectile)(Object)this;
        if(bullet.level().isClientSide)return;
        if(dev.gungang.acoustics.server.SpawnGateProtection.bulletBlocked(bullet,position)){
            bullet.getPersistentData().putBoolean(dev.gungang.acoustics.server.SpawnGateProtection.BLOCKED,true);bullet.discard();cir.setReturnValue(0f);return;
        }
        double multiplier=AdrenalineDamage.multiplier(bullet.getOwner());
        bullet.getPersistentData().putDouble("GunGangRawDamageMultiplier",multiplier);
        cir.setReturnValue((float)(cir.getReturnValue()*multiplier));
    }
}
