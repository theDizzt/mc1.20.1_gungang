package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gungang.acoustics.client.TeamSilhouette;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class TeamSilhouetteMixin {
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void silhouette(EntityModel<?> model, PoseStack pose, VertexConsumer consumer,
                            int light, int overlay, float red, float green, float blue, float alpha,
                            LivingEntity entity, float yaw, float partialTick, PoseStack renderPose,
                            MultiBufferSource buffers, int packedLight) {
        TeamSilhouette.capture(entity, model, pose);
        model.renderToBuffer(pose, consumer, light, overlay, red, green, blue, alpha);
    }
}
