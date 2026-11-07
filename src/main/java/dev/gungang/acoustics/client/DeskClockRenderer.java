package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.DeskBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 221: the desk clock's LCD face, the real local time as 24-hour HH:MM:SS drawn with the match timer's digit atlas
 * (unlit "8" segments behind, the colons blinking each second). Glows in the dark; red while the alarm is set.
 */
public final class DeskClockRenderer implements BlockEntityRenderer<DeskBlocks.ClockEntity> {
    private static final ResourceLocation ATLAS=new ResourceLocation(GungangAcoustics.ID,"textures/gui/timer.png");
    private static final java.time.format.DateTimeFormatter TIME=java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
    /** Screen in model pixels on the north-facing clock: its front plane, left edge (seen from the front, +x) and height. */
    private static final float FRONT=4.95f/16,LEFT=13.3f/16,WIDTH=10.6f/16,BOTTOM=1.4f/16,TOP=5.6f/16;
    private static final int FULL_BRIGHT=0xF000F0;
    public DeskClockRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(DeskBlocks.ClockEntity clock,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var state=clock.getBlockState();if(!state.hasProperty(DeskBlocks.Clock.FACING))return;
        float rot=switch(state.getValue(DeskBlocks.Clock.FACING)){case EAST->90;case SOUTH->180;case WEST->270;default->0;};
        boolean alarm=state.getValue(DeskBlocks.Clock.ALARM);
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-rot));pose.translate(-.5,0,-.5);
        String text=java.time.LocalTime.now().format(TIME);boolean colon=System.currentTimeMillis()%1000<500;
        var buffer=buffers.getBuffer(RenderType.text(ATLAS));var matrix=pose.last().pose();
        float cell=WIDTH/text.length(),height=Math.min(TOP-BOTTOM,cell*1.6f),bottom=(BOTTOM+TOP-height)/2;
        float r=alarm?1:.55f,g=alarm?.42f:1,b=alarm?.36f:.7f;
        for(int i=0;i<text.length();i++){
            int glyph="0123456789:".indexOf(text.charAt(i));if(glyph<0)continue;
            float left=LEFT-i*cell;
            // Unlit segments first, a hair behind the lit digit.
            glyph(buffer,matrix,glyph==10?10:8,left,bottom,cell,height,FRONT-.0005f,r,g,b,.13f);
            float shift=glyph==1?cell*7/30:0;
            if(glyph!=10||colon)glyph(buffer,matrix,glyph,left-shift,bottom,cell,height,FRONT-.001f,r,g,b,1);
        }
        pose.popPose();
    }
    /** One atlas cell on the clock's front plane, facing -z; seen from the front its left edge is at +x. */
    private static void glyph(VertexConsumer buffer,Matrix4f matrix,int glyph,float left,float bottom,float w,float h,float z,float r,float g,float b,float a){
        float u0=glyph/11f,u1=(glyph+1)/11f,right=left-w,top=bottom+h;
        buffer.vertex(matrix,left,top,z).color(r,g,b,a).uv(u0,0).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix,left,bottom,z).color(r,g,b,a).uv(u0,1).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix,right,bottom,z).color(r,g,b,a).uv(u1,1).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix,right,top,z).color(r,g,b,a).uv(u1,0).uv2(FULL_BRIGHT).endVertex();
    }
}
