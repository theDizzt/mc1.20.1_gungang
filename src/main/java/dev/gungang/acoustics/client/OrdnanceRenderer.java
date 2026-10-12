package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.OrdnanceEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class OrdnanceRenderer extends EntityRenderer<OrdnanceEntity> {
    private static final ResourceLocation BEAM=new ResourceLocation("minecraft","textures/entity/beacon_beam.png");
    public OrdnanceRenderer(EntityRendererProvider.Context context) {super(context);}
    @Override public void render(OrdnanceEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(entity.orbital()) {
            pose.pushPose();pose.translate(-.5,0,-.5);
            BeaconRenderer.renderBeaconBeam(pose,buffers,BEAM,partial,1,entity.level().getGameTime(),0,256,new float[]{1,.05f,.85f},.2f,.35f);
            pose.popPose();
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    @Override public boolean shouldRender(OrdnanceEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z) {return entity.orbital()||super.shouldRender(entity,frustum,x,y,z);}
    @Override public ResourceLocation getTextureLocation(OrdnanceEntity entity) {return BEAM;}
}
