package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.HologramEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;

public final class HologramRenderer implements BlockEntityRenderer<HologramEntity> {
    public HologramRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(HologramEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(be.displayed().isEmpty()||be.getLevel()==null)return;
        float time=be.getLevel().getGameTime()+partial;
        draw(be.displayed(),time,pose,buffers,be.getLevel(),be.size()/100f,be.rotation());
    }
    public static void draw(ItemStack stack,float time,PoseStack pose,MultiBufferSource buffers,net.minecraft.world.level.Level level){
        draw(stack,time,pose,buffers,level,1,2);
    }
    public static void draw(ItemStack stack,float time,PoseStack pose,MultiBufferSource buffers,net.minecraft.world.level.Level level,float size,int rotation){
        pose.pushPose();pose.translate(.5,.7+.45*size+Math.sin(time*.045)*.055,.5);pose.mulPose(Axis.YP.rotationDegrees(time*.625f*rotation));pose.scale(.9f*size,.9f*size,.9f*size);
        MultiBufferSource tinted=type->{
            var target=type==Sheets.solidBlockSheet()||type==Sheets.cutoutBlockSheet()?Sheets.translucentItemSheet():type;
            return new Tint(buffers.getBuffer(target));
        };
        Minecraft.getInstance().getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,tinted,level,0);
        pose.popPose();
        // A luminous scan ring ties the floating model to the projector lens.
        var lines=buffers.getBuffer(RenderType.lines());
        for(int i=0;i<4;i++){double a=i*Math.PI/2+Math.PI/4;edge(pose,lines,.5f,.35f,.5f,(float)(.5+Math.cos(a)*.3),.85f,(float)(.5+Math.sin(a)*.3));}
        float y=.5f+(float)((time*.012)%1)*1.05f;
        for(int i=0;i<40;i++){
            double a=i*Math.PI/20,b=(i+1)*Math.PI/20;
            edge(pose,lines,(float)(.5+Math.cos(a)*.36),y,(float)(.5+Math.sin(a)*.36),(float)(.5+Math.cos(b)*.36),y,(float)(.5+Math.sin(b)*.36));
        }
    }
    private static void edge(PoseStack p,VertexConsumer v,float x,float y,float z,float a,float b,float c){
        float dx=a-x,dy=b-y,dz=c-z,len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        v.vertex(p.last().pose(),x,y,z).color(65,210,255,95).normal(p.last().normal(),dx/len,dy/len,dz/len).endVertex();
        v.vertex(p.last().pose(),a,b,c).color(65,210,255,95).normal(p.last().normal(),dx/len,dy/len,dz/len).endVertex();
    }
    private record Tint(VertexConsumer delegate) implements VertexConsumer {
        public VertexConsumer vertex(double x,double y,double z){delegate.vertex(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){delegate.color((int)(r*.35),(int)(g*.85),b,(int)(a*.68));return this;}
        public VertexConsumer uv(float u,float v){delegate.uv(u,v);return this;}
        public VertexConsumer overlayCoords(int u,int v){delegate.overlayCoords(u,v);return this;}
        public VertexConsumer uv2(int u,int v){delegate.uv2(240,240);return this;}
        public VertexConsumer normal(float x,float y,float z){delegate.normal(x,y,z);return this;}
        public void endVertex(){delegate.endVertex();}
        public void defaultColor(int r,int g,int b,int a){delegate.defaultColor((int)(r*.35),(int)(g*.85),b,(int)(a*.68));}
        public void unsetDefaultColor(){delegate.unsetDefaultColor();}
    }
    @Override public int getViewDistance(){return 48;}
}
