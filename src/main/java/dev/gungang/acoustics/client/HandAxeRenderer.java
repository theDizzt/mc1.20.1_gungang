package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.HandAxeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** The thrown axe tumbles end over end along its flight direction. */
public final class HandAxeRenderer extends EntityRenderer<HandAxeEntity> {
    private final ItemRenderer items;
    public HandAxeRenderer(EntityRendererProvider.Context context) {super(context);items=context.getItemRenderer();}
    @Override public void render(HandAxeEntity axe,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        pose.translate(0,axe.embedded()?0:.15,0);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial,axe.yRotO,axe.getYRot())-90));
        pose.mulPose(Axis.ZP.rotationDegrees(axe.embedded()?-axe.getXRot():-(axe.tickCount+partial)*40));
        items.renderStatic(axe.getItem(),ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,axe.level(),axe.getId());
        pose.popPose();
        super.render(axe,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(HandAxeEntity entity) {return TextureAtlas.LOCATION_BLOCKS;}
}
