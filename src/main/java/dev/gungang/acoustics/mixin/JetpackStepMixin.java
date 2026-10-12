package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.SurvivalGadgets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuse collision-safe stepping on both sides even while no-gravity flight clears onGround. */
@Mixin(Entity.class)
public abstract class JetpackStepMixin {
    @Redirect(method="collide",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;onGround()Z"))
    private boolean gungang$jetpackCanStep(Entity entity) {
        return entity.onGround()||gungang$jetting(entity);
    }

    @Redirect(method="collide",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getStepHeight()F",remap=false))
    private float gungang$jetpackStepHeight(Entity entity) {
        return gungang$jetting(entity)?.5f:entity.getStepHeight();
    }

    private static boolean gungang$jetting(Entity entity) {
        return entity instanceof LivingEntity living&&SurvivalGadgets.jetting(living);
    }
}
