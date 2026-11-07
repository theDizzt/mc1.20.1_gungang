package dev.gungang.acoustics.mixin;

import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * lrtactical's cone hitbox collects every entity in range (items, bullets, holograms, paintings, NPC decor ...) and the
 * attack request keeps only the first {@code melee_max_target} of them in no particular order. The katana's 360° spin
 * (29, right click) therefore often sent no player or bot at all and hit nobody. Only what a blade can hurt is kept,
 * nearest first.
 */
@Pseudo
@Mixin(targets="me.xjqsh.lrtactical.api.collision.ConeFilter",remap=false)
public abstract class MeleeConeTargetMixin {
    @Inject(method="filterTargets",at=@At("RETURN"),cancellable=true,require=0)
    private void gungang$livingOnly(LivingEntity attacker,Vec3 eye,Vec3 look,CallbackInfoReturnable<List<Entity>> ci){
        var targets=ci.getReturnValue();if(targets==null||targets.isEmpty())return;
        ci.setReturnValue(targets.stream()
                .filter(e->e instanceof LivingEntity living&&living.isAlive()&&!living.isSpectator()||e instanceof dev.gungang.acoustics.server.PropEntity)
                .sorted(Comparator.comparingDouble(e->e.getBoundingBox().getCenter().distanceToSqr(eye)))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
    }
}
