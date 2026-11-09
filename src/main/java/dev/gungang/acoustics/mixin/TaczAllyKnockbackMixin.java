package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.CombatRules;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Friendly fire (or zero damage) stops a TaCZ hit inside {@code hurt}, before any push; knockback guns still push. */
@Pseudo
@Mixin(targets="com.tacz.guns.entity.EntityKineticBullet",remap=false)
public abstract class TaczAllyKnockbackMixin {
    @Inject(method="onHitEntity",at=@At("HEAD"),require=0)
    private void gungang$allyKnockback(@Coerce EntityHitResult result,Vec3 start,Vec3 end,CallbackInfo ci) {
        CombatRules.gunKnockback((Projectile)(Object)this,result.getEntity());
    }
}
