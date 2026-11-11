package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.RapierDash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** A few faint frozen skin poses, fading out in 0.3 seconds. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class RapierTrail {
    private record Ghost(PlayerModel<AbstractClientPlayer> model,ResourceLocation skin,Vec3 pos,float yaw,long born,int light,int color){}
    private static final java.util.ArrayDeque<Ghost> GHOSTS=new java.util.ArrayDeque<>();
    private static final java.util.Map<java.util.UUID,Long> SAMPLED=new java.util.HashMap<>();
    public static void receive(GungangNetwork.RapierStep packet){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(mc.level.getEntity(packet.entity()) instanceof net.minecraft.world.entity.player.Player p){
            WeaponFx.rapierDash(p,packet.yaw(),packet.distance());
            RapierDash.start(p,packet.yaw(),packet.distance());
        }
    }
    @SubscribeEvent public static void capture(RenderPlayerEvent.Post event){
        if(!(event.getEntity() instanceof AbstractClientPlayer p)||!RapierDash.active(p)||p.isInvisible()||HermesTrail.dashing(p))return;
        long now=p.level().getGameTime();if(now-SAMPLED.getOrDefault(p.getUUID(),Long.MIN_VALUE/2)<2)return;
        SAMPLED.put(p.getUUID(),now);
        var mc=Minecraft.getInstance();boolean slim=p.getModelName().equals("slim");
        var model=new PlayerModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
        var source=event.getRenderer().getModel();
        model.young=source.young;model.riding=source.riding;model.crouching=source.crouching;
        var from=parts(source);var to=parts(model);
        for(int i=0;i<Math.min(from.size(),to.size());i++){to.get(i).loadPose(from.get(i).storePose());to.get(i).visible=from.get(i).visible;}
        // The owner's rapier effect colour; with two, successive afterimages alternate between them.
        var colors=dev.gungang.acoustics.server.EffectColors.client(p.getUUID(),"rapier");
        int color=dev.gungang.acoustics.server.EffectColors.at(colors,(now/2)%2==0?0:1);
        GHOSTS.addLast(new Ghost(model,p.getSkinTextureLocation(),p.getPosition(event.getPartialTick()),p.yBodyRot,now,event.getPackedLight(),color));
        while(GHOSTS.size()>96)GHOSTS.removeFirst();
    }
    private static java.util.List<net.minecraft.client.model.geom.ModelPart> parts(PlayerModel<?> m){
        return java.util.List.of(m.head,m.hat,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg,m.jacket,m.rightSleeve,m.leftSleeve,m.rightPants,m.leftPants);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        double now=mc.level.getGameTime()+event.getPartialTick();GHOSTS.removeIf(g->now-g.born>=6||now<g.born);
        SAMPLED.values().removeIf(t->now-t>20);
        if(GHOSTS.isEmpty())return;
        var buffer=mc.renderBuffers().bufferSource();var camera=event.getCamera().getPosition();PoseStack pose=event.getPoseStack();
        for(var ghost:GHOSTS){
            pose.pushPose();pose.translate(ghost.pos.x-camera.x,ghost.pos.y-camera.y,ghost.pos.z-camera.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-ghost.yaw));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
            // Emissive and full-bright like the boots trail: a skin multiplied by a tint at low alpha under world light
            // hid the owner's colour entirely.
            float fade=(float)(1-(now-ghost.born)/6);
            ghost.model.renderToBuffer(pose,buffer.getBuffer(RenderType.entityTranslucentEmissive(ghost.skin)),net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,
                    dev.gungang.acoustics.server.EffectColors.red(ghost.color),dev.gungang.acoustics.server.EffectColors.green(ghost.color),dev.gungang.acoustics.server.EffectColors.blue(ghost.color),.42f*fade*fade);
            pose.popPose();
        }
        buffer.endBatch();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){GHOSTS.clear();SAMPLED.clear();}
    private RapierTrail(){}
}
