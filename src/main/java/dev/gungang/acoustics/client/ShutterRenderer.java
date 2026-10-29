package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Draws only the local, clipped section of a continuous upward-moving curtain. */
public final class ShutterRenderer implements BlockEntityRenderer<ShutterEntity> {
    private static final ResourceLocation PANEL=new ResourceLocation("gungang","block/shutter_panel"),FRAME=new ResourceLocation("gungang","block/shutter_frame");
    public ShutterRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(ShutterEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var level=be.getLevel();if(level==null)return;
        var state=be.getBlockState();var pos=be.getBlockPos();var sides=ShutterBlock.connections(state);
        draw(pose,buffers,light,overlay,state.getValue(ShutterBlock.AXIS)==Direction.Axis.X,
                !ShutterBlock.joined(level,pos,state,sides[2]),!ShutterBlock.joined(level,pos,state,sides[3]),!ShutterBlock.joined(level,pos,state,Direction.UP),
                be.localBottom(partial),be.lift(partial),be.bottomY()+be.lift(partial)-pos.getY());
    }
    public static void draw(PoseStack pose,MultiBufferSource buffers,int light,int overlay,boolean axisX,boolean left,boolean right,boolean top,double bottom,double lift,double edge){
        var atlas=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        var panel=atlas.apply(PANEL);var frame=atlas.apply(FRAME);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();if(axisX){pose.translate(1,0,0);pose.mulPose(Axis.YP.rotationDegrees(-90));}
        double phase=lift%.125;
        for(int i=-1;i<9;i++){
            double start=i*.125+phase,end=start+.125,low=Math.max(bottom,Math.max(0,start)),high=Math.min(1,end);
            if(high<=low)continue;
            box(pose,out,panel,0,low,ShutterBlock.MIN+.012,1,high,ShutterBlock.MAX-.012,light,overlay,1);
            double lip=Math.min(high,start+.018);
            if(lip>low)box(pose,out,frame,0,low,ShutterBlock.MIN+.006,1,lip,ShutterBlock.MAX-.006,light,overlay,.9f);
        }
        if(edge>=0&&edge<1)box(pose,out,frame,0,edge,ShutterBlock.MIN,1,Math.min(1,edge+.045),ShutterBlock.MAX,light,overlay,1);
        if(left)box(pose,out,frame,0,0,ShutterBlock.MIN,ShutterBlock.RAIL,1,ShutterBlock.MAX,light,overlay,1);
        if(right)box(pose,out,frame,1-ShutterBlock.RAIL,0,ShutterBlock.MIN,1,1,ShutterBlock.MAX,light,overlay,1);
        if(top)box(pose,out,frame,0,1-ShutterBlock.HEADER,ShutterBlock.MIN,1,1,ShutterBlock.MAX,light,overlay,1);
        pose.popPose();
    }
    private static void box(PoseStack p,VertexConsumer v,TextureAtlasSprite s,double x1,double y1,double z1,double x2,double y2,double z2,int light,int overlay,float shade){
        face(p,v,s,new double[][]{{x2,y1,z1},{x1,y1,z1},{x1,y2,z1},{x2,y2,z1}},0,0,-1,light,overlay,shade);
        face(p,v,s,new double[][]{{x1,y1,z2},{x2,y1,z2},{x2,y2,z2},{x1,y2,z2}},0,0,1,light,overlay,shade);
        face(p,v,s,new double[][]{{x1,y1,z1},{x1,y1,z2},{x1,y2,z2},{x1,y2,z1}},-1,0,0,light,overlay,shade*.8f);
        face(p,v,s,new double[][]{{x2,y1,z2},{x2,y1,z1},{x2,y2,z1},{x2,y2,z2}},1,0,0,light,overlay,shade*.8f);
        face(p,v,s,new double[][]{{x1,y2,z1},{x1,y2,z2},{x2,y2,z2},{x2,y2,z1}},0,1,0,light,overlay,shade);
        face(p,v,s,new double[][]{{x1,y1,z2},{x1,y1,z1},{x2,y1,z1},{x2,y1,z2}},0,-1,0,light,overlay,shade*.65f);
    }
    private static void face(PoseStack p,VertexConsumer out,TextureAtlasSprite sprite,double[][] points,float nx,float ny,float nz,int light,int overlay,float shade){
        for(int i=0;i<4;i++){var v=points[i];out.vertex(p.last().pose(),(float)v[0],(float)v[1],(float)v[2]).color(shade,shade,shade,1)
                .uv(sprite.getU(i==0||i==3?0:16),sprite.getV(i<2?16:0)).overlayCoords(overlay).uv2(light).normal(p.last().normal(),nx,ny,nz).endVertex();}
    }
}
