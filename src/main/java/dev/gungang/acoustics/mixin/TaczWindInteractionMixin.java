package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.CombatRules;
import dev.gungang.acoustics.server.Las5Damage;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TaCZ Tweaks can skip the base hit handler; observe its resolved entity collision too. */
@Pseudo
@Mixin(targets="me.muksc.tacztweaks.data.manager.BulletInteractionManager",remap=false)
public abstract class TaczWindInteractionMixin {
    @Inject(method="handleEntityInteraction",at=@At("HEAD"),require=1)
    private void gungang$wind(@Coerce Object bullet,@Coerce Object hit,ClipContext context,CallbackInfoReturnable<Object> cir){
        if(bullet instanceof Projectile projectile&&bullet instanceof Las5Damage.BulletAccess access
                &&"bf1:wex".equals(String.valueOf(access.getGunId()))&&hit instanceof EntityHitResult result)
            CombatRules.gunKnockback(projectile,result.getEntity());
    }
}
