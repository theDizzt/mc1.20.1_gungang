package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class BombWorldHud {
    private static final ResourceLocation BEAM=new ResourceLocation("minecraft","textures/entity/beacon_beam.png");
    private static BombMatch.View view;
    private static long received;
    public static void receive(MatchSnapshot state){
        view=state!=null&&state.active()?state.bomb():null;
        var level=Minecraft.getInstance().level;received=level==null?0:level.getGameTime();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();
        if(view==null||mc.level==null||!mc.level.dimension().location().toString().equals(view.dimension())||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        for(int i=0;i<2;i++){
            var point=i==0?view.pointA():view.pointB();String site=i==0?"A":"B";
            var area=i==0?view.areaA():view.areaB();
            double x=area==null?point.x():area.centerX()-.5,z=area==null?point.z():area.centerZ()-.5;
            pose.pushPose();pose.translate(x-camera.x,point.y()-camera.y,z-camera.z);
            BeaconRenderer.renderBeaconBeam(pose,buffers,BEAM,event.getPartialTick(),1,mc.level.getGameTime(),0,256,new float[]{1,.45f,.05f},.12f,.18f);
            // A flat one-block X, slightly above the floor to avoid z-fighting.
            var consumer=buffers.getBuffer(RenderType.lightning());var matrix=pose.last().pose();
            for(int diagonal:new int[]{-1,1}){
                float z0=diagonal==1?.08f:.92f,z1=diagonal==1?.92f:.08f;
                consumer.vertex(matrix,.05f,.015f,z0).color(255,115,13,230).endVertex();
                consumer.vertex(matrix,.11f,.015f,z0).color(255,115,13,230).endVertex();
                consumer.vertex(matrix,.95f,.015f,z1).color(255,115,13,230).endVertex();
                consumer.vertex(matrix,.89f,.015f,z1).color(255,115,13,230).endVertex();
            }
            pose.pushPose();pose.translate(.5,1.6,.5);pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.025f,-.025f,.025f);
            String text=site;
            if(site.equals(view.plantedSite())&&view.plantedPosition()==null){
                float ticks=Math.max(0,view.fuseTicks()-(mc.level.getGameTime()-received)-event.getPartialTick());
                text=String.format(java.util.Locale.ROOT,"%.1f초",ticks/20f);
            }
            if(!site.equals(view.plantedSite())||view.plantedPosition()==null)
                mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,0xFFFFFFFF,true,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x80000000,15728880);
            pose.popPose();pose.popPose();
        }
        if(view.plantedPosition()!=null){
            var p=view.plantedPosition();pose.pushPose();pose.translate(p.x()+.5-camera.x,p.y()+1.6-camera.y,p.z()+.5-camera.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.025f,-.025f,.025f);
            float ticks=Math.max(0,view.fuseTicks()-(mc.level.getGameTime()-received)-event.getPartialTick());
            String text=String.format(java.util.Locale.ROOT,"%.1f초",ticks/20f);
            mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,0xFFFFFFFF,true,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x80000000,15728880);pose.popPose();
        }
        buffers.endBatch();
    }
    private BombWorldHud(){}
}
