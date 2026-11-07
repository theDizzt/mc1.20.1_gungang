package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

public final class VoidAnchorRenderer extends EntityRenderer<VoidAnchorEntity> {
    private final ItemRenderer items;
    private final ItemStack spear=new ItemStack(GadgetItems.VOID_ANCHOR.get());
    public VoidAnchorRenderer(EntityRendererProvider.Context context) {super(context);items=context.getItemRenderer();}
    @Override public void render(VoidAnchorEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        if(entity.planted())pose.translate(0,.5,0);
        else {
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(-90+entity.getXRot()));
            pose.translate(0,.5,0);
        }
        items.renderStatic(spear,ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(VoidAnchorEntity entity) {return TextureAtlas.LOCATION_BLOCKS;}
}
