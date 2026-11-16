package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.*;
import dev.gungang.acoustics.server.GluePoolEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** 37 glue gun pool: a flat white disc with a light grey rim, fading out over its last half second. */
public final class GluePoolRenderer extends EntityRenderer<GluePoolEntity> {
    private static final int SEGMENTS=40;
    public GluePoolRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(GluePoolEntity e){return new ResourceLocation("minecraft","textures/misc/white.png");}
    @Override public void render(GluePoolEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float age=e.tickCount+partial,left=e.life()-age;
        float fade=Mth.clamp(left/10f,0,1)*Mth.clamp(age/3f,0,1);
        if(fade<=0)return;
        float r=e.radius()*Mth.clamp(.6f+age/6f,0,1);
        var out=buffers.getBuffer(RenderType.debugQuads());var matrix=pose.last().pose();
        int core=Math.round(200*fade),rim=Math.round(230*fade);
        // Just above the floor so it does not flicker against it.
        float y=.03f;
        for(int i=0;i<SEGMENTS;i++){
            float a0=(float)(i*Math.PI*2/SEGMENTS),a1=(float)((i+1)*Math.PI*2/SEGMENTS);
            float c0=Mth.cos(a0),s0=Mth.sin(a0),c1=Mth.cos(a1),s1=Mth.sin(a1),inner=r*.88f;
            // Fill (centre fan as degenerate quads), then the rim band.
            vertex(out,matrix,0,y,0,0xFFFFFF,core);vertex(out,matrix,inner*c1,y,inner*s1,0xFFFFFF,core);
            vertex(out,matrix,inner*c0,y,inner*s0,0xFFFFFF,core);vertex(out,matrix,inner*c0,y,inner*s0,0xFFFFFF,core);
            vertex(out,matrix,inner*c0,y+.002f,inner*s0,0xE4E8EC,rim);vertex(out,matrix,inner*c1,y+.002f,inner*s1,0xE4E8EC,rim);
            vertex(out,matrix,r*c1,y+.002f,r*s1,0xC9CED4,rim);vertex(out,matrix,r*c0,y+.002f,r*s0,0xC9CED4,rim);
        }
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,float x,float y,float z,int rgb,int alpha){
        out.vertex(matrix,x,y,z).color(rgb>>16&255,rgb>>8&255,rgb&255,alpha).endVertex();
    }
}
