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
public abstract class TaczDamageMixin implements dev.gungang.acoustics.server.Las5Damage.BulletAccess {
    @org.spongepowered.asm.mixin.Shadow public abstract net.minecraft.resources.ResourceLocation getGunId();
    @org.spongepowered.asm.mixin.Shadow private java.util.LinkedList<?> damageAmount;
    @org.spongepowered.asm.mixin.Unique private static java.lang.reflect.Method gungang$pairDamage;
    // Change only the distance used for falloff; retain the actual hit position for spawn protection.
    @org.spongepowered.asm.mixin.injection.Redirect(method="getDamage",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D",remap=true),require=1)
    private double gungang$penetrationDistance(Vec3 hit,Vec3 origin){
        return dev.gungang.acoustics.server.M95.ignoresFalloff((Projectile)(Object)this)?0:hit.distanceTo(origin);
    }
    @Inject(method="getDamage",at=@At("RETURN"),cancellable=true,require=0)
    private void gungang$damage(Vec3 position,CallbackInfoReturnable<Float> cir) {
        var bullet=(Projectile)(Object)this;
        if(bullet.level().isClientSide)return;
        if(dev.gungang.acoustics.server.SpawnGateProtection.bulletBlocked(bullet,position)){
            bullet.getPersistentData().putBoolean(dev.gungang.acoustics.server.SpawnGateProtection.BLOCKED,true);bullet.discard();cir.setReturnValue(0f);return;
        }
        double multiplier=AdrenalineDamage.multiplier(bullet.getOwner());
        bullet.getPersistentData().putDouble("GunGangRawDamageMultiplier",multiplier);
        float damage=cir.getReturnValue();
        if(dev.gungang.acoustics.server.M95.bullet(bullet)&&!damageAmount.isEmpty()){
            // Preserve TaCZ's distance curve, attachment modifiers and scripts as ratios to its first damage band.
            // Normal and piercing bullets snapshot their independently configured base when fired.
            try{
                Object first=damageAmount.getFirst();
                if(gungang$pairDamage==null)gungang$pairDamage=first.getClass().getMethod("getDamage");
                double base=((Number)gungang$pairDamage.invoke(first)).doubleValue();
                var tag=bullet.getPersistentData();
                double configured=tag.contains(dev.gungang.acoustics.server.M95.DAMAGE)?tag.getDouble(dev.gungang.acoustics.server.M95.DAMAGE):dev.gungang.acoustics.server.ExtraGadgetConfig.m95.damage();
                if(base>0)damage=(float)(damage*configured/base);
            }catch(ReflectiveOperationException error){throw new IllegalStateException("TaCZ damage band API changed",error);}
        }
        cir.setReturnValue((float)(dev.gungang.acoustics.server.Las5Damage.baseDamage(bullet,damage)*multiplier));
    }
}
