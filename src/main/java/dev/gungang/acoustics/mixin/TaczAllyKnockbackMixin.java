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
    @Inject(method="onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),require=1)
    private void gungang$allyKnockback(@Coerce Object result,Vec3 start,Vec3 end,CallbackInfo ci) {
        CombatRules.gunKnockback((Projectile)(Object)this,((EntityHitResult)result).getEntity());
    }
}
