package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only the aiming player sees the predicted landing marker. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class VoidAnchorTrajectory {
    private static VoidAnchorEntity probe;
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {probe=null;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null||mc.level==null||mc.screen!=null||!p.isAlive()||p.isSpectator()||mc.options.hideGui)return;
        // Only while aiming it: held in the main hand, or in the off-hand with its use key (H) down. The secondary always
        // sits in the off-hand, so checking that hand alone left the marker up while the primary was in use.
        var stack=p.getMainHandItem();
        if(!stack.is(GadgetItems.VOID_ANCHOR.get())&&OffhandMacro.USE.isDown())stack=p.getOffhandItem();
        if(!stack.is(GadgetItems.VOID_ANCHOR.get())||SecondaryRuntime.remaining(p,stack)>0)return;
        if(probe==null||probe.level()!=mc.level)probe=GadgetItems.VOID_ANCHOR_ENTITY.get().create(mc.level);
        if(probe==null)return;
        Vec3 point=p.getEyePosition(event.getPartialTick()).add(p.getLookAngle().scale(.4));
        Vec3 velocity=VoidAnchorFlight.launch(p.getLookAngle());double distance=0;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
        for(int tick=0;tick<=400;tick++) {
            Vec3 step=VoidAnchorFlight.step(velocity,distance),end=point.add(step);probe.setPos(point);
            var hit=mc.level.clip(new ClipContext(point,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,probe));
            boolean landed=hit.getType()==HitResult.Type.BLOCK;
            if(landed)end=hit.getLocation();
            if(landed) {
                ThrowAimGuide.impact(pose,lines,PlacementSurface.anchorImpact(mc.level,hit),mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.SlabBlock?new Vec3(0,1,0):Vec3.atLowerCornerOf(hit.getDirection().getNormal()),false);
                break;
            }
            point=end;distance+=step.horizontalDistance();velocity=VoidAnchorFlight.next(velocity,distance);
            if(point.y<mc.level.getMinBuildHeight()-64)break;
        }
        buffers.endBatch(RenderType.lines());pose.popPose();
    }
    private VoidAnchorTrajectory() { }
}
