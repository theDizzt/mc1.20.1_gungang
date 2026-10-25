package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server sends locations only to the scanning team, including targets outside entity tracking range. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class ScannerVision {
    private static final RenderType OUTLINE=RenderType.outline(new ResourceLocation("minecraft","textures/block/white_concrete.png"));
    private static boolean remoteDrawn;
    private static CombatGadgets.ScanView view=new CombatGadgets.ScanView(java.util.List.of());
    private static PlayerModel<net.minecraft.client.player.AbstractClientPlayer> model;
    public static void beginFrame(){remoteDrawn=false;}
    public static boolean remoteDrawn(){return remoteDrawn;}
    public static Integer color(net.minecraft.world.entity.Entity entity){
        var mc=Minecraft.getInstance();
        if(mc.player==null||entity==mc.player||!entity.isAlive()||entity.isSpectator()||entity.isAlliedTo(mc.player))return null;
        return view.targets().stream().filter(t->t.id().equals(entity.getUUID())).map(CombatGadgets.Target::color).findFirst().orElse(null);
    }
    public static void receive(String json) {view=ServerFiles.GSON.fromJson(json,CombatGadgets.ScanView.class);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {view=new CombatGadgets.ScanView(java.util.List.of());model=null;}
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||mc.level==null||mc.player==null||view.targets().isEmpty()||!mc.levelRenderer.shouldShowEntityOutlines())return;
        if(model==null)model=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        for(var target:view.targets()) {
            boolean tracked=false;
            for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(target.id())){tracked=true;break;}
            // Tracked players and bots use their real renderer, pose and equipment through vanilla glowing.
            if(tracked)continue;
            if(target.id().equals(mc.player.getUUID()))continue;
            var pos=new net.minecraft.world.phys.Vec3(target.x(),target.y(),target.z());
            model.head.resetPose();model.hat.resetPose();model.body.resetPose();model.leftArm.resetPose();model.rightArm.resetPose();model.leftLeg.resetPose();model.rightLeg.resetPose();
            model.setAllVisible(true);model.young=false;model.riding=false;model.attackTime=0;model.crouching=target.crouching();
            model.leftSleeve.copyFrom(model.leftArm);model.rightSleeve.copyFrom(model.rightArm);
            model.leftPants.copyFrom(model.leftLeg);model.rightPants.copyFrom(model.rightLeg);model.jacket.copyFrom(model.body);
            pose.pushPose();pose.translate(pos.x-camera.x,pos.y-camera.y+(target.crouching()?1.3:1.5),pos.z-camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180-target.yaw()));pose.scale(-1,target.crouching()?-.85f:-1,1);
            int color=target.color();var buffer=mc.renderBuffers().outlineBufferSource();
            buffer.setColor(color>>16&255,color>>8&255,color&255,255);
            model.renderToBuffer(pose,buffer.getBuffer(OUTLINE),15728880,OverlayTexture.NO_OVERLAY,1,1,1,1);remoteDrawn=true;
            pose.popPose();
        }
    }
    private ScannerVision(){}
}
