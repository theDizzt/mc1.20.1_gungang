package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.CombatRules;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TaCZ Tweaks can skip the base hit handler; knockback gun pellets observe its resolved entity collision too. */
@Pseudo
@Mixin(targets="me.muksc.tacztweaks.data.manager.BulletInteractionManager",remap=false)
public abstract class TaczWindInteractionMixin {
    @Inject(method="handleEntityInteraction",at=@At("HEAD"),require=1)
    private void gungang$knockback(@Coerce Object bullet,@Coerce Object hit,ClipContext context,CallbackInfoReturnable<Object> cir){
        if(bullet instanceof Projectile projectile&&CombatRules.knockbackGun(bullet)&&hit instanceof EntityHitResult result)
            CombatRules.gunKnockback(projectile,result.getEntity());
    }
}
