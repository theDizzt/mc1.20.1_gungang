package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.*;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Solid pyramidal spikes; colour slot 2 belongs only to the last three blocks. */
public final class ToeTrailRenderer extends EntityRenderer<ToeTrailEntity> {
    public ToeTrailRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(ToeTrailEntity e){return new ResourceLocation("gungang","textures/item/toe.png");}
    @Override public void render(ToeTrailEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var data=e.shape();int[] points=data.getIntArray("Points");if(points.length==0||!data.hasUUID("Owner"))return;
        int[] colors=EffectColors.client(data.getUUID("Owner"),"toe");
        int critical=colors[1]<0?EffectColors.effect("toe").second():colors[1];int[] criticalFlags=data.getIntArray("CriticalFlags");
        pose.pushPose();
        // Shape coordinates have their own snapshot origin, independent of interpolated entity movement.
        double x=e.xOld+(e.getX()-e.xOld)*partial,y=e.yOld+(e.getY()-e.yOld)*partial,z=e.zOld+(e.getZ()-e.zOld)*partial;
        pose.translate(data.getDouble("X")-x,data.getDouble("Y")-y,data.getDouble("Z")-z);
        var out=buffers.getBuffer(RenderType.lightning());var matrix=pose.last().pose();
        float radius=data.getFloat("Width")/2,height=data.getFloat("Height");
        for(int i=0;i<points.length/3;i++){
            float px=points[i*3]/64f,py=points[i*3+1]/64f,pz=points[i*3+2]/64f;
            int color=(i<criticalFlags.length?criticalFlags[i]!=0:i>=points.length/3-data.getInt("Critical"))?critical:colors[0];
            float[][] corners={{px-radius,py,pz-radius},{px+radius,py,pz-radius},{px+radius,py,pz+radius},{px-radius,py,pz+radius}};
            for(int face=0;face<4;face++){
                var a=corners[face];var b=corners[(face+1)%4];float shade=.65f+face*.1f;
                vertex(out,matrix,a[0],a[1],a[2],color,shade);vertex(out,matrix,b[0],b[1],b[2],color,shade);
                vertex(out,matrix,px,py+height,pz,color,1);vertex(out,matrix,px,py+height,pz,color,1);
            }
        }
        pose.popPose();
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,float x,float y,float z,int color,float shade){
        out.vertex(matrix,x,y,z).color((int)((color>>16&255)*shade),(int)((color>>8&255)*shade),(int)((color&255)*shade),255).endVertex();
    }
}
