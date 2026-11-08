package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.HandAxeItem;
import dev.gungang.acoustics.server.HandAxeRules;
import dev.gungang.acoustics.server.HandAxeFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local-only aiming guide, using the same launch parameters and discrete physics as the axe. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class HandAxeTrajectory {
    private static dev.gungang.acoustics.server.HandAxeEntity probe;
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){probe=null;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null||mc.screen!=null||mc.options.hideGui||!player.isAlive()||player.isSpectator()||!player.isUsingItem()||!HandAxeItem.is(player.getUseItem()))return;
        var tag=player.getUseItem().getTag();
        if(tag==null||!tag.contains("AxeArcDegrees"))return;
        double distance=HandAxeRules.distance(player.getTicksUsingItem(),tag.getDouble("AxeMinRange"),tag.getDouble("AxeMaxRange"),tag.getDouble("AxeChargeSeconds"));
        Vec3 velocity=HandAxeFlight.velocity(player,distance,tag.getDouble("AxeArcDegrees"));
        Vec3 point=HandAxeFlight.origin(player);
        // Use the projectile itself for collision context and owner filtering, without adding/ticking it in the world.
        if(probe==null||probe.level()!=mc.level)probe=dev.gungang.acoustics.server.GadgetItems.HAND_AXE_PROJECTILE.get().create(mc.level);
        if(probe==null)return;probe.setOwner(player);
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
        boolean hit=false,entityHit=false;
        Vec3 impactNormal=Vec3.ZERO;double travelled=0;
        for(int tick=0;tick<160;tick++) {
            probe.setPos(point);probe.setDeltaMovement(velocity);
            boolean water=probe.updateFluidHeightAndDoFluidPushing(net.minecraft.tags.FluidTags.WATER,.014);
            velocity=probe.getDeltaMovement();
            Vec3 end=point.add(velocity);
            var block=mc.level.clip(new ClipContext(point,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,probe));
            hit=block.getType()!=HitResult.Type.MISS;if(hit)end=block.getLocation();
            if(hit)impactNormal=Vec3.atLowerCornerOf(block.getDirection().getNormal());
            for(var target:mc.level.getEntitiesOfClass(LivingEntity.class,probe.getBoundingBox().expandTowards(velocity).inflate(1),e->e!=player&&e.isAlive()&&!e.isSpectator()&&e.isPickable()&&!e.isPassengerOfSameVehicle(player))) {
                var intersection=target.getBoundingBox().inflate((double).3f).clip(point,end);
                if(intersection.isPresent()){end=intersection.get();hit=true;entityHit=true;impactNormal=camera.subtract(end).normalize();}
            }
            ThrowAimGuide.trail(pose,lines,point,end,travelled);
            travelled+=point.distanceTo(end);
            point=end;if(hit)break;
            velocity=HandAxeFlight.nextVelocity(velocity,water);
            if(point.y<mc.level.getMinBuildHeight())break;
        }
        if(hit)ThrowAimGuide.impact(pose,lines,point,impactNormal,entityHit);
        buffers.endBatch(RenderType.lines());pose.popPose();
    }
    private HandAxeTrajectory(){}
}
