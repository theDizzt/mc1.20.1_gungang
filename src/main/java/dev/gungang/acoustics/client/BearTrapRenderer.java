package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.BearTrapEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Built from scaled block cuboids: a dark frame with leaf springs and a pressure pan, plus two toothed
 * iron jaws hinged on the long axis. Set, the jaws lie flat open; sprung, they stand up and the teeth interlock.
 */
public final class BearTrapRenderer extends EntityRenderer<BearTrapEntity> {
    private static final float HALF=.42f,JAW=.4f,BAR=.05f;
    private static final BlockState FRAME=Blocks.NETHERITE_BLOCK.defaultBlockState(),STEEL=Blocks.IRON_BLOCK.defaultBlockState();
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public BearTrapRenderer(EntityRendererProvider.Context context) {super(context);blocks=context.getBlockRenderDispatcher();}

    @Override public void render(BearTrapEntity trap,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        float now=trap.tickCount+partial;
        if(trap.sprung()&&trap.closedAt<0)trap.closedAt=now;
        if(!trap.sprung())trap.closedAt=-1;
        // Snap shut over two ticks with a slight overshoot so the bite reads.
        float t=trap.closedAt<0?0:Math.min(1,(now-trap.closedAt)/2f);
        float close=t<1?t*1.08f:1;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-trap.getYRot()));
        // Base: two cross rails, the long spring bar the jaws pivot on, and leaf springs at each end.
        box(pose,buffers,light,FRAME,-HALF,0,-.03f,HALF,.04f,.03f);
        box(pose,buffers,light,FRAME,-.03f,0,-HALF*.8f,.03f,.03f,HALF*.8f);
        for(int side=-1;side<=1;side+=2) {
            float x=side*(HALF+.04f);
            box(pose,buffers,light,FRAME,x-.05f,0,-.08f,x+.05f,.07f,.08f);
            box(pose,buffers,light,STEEL,x-.02f,.07f,-.02f,x+.02f,.1f,.02f);
        }
        // Pressure pan in the middle; it sinks when stepped on.
        pose.pushPose();pose.translate(-.13,close*-.015,-.13);pose.scale(.26f,1,.26f);
        blocks.renderSingleBlock(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // Anchor chain trailing off one end.
        pose.pushPose();pose.translate(HALF+.05,.03-.25,-.25);pose.scale(.5f,.5f,.5f);
        blocks.renderSingleBlock(Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
        jaw(pose,buffers,light,1,close);
        jaw(pose,buffers,light,-1,close);
        pose.popPose();
        if(trap.outlineVisibleTo(Minecraft.getInstance().player)) {
            int color=trap.teamColor();
            var box=trap.getBoundingBox().move(-trap.getX(),-trap.getY(),-trap.getZ()).inflate(.015);
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),box,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);
        }
        super.render(trap,yaw,partial,pose,buffers,light);
    }

    /** One jaw in its own frame: hinge on the x axis, reaching out along +z; teeth stand up along local +y. */
    private void jaw(PoseStack pose,MultiBufferSource buffers,int light,int side,float close) {
        pose.pushPose();
        // The two hinges sit a hair apart so the shut jaws lean into each other instead of overlapping.
        pose.translate(0,.045,side*.02);
        if(side<0)pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.mulPose(Axis.XP.rotationDegrees(-86*close));
        // Curved bite: an outer rail and two angled cheeks approximate the half-circle.
        box(pose,buffers,light,STEEL,-HALF*.62f,0,JAW-BAR,HALF*.62f,BAR,JAW);
        for(int s=-1;s<=1;s+=2) {
            box(pose,buffers,light,STEEL,s>0?HALF*.62f-BAR:-HALF*.62f,0,JAW*.55f,s>0?HALF*.62f:-HALF*.62f+BAR,BAR,JAW);
            pose.pushPose();pose.translate(s*HALF*.62f,0,JAW*.55f);pose.mulPose(Axis.YP.rotationDegrees(s*-38));
            box(pose,buffers,light,STEEL,s>0?-BAR:0,0,-JAW*.62f,s>0?0:BAR,BAR,0);
            pose.popPose();
        }
        // Teeth along the outer rail; the two jaws are offset half a tooth so they mesh.
        int teeth=6;float span=HALF*1.12f,step=span/teeth,offset=side>0?0:step/2;
        for(int i=0;i<teeth;i++) {
            float x=-span/2+offset+i*step;
            if(x+.025f>span/2)continue;
            box(pose,buffers,light,STEEL,x,BAR,JAW-BAR*.9f,x+.025f,BAR+.065f,JAW-BAR*.1f);
        }
        pose.popPose();
    }
    private void box(PoseStack pose,MultiBufferSource buffers,int light,BlockState state,float x0,float y0,float z0,float x1,float y1,float z1) {
        pose.pushPose();pose.translate(x0,y0,z0);pose.scale(x1-x0,y1-y0,z1-z0);
        blocks.renderSingleBlock(state,pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(BearTrapEntity entity) {return TextureAtlas.LOCATION_BLOCKS;}
}
