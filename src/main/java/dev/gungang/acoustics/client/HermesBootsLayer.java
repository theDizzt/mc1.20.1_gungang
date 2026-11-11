package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.GadgetItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Wears the 74 Hermes' Boots secondary on both feet, like the jetpack on the back: the offhand is visible to every
 * client, so no sync is needed, and the item model hides itself in the hands (scale 0).
 * The model is one boot in leg pixels (scripts/import_hermes_boots.py): a 4x6x4 shaft centred on the model centre,
 * its toe toward -x and wings on +-z. Each leg gets one, following the leg's swing.
 */
public final class HermesBootsLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** Just clear of the 4-pixel leg (and a little of leggings), so the shaft does not z-fight the skin. */
    private static final float INFLATE = 1.12f;

    public HermesBootsLayer(RenderLayerParent<T, M> parent) { super(parent); }

    static ItemStack worn(LivingEntity entity) {
        if (entity.getOffhandItem().is(GadgetItems.HERMES_BOOTS.get())) return entity.getOffhandItem();
        if (entity.getMainHandItem().is(GadgetItems.HERMES_BOOTS.get())) return entity.getMainHandItem();
        return ItemStack.EMPTY;
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                                 float swing, float amount, float partial, float age, float yaw, float pitch) {
        ItemStack stack = worn(entity);
        if (stack.isEmpty() || entity.isInvisible()) return;
        boot(pose, buffers, light, entity, stack, getParentModel().rightLeg);
        boot(pose, buffers, light, entity, stack, getParentModel().leftLeg);
    }

    private static void boot(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, ItemStack stack, ModelPart leg) {
        if (!leg.visible) return;
        pose.pushPose();
        // Leg space: origin at the hip pivot, +y down the 12-pixel leg, -z toward the toes.
        leg.translateAndRotate(pose);
        pose.translate(0, 9 / 16.0, 0);
        // Model -> leg: (x, y, z) -> (z, -y, x): wings out to the sides, shaft upright, toe (-x) forward (-z).
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.scale(INFLATE, INFLATE, INFLATE);
        Minecraft.getInstance().getItemRenderer().renderStatic(entity, stack, ItemDisplayContext.NONE, false, pose, buffers,
                entity.level(), light, OverlayTexture.NO_OVERLAY, entity.getId());
        pose.popPose();
    }
}
