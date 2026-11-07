package dev.gungang.acoustics.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both TaCZ path searches skip a null hit result, so returning null lets bullets fly through custom NPCs. */
@Pseudo
@Mixin(targets="com.tacz.guns.util.EntityUtil",remap=false)
public abstract class TaczNpcPassMixin {
    @Inject(method="getHitResult",at=@At("HEAD"),cancellable=true,require=0)
    private static void gungang$npc(Projectile bullet,Entity target,Vec3 start,Vec3 end,CallbackInfoReturnable<Object> cir) {
        if(dev.gungang.acoustics.server.PropHuntMatch.bulletPasses(bullet,target))cir.setReturnValue(null);
    }
}
