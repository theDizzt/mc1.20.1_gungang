package dev.gungang.acoustics.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
public final class PuddleRenderer extends EntityRenderer<PuddleOverlay>{
    public PuddleRenderer(EntityRendererProvider.Context c){super(c);}
    @Override public void render(PuddleOverlay e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){pose.pushPose();pose.translate(-.5,0,-.5);net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(GimmickBlocks.PUDDLE.get().defaultBlockState(),pose,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);pose.popPose();}
    @Override public net.minecraft.resources.ResourceLocation getTextureLocation(PuddleOverlay e){return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;}
}
