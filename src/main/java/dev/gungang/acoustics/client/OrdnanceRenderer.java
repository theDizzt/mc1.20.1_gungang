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
            float beam,glow;float[] color;
            if(entity.detonated()) {
                // A brief white-hot lance at impact, collapsing over ~0.4 s.
                int since=entity.detonatedAt()<0?99:entity.tickCount-entity.detonatedAt();
                float t=(since+partial)/8f;
                if(t>=1){super.render(entity,yaw,partial,pose,buffers,light);return;}
                float k=(1-t)*(1-t);beam=.3f+2.4f*k;glow=.4f+2.6f*k;color=new float[]{1,.85f+.15f*k,.95f};
            } else {
                // Charge-up: the last 40% of the fuse thickens the beam and heats magenta toward white.
                float progress=Math.min(1,(entity.tickCount+partial)/entity.fuse());
                float heat=Math.max(0,Math.min(1,(progress-.6f)/.4f));heat=heat*heat*(3-2*heat);
                float flicker=heat>0?1+.12f*heat*(float)Math.sin((entity.tickCount+partial)*2.1):1;
                beam=(.2f+.45f*heat)*flicker;glow=.35f+.75f*heat;
                color=new float[]{1,.05f+.75f*heat,.85f+.15f*heat};
            }
            pose.pushPose();pose.translate(-.5,0,-.5);
            BeaconRenderer.renderBeaconBeam(pose,buffers,BEAM,partial,1,entity.level().getGameTime(),0,256,color,beam,glow);
            pose.popPose();
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    @Override public boolean shouldRender(OrdnanceEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z) {return entity.orbital()||super.shouldRender(entity,frustum,x,y,z);}
    @Override public ResourceLocation getTextureLocation(OrdnanceEntity entity) {return BEAM;}
}
