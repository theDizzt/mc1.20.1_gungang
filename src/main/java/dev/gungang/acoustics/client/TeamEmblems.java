package dev.gungang.acoustics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Team identity comes from the team name, never from a configurable colour. */
public final class TeamEmblems {
    private static final ResourceLocation ALPHA=new ResourceLocation("gungang","textures/team/alpha.png");
    private static final ResourceLocation BRAVO=new ResourceLocation("gungang","textures/team/bravo.png");
    public static ResourceLocation texture(String team){
        return "ALPHA".equals(team)||"Red".equals(team)?ALPHA:"BRAVO".equals(team)||"Blue".equals(team)?BRAVO:null;
    }
    public static boolean draw(GuiGraphics g,String team,int x,int y,int size){
        var texture=texture(team);if(texture==null)return false;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        g.blit(texture,x,y,size,size,0,0,1254,1254,1254,1254);
        return true;
    }
    /** A decal in local XY, with its bottom at y; each face reads normally from its own side. */
    public static void decal(PoseStack pose,MultiBufferSource buffers,int light,String team,float x,float y,float z,float size,boolean back){
        var texture=texture(team);if(texture==null)return;
        var v=buffers.getBuffer(RenderType.entityTranslucent(texture));var p=pose.last();
        float left=x-size/2,right=x+size/2,normal=back?1:-1;
        float u0=back?0:1,u1=back?1:0;
        vertex(v,p,light,left,y,z,u0,1,normal);vertex(v,p,light,right,y,z,u1,1,normal);
        vertex(v,p,light,right,y+size,z,u1,0,normal);vertex(v,p,light,left,y+size,z,u0,0,normal);
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer v,PoseStack.Pose p,int light,float x,float y,float z,float u,float t,float normal){
        v.vertex(p.pose(),x,y,z).color(255,255,255,255).uv(u,t).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(),0,0,normal).endVertex();
    }
    private TeamEmblems(){}
}
