package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.TurretEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;

/** Base and bearing stay grounded; the yoke, gun and barrel turn toward the target. */
public final class TurretRenderer extends EntityRenderer<TurretEntity> {
    public static final ResourceLocation BASE=new ResourceLocation("gungang","turret_base"),HEAD=new ResourceLocation("gungang","turret_head");
    public TurretRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.9f;}
    @Override public void render(TurretEntity turret,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.scale(2,2,2);
        part(BASE,pose,buffers,light);
        pose.mulPose(Axis.YP.rotationDegrees(-turret.aimYaw()));
        part(HEAD,pose,buffers,light);
        pose.popPose();
        if(!Minecraft.getInstance().options.hideGui){
            pose.pushPose();pose.translate(0,2.25,0);pose.mulPose(entityRenderDispatcher.cameraOrientation());pose.scale(-.025f,-.025f,.025f);
            var font=Minecraft.getInstance().font;String marker="▼ 터렛";
            font.drawInBatch(marker,-font.width(marker)/2f,0,turret.teamColor()|0xFF000000,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x60000000,15728880);
            pose.popPose();
        }
        super.render(turret,yaw,partial,pose,buffers,light);
    }
    private static void part(ResourceLocation id,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();pose.pushPose();pose.translate(-.5,0,-.5);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),null,
                mc.getModelManager().getModel(id),1,1,1,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(TurretEntity turret){return TextureAtlas.LOCATION_BLOCKS;}
}
