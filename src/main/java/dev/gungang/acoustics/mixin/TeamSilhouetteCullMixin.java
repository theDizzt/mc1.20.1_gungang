package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.TeamSilhouette;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla stops drawing players past about 64 blocks (bounding box size x 64 x entity distance), even while the
 * server still tracks them; a teammate's silhouette would vanish there. Keep allies drawn while they are on screen.
 */
@Mixin(EntityRenderer.class)
public abstract class TeamSilhouetteCullMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void keepTeammate(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> result) {
        if (TeamSilhouette.farVisible(entity, frustum)) result.setReturnValue(true);
    }
}
