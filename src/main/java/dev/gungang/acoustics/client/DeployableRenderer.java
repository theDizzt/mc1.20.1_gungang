package dev.gungang.acoustics.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.DeployableEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public final class DeployableRenderer extends EntityRenderer<DeployableEntity> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public DeployableRenderer(EntityRendererProvider.Context context) {super(context);blocks=context.getBlockRenderDispatcher();}
    @Override public void render(DeployableEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        if(entity.wall()) {
            for(int x=-1;x<=1;x++)for(int y=0;y<3;y++) {
                pose.pushPose();
                pose.translate(entity.east()?-.18:x-.5,y,entity.east()?x-.5:-.18);
                pose.scale(entity.east()?.36f:1,1,entity.east()?1:.36f);
                blocks.renderSingleBlock(Blocks.OAK_PLANKS.defaultBlockState(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        } else {
            pose.translate(-.35,0,-.35);pose.scale(.7f,1.2f,.7f);
            blocks.renderSingleBlock(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        // Enemy mines must not reveal their team through the ordinary colored frame either.
        if(entity.wall()||entity.mineOutlineVisibleTo(net.minecraft.client.Minecraft.getInstance().player)) {
            int color=entity.teamColor();
            var box=entity.getBoundingBox().move(-entity.getX(),-entity.getY(),-entity.getZ()).inflate(.015);
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),box,
                    (color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(DeployableEntity entity) {return TextureAtlas.LOCATION_BLOCKS;}
}
