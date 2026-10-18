package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;

public final class EscortRenderer extends EntityRenderer<EscortCart> {
    public EscortRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=1.6f;}
    @Override public ResourceLocation getTextureLocation(EscortCart cart){return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;}
    static void box(PoseStack pose,MultiBufferSource buffers,int light,Block block,double x,double y,double z,float w,float h,float d){
        pose.pushPose();pose.translate(x,y,z);pose.scale(w,h,d);Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(),pose,buffers,light,OverlayTexture.NO_OVERLAY);pose.popPose();
    }
    @Override public void render(EscortCart cart,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-net.minecraft.util.Mth.rotLerp(partial,cart.yRotO,cart.getYRot())));
        box(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-1.5,.55,-2.5,3,.3f,5);
        box(pose,buffers,light,Blocks.IRON_BLOCK,-1.5,.85,-2.5,.18f,1.45f,5);
        box(pose,buffers,light,Blocks.IRON_BLOCK,1.32,.85,-2.5,.18f,1.45f,5);
        box(pose,buffers,light,Blocks.IRON_BLOCK,-1.32,.85,-2.5,2.64f,1.45f,.18f);
        box(pose,buffers,light,Blocks.IRON_BLOCK,-1.32,.85,2.32,2.64f,1.45f,.18f);
        box(pose,buffers,light,Blocks.RAW_GOLD_BLOCK,-1.3,.85,-2.3,2.6f,1.5f,4.6f);
        // Flat gold load top is the collision deck players can stand on.
        box(pose,buffers,light,Blocks.GOLD_BLOCK,-1.3,2.35,-2.3,2.6f,.15f,4.6f);
        for(int side:new int[]{-1,1})for(float z:new float[]{-1.75f,1.75f}){
            pose.pushPose();pose.translate(side*1.28,.48,z);
            pose.mulPose(Axis.XP.rotationDegrees(cart.travel()*120));
            box(pose,buffers,light,Blocks.POLISHED_DEEPSLATE,-.16,-.42,-.42,.32f,.84f,.84f);
            box(pose,buffers,light,Blocks.IRON_BLOCK,-.18,-.12,-.12,.36f,.24f,.24f);pose.popPose();
        }
        pose.popPose();super.render(cart,yaw,partial,pose,buffers,light);
    }
    public static final class Rail extends EntityRenderer<EscortRail> {
        public Rail(EntityRendererProvider.Context c){super(c);}
        @Override public ResourceLocation getTextureLocation(EscortRail rail){return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;}
        @Override public void render(EscortRail rail,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-yaw));pose.mulPose(Axis.XP.rotationDegrees(rail.getXRot()));float len=rail.segmentLength();
            box(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-1.5,0,-.12,3,.08f,.24f);
            for(float x:new float[]{-1.1f,.98f})box(pose,buffers,light,Blocks.IRON_BLOCK,x,.08,-len/2,.12f,.1f,len);
            pose.popPose();
        }
    }
}
