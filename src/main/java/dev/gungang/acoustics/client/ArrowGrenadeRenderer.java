package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.ArrowGrenadeEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

public final class ArrowGrenadeRenderer extends EntityRenderer<ArrowGrenadeEntity> {
    private final ItemRenderer items;
    public ArrowGrenadeRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemRenderer();shadowRadius=.15f;}
    @Override public void render(ArrowGrenadeEntity grenade,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.scale(.45f,.45f,.45f);
        pose.mulPose(Axis.YP.rotationDegrees((grenade.tickCount+partial)*8));
        if(!grenade.active())pose.mulPose(Axis.XP.rotationDegrees((grenade.tickCount+partial)*20));
        items.renderStatic(grenade.getItem(),ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,grenade.level(),grenade.getId());
        pose.popPose();super.render(grenade,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(ArrowGrenadeEntity grenade){return TextureAtlas.LOCATION_BLOCKS;}
}
