package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.ShurikenEntity;
import java.util.ArrayList;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The flying shuriken spins flat along its flight, full-bright, trailing a purple glow ribbon that fades out. */
public final class ShurikenRenderer extends EntityRenderer<ShurikenEntity> {
    private static final float SCALE=.55f,WIDTH=.16f,CORE=.05f;
    private final ItemRenderer items;
    public ShurikenRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemRenderer();}
    @Override public void render(ShurikenEntity shuriken,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        trail(shuriken,partial,pose,buffers);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial,shuriken.yRotO,shuriken.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial,shuriken.xRotO,shuriken.getXRot())));
        // The model faces +Z; lay it flat so it cuts through the air, and spin it about its own axis.
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.ZP.rotationDegrees((shuriken.tickCount+partial)*55));
        pose.scale(SCALE,SCALE,SCALE);
        items.renderStatic(shuriken.getItem(),ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,buffers,shuriken.level(),shuriken.getId());
        pose.popPose();
        super.render(shuriken,yaw,partial,pose,buffers,light);
    }
    /** Camera-facing ribbon through the recent positions: a wide purple glow with a pale core, both additive. */
    private void trail(ShurikenEntity shuriken,float partial,PoseStack pose,MultiBufferSource buffers){
        if(shuriken.trail.size()<2)return;
        var origin=new Vec3(Mth.lerp(partial,shuriken.xo,shuriken.getX()),Mth.lerp(partial,shuriken.yo,shuriken.getY()),Mth.lerp(partial,shuriken.zo,shuriken.getZ()));
        var points=new ArrayList<Vec3>();points.add(origin);
        boolean first=true;
        for(var point:shuriken.trail){if(first){first=false;continue;}points.add(point);}
        var camera=entityRenderDispatcher.camera.getPosition();
        var buffer=buffers.getBuffer(RenderType.lightning());
        var matrix=pose.last().pose();
        // The thrower's effect colours (key "shuriken"): blade end in the first, tail in the second.
        int[] colors=shuriken.colors();
        ribbon(buffer,matrix,points,origin,camera,WIDTH,colors,0,.55f);
        ribbon(buffer,matrix,points,origin,camera,CORE,colors,.7f,.75f);
    }
    /** {@code wash}: how far each colour is lifted toward white (the pale core). */
    static void ribbon(VertexConsumer buffer,Matrix4f matrix,java.util.List<Vec3> points,Vec3 origin,Vec3 camera,float width,int[] colors,float wash,float alpha){
        int n=points.size();
        for(int i=0;i+1<n;i++){
            var a=points.get(i);var c=points.get(i+1);
            var along=c.subtract(a);if(along.lengthSqr()<1e-8)continue;
            var view=a.subtract(camera);
            var side=along.cross(view);if(side.lengthSqr()<1e-10)continue;
            float t0=i/(float)(n-1),t1=(i+1)/(float)(n-1);
            var unit=side.normalize();var s0=unit.scale(width*(1-t0));var s1=unit.scale(width*(1-t1));
            float a0=alpha*(1-t0),a1=alpha*(1-t1);
            var p0=a.subtract(origin);var p1=c.subtract(origin);
            int color=dev.gungang.acoustics.server.EffectColors.at(new int[]{dev.gungang.acoustics.server.EffectColors.at(colors,t0),0xFFFFFF},wash);
            float r=dev.gungang.acoustics.server.EffectColors.red(color),g=dev.gungang.acoustics.server.EffectColors.green(color),b=dev.gungang.acoustics.server.EffectColors.blue(color);
            // Both windings: the ribbon is seen from either side as the camera passes it.
            quad(buffer,matrix,p0.add(s0),p0.subtract(s0),p1.subtract(s1),p1.add(s1),r,g,b,a0,a1);
            quad(buffer,matrix,p1.add(s1),p1.subtract(s1),p0.subtract(s0),p0.add(s0),r,g,b,a1,a0);
        }
    }
    private static void quad(VertexConsumer buffer,Matrix4f matrix,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float bl,float alphaAB,float alphaCD){
        buffer.vertex(matrix,(float)a.x,(float)a.y,(float)a.z).color(r,g,bl,alphaAB).endVertex();
        buffer.vertex(matrix,(float)b.x,(float)b.y,(float)b.z).color(r,g,bl,alphaAB).endVertex();
        buffer.vertex(matrix,(float)c.x,(float)c.y,(float)c.z).color(r,g,bl,alphaCD).endVertex();
        buffer.vertex(matrix,(float)d.x,(float)d.y,(float)d.z).color(r,g,bl,alphaCD).endVertex();
    }
    @Override public ResourceLocation getTextureLocation(ShurikenEntity entity){return TextureAtlas.LOCATION_BLOCKS;}
}
