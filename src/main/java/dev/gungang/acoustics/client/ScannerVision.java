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
public final class ScannerVision extends RenderType {
    private static final RenderType SILHOUETTE=create("gungang_infrared",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(new TextureStateShard(new ResourceLocation("minecraft","textures/block/white_concrete.png"),false,false))
                    .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(32768));
    private static CombatGadgets.ScanView view=new CombatGadgets.ScanView(java.util.List.of());
    private static PlayerModel<net.minecraft.client.player.AbstractClientPlayer> model;
    public static void receive(String json) {view=ServerFiles.GSON.fromJson(json,CombatGadgets.ScanView.class);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {view=new CombatGadgets.ScanView(java.util.List.of());model=null;}
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||mc.level==null||mc.player==null||view.targets().isEmpty())return;
        if(model==null)model=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        for(var target:view.targets()) {
            var tracked=mc.level.getPlayerByUUID(target.id());
            if(tracked!=null&&(!tracked.isAlive()||tracked.isSpectator()||tracked.isAlliedTo(mc.player)))continue;
            if(target.id().equals(mc.player.getUUID()))continue;
            var pos=tracked==null?new net.minecraft.world.phys.Vec3(target.x(),target.y(),target.z()):tracked.getPosition(event.getPartialTick());
            model.head.resetPose();model.hat.resetPose();model.body.resetPose();model.leftArm.resetPose();model.rightArm.resetPose();model.leftLeg.resetPose();model.rightLeg.resetPose();
            model.setAllVisible(true);model.young=false;model.riding=false;model.attackTime=0;model.crouching=target.crouching();
            model.leftSleeve.copyFrom(model.leftArm);model.rightSleeve.copyFrom(model.rightArm);
            model.leftPants.copyFrom(model.leftLeg);model.rightPants.copyFrom(model.rightLeg);model.jacket.copyFrom(model.body);
            pose.pushPose();pose.translate(pos.x-camera.x,pos.y-camera.y+(target.crouching()?1.3:1.5),pos.z-camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180-target.yaw()));pose.scale(-1,target.crouching()?-.85f:-1,1);
            int color=target.color();model.renderToBuffer(pose,BUFFER.getBuffer(SILHOUETTE),15728880,OverlayTexture.NO_OVERLAY,
                    (color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);
            pose.popPose();
        }
        BUFFER.endBatch();
    }
    private ScannerVision(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean affects,boolean sort,Runnable setup,Runnable clear) {
        super(name,format,mode,size,affects,sort,setup,clear);
    }
}
