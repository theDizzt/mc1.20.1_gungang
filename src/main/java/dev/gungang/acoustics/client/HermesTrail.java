package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.EffectColors;
import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.RapierDash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 74 Hermes' Boots dash: the client replays the server's fixed-step dash and leaves glowing afterimages of the body
 * plus a spray of motes from the feet, tinted with the owner's effect colours ({@link EffectColors}, key
 * "hermes_boots"). With two colours the afterimages run from the first (dash start) to the second (dash end).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class HermesTrail {
    private record Ghost(PlayerModel<AbstractClientPlayer> model,ResourceLocation skin,Vec3 pos,float yaw,long born,int color,float life){}
    /** A running dash: when it started and ends (game ticks), and how long its afterimages last. */
    private record Dash(long start,long end,float life){}
    private static final java.util.ArrayDeque<Ghost> GHOSTS=new java.util.ArrayDeque<>();
    private static final java.util.Map<Integer,Dash> DASHES=new java.util.HashMap<>();
    private static final java.util.Map<Integer,Double> SAMPLED=new java.util.HashMap<>();

    public static void receive(GungangNetwork.HermesDash packet){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(!(mc.level.getEntity(packet.entity()) instanceof net.minecraft.world.entity.player.Player p))return;
        RapierDash.start(p,packet.yaw(),packet.distance(),packet.ticks());
        long now=mc.level.getGameTime();
        DASHES.put(p.getId(),new Dash(now,now+Math.max(1,packet.ticks()),Math.max(1,packet.trailSeconds()*20)));
        burst(p,EffectColors.client(p.getUUID(),"hermes_boots"),14);
    }
    /** Whether this body is mid boots dash (the rapier's own trail stays out of it). */
    public static boolean dashing(net.minecraft.world.entity.player.Player p){
        var dash=DASHES.get(p.getId());return dash!=null&&p.level().getGameTime()<=dash.end;
    }
    private static float progress(Dash dash,double now){return (float)Math.max(0,Math.min(1,(now-dash.start)/Math.max(1,dash.end-dash.start)));}
    private static void burst(net.minecraft.world.entity.player.Player p,int[] colors,int count){
        var random=p.getRandom();
        for(int i=0;i<count;i++){
            int color=EffectColors.at(colors,random.nextFloat());
            p.level().addParticle(new DustParticleOptions(EffectColors.vector(color),.9f+random.nextFloat()*.5f),
                    p.getX()+(random.nextDouble()-.5)*.6,p.getY()+.05+random.nextDouble()*.35,p.getZ()+(random.nextDouble()-.5)*.6,0,.02,0);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        long now=mc.level.getGameTime();
        DASHES.entrySet().removeIf(e->now>e.getValue().end+2);
        SAMPLED.keySet().retainAll(DASHES.keySet());
        for(var entry:DASHES.entrySet())if(mc.level.getEntity(entry.getKey()) instanceof net.minecraft.world.entity.player.Player p&&!p.isInvisible())
            burst(p,EffectColors.client(p.getUUID(),"hermes_boots"),6);
    }
    @SubscribeEvent public static void capture(RenderPlayerEvent.Post event){
        if(!(event.getEntity() instanceof AbstractClientPlayer p)||p.isInvisible())return;
        var dash=DASHES.get(p.getId());if(dash==null)return;
        double now=p.level().getGameTime()+event.getPartialTick();
        if(now>dash.end+1)return;
        // One afterimage per rendered frame would pile up on fast machines; keep them a third of a tick apart.
        if(now-SAMPLED.getOrDefault(p.getId(),-1e9)<.34)return;
        SAMPLED.put(p.getId(),now);
        var mc=Minecraft.getInstance();boolean slim=p.getModelName().equals("slim");
        var model=new PlayerModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
        var source=event.getRenderer().getModel();
        model.young=source.young;model.riding=source.riding;model.crouching=source.crouching;
        var from=parts(source);var to=parts(model);
        for(int i=0;i<Math.min(from.size(),to.size());i++){to.get(i).loadPose(from.get(i).storePose());to.get(i).visible=from.get(i).visible;}
        int color=EffectColors.at(EffectColors.client(p.getUUID(),"hermes_boots"),progress(dash,now));
        // born is kept in thousandths of a tick so sub-tick spacing and fading stay smooth.
        GHOSTS.addLast(new Ghost(model,p.getSkinTextureLocation(),p.getPosition(event.getPartialTick()),p.yBodyRot,Math.round(now*1000),color,dash.life));
        while(GHOSTS.size()>96)GHOSTS.removeFirst();
    }
    private static java.util.List<net.minecraft.client.model.geom.ModelPart> parts(PlayerModel<?> m){
        return java.util.List.of(m.head,m.hat,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg,m.jacket,m.rightSleeve,m.leftSleeve,m.rightPants,m.leftPants);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        double now=mc.level.getGameTime()+event.getPartialTick();
        GHOSTS.removeIf(g->now-g.born/1000.0>=g.life||now<g.born/1000.0-1);
        if(GHOSTS.isEmpty())return;
        var buffer=mc.renderBuffers().bufferSource();var camera=event.getCamera().getPosition();PoseStack pose=event.getPoseStack();
        for(var ghost:GHOSTS){
            float fade=(float)(1-(now-ghost.born/1000.0)/ghost.life);if(fade<=0)continue;
            pose.pushPose();pose.translate(ghost.pos.x-camera.x,ghost.pos.y-camera.y,ghost.pos.z-camera.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-ghost.yaw));pose.scale(-1.04f,-1.04f,1.04f);pose.translate(0,-1.501,0);
            ghost.model.renderToBuffer(pose,buffer.getBuffer(RenderType.entityTranslucentEmissive(ghost.skin)),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,
                    EffectColors.red(ghost.color),EffectColors.green(ghost.color),EffectColors.blue(ghost.color),.55f*fade*fade);
            pose.popPose();
        }
        buffer.endBatch();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){GHOSTS.clear();DASHES.clear();SAMPLED.clear();EffectColors.clearClient();}
    private HermesTrail(){}
}
