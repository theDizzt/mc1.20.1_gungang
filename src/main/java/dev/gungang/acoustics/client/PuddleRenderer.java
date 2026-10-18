package dev.gungang.acoustics.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
public final class PuddleRenderer extends EntityRenderer<PuddleOverlay>{
    public PuddleRenderer(EntityRendererProvider.Context c){super(c);}
    @Override public void render(PuddleOverlay e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();
        pose.translate(-.5,0,-.5);
        var renderer=net.minecraft.client.Minecraft.getInstance().getBlockRenderer();
        var state=(e.acid()?GimmickBlocks.ACID_PUDDLE:GimmickBlocks.PUDDLE).get().defaultBlockState();
        // Acid texture already carries its color; only water takes the biome tint.
        int color=e.acid()?0xFFFFFF:BiomeColors.getAverageWaterColor(e.level(),e.blockPosition());
        renderer.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.translucent()),state,renderer.getBlockModel(state),
                ((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    @Override public net.minecraft.resources.ResourceLocation getTextureLocation(PuddleOverlay e){return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;}
}
