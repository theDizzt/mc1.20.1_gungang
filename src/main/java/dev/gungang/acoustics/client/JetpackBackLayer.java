package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.GadgetItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Wears the jetpack secondary on the back like a backpack. The secondary sits in the offhand, which every client
 * sees, so no extra sync is needed; the item model hides itself in the hands (scale 0) so it is not drawn twice.
 */
public final class JetpackBackLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** The model's tallest side (11 px of the GLB) is normalized to one block; this restores its pixel scale. */
    private static final float SCALE = 11 / 16f;

    public JetpackBackLayer(RenderLayerParent<T, M> parent) { super(parent); }

    static ItemStack worn(LivingEntity entity) {
        if (entity.getOffhandItem().is(GadgetItems.JETPACK.get())) return entity.getOffhandItem();
        if (entity.getMainHandItem().is(GadgetItems.JETPACK.get())) return entity.getMainHandItem();
        return ItemStack.EMPTY;
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                                 float swing, float amount, float partial, float age, float yaw, float pitch) {
        ItemStack stack = worn(entity);
        if (stack.isEmpty() || entity.isInvisible()) return;
        pose.pushPose();
        // Body space: origin at the neck, +y down, +z behind the back (the body's back face is at z = 2 px).
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0, 6.5 / 16, 3.0 / 16);
        // Model space -> body space: tanks (-x) face outward (+z), straps (+x) toward the body, +y up, width along x.
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        // The model's centre is where the tanks meet the straps, so that plane lands just behind the back (and
        // clear of a chestplate); NONE applies no display transform.
        pose.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(entity, stack, ItemDisplayContext.NONE, false, pose, buffers,
                entity.level(), light, OverlayTexture.NO_OVERLAY, entity.getId());
        pose.popPose();
    }
}
