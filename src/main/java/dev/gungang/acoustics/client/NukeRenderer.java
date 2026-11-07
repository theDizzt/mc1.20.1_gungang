package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.ToyBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** 239: the 2x3x2 nuclear warhead (tail fins, olive body with a yellow radiation band, stepped nose), on its block's centre. */
public final class NukeRenderer implements BlockEntityRenderer<ToyBlocks.NukeEntity> {
    public NukeRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(ToyBlocks.NukeEntity nuke,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){draw(pose,buffers,light);}
    @Override public boolean shouldRenderOffScreen(ToyBlocks.NukeEntity nuke){return true;}
    /** Block-corner coordinates: the warhead stands on the cell's floor, centred on x = z = 0.5, 2 wide and 3 tall. */
    static void draw(PoseStack pose,MultiBufferSource buffers,int light){
        Block fin=Blocks.POLISHED_DEEPSLATE,body=Blocks.GREEN_TERRACOTTA,band=Blocks.YELLOW_CONCRETE,mark=Blocks.BLACK_CONCRETE,metal=Blocks.IRON_BLOCK;
        // Tail fins in a cross, and the tail cone between them.
        EscortRenderer.box(pose,buffers,light,fin,-.45,0,.45,1.9f,.8f,.1f);
        EscortRenderer.box(pose,buffers,light,fin,.45,0,-.45,.1f,.8f,1.9f);
        EscortRenderer.box(pose,buffers,light,Blocks.GRAY_CONCRETE,.05,0,.05,.9f,.42f,.9f);
        // A rounded body: three overlapping boxes read as an octagon from above.
        octagon(pose,buffers,light,body,.4,1.7f,0);
        octagon(pose,buffers,light,band,1.45,.28f,.02f);
        for(int side=0;side<4;side++){
            pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side*90));pose.translate(-.5,0,-.5);
            EscortRenderer.box(pose,buffers,light,mark,.36,1.49,-.34,.28f,.2f,.02f);
            pose.popPose();
        }
        // Stepped nose cone with a metal tip.
        EscortRenderer.box(pose,buffers,light,body,-.15,2.1,-.15,1.3f,.3f,1.3f);
        EscortRenderer.box(pose,buffers,light,body,0,2.4,0,1f,.25f,1f);
        EscortRenderer.box(pose,buffers,light,body,.15,2.65,.15,.7f,.2f,.7f);
        EscortRenderer.box(pose,buffers,light,metal,.3,2.85,.3,.4f,.15f,.4f);
    }
    private static void octagon(PoseStack pose,MultiBufferSource buffers,int light,Block block,double y,float h,float grow){
        EscortRenderer.box(pose,buffers,light,block,-.3-grow,y,-grow,1.6f+2*grow,h,1f+2*grow);
        EscortRenderer.box(pose,buffers,light,block,-grow,y,-.3-grow,1f+2*grow,h,1.6f+2*grow);
        EscortRenderer.box(pose,buffers,light,block,-.2-grow,y,-.2-grow,1.4f+2*grow,h,1.4f+2*grow);
    }
}
