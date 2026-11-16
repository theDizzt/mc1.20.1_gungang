package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Uses the server's HUD/minimap number, with ordinary depth-tested world rendering. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class TeamNumberMarkers {
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(2048));
    private static boolean replaces(LivingEntity entity){
        return AcousticConfig.TEAM_NUMBER_MARKERS.get()&&TeamHud.teammate(entity)&&TeamPointHud.number(entity.getUUID())>0;
    }
    @SubscribeEvent public static void name(RenderNameTagEvent event){
        if(event.getEntity() instanceof LivingEntity entity&&replaces(entity))event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||mc.level==null||mc.player==null
                ||mc.options.hideGui||!AcousticConfig.TEAM_NUMBER_MARKERS.get()||PropHuntClient.active())return;
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof LivingEntity target)||!replaces(target)||!target.isAlive()
                    ||target.isInvisibleTo(mc.player)||target.distanceToSqr(mc.player)>96*96)continue;
            var position=target.getPosition(event.getPartialTick());
            var text=HudText.gungang(Integer.toString(TeamPointHud.number(target.getUUID())));
            float half=Math.max(6,(mc.font.width(text)+4)/2f);
            pose.pushPose();
            pose.translate(position.x-camera.x,position.y+target.getBbHeight()+.5-camera.y,position.z-camera.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.025f,-.025f,.025f);
            rectangle(pose.last().pose(),-half-1,-6,half+1,6,0xFF101820);
            pose.translate(0,0,.01);
            rectangle(pose.last().pose(),-half,-5,half,5,TeamHud.teamColor(target));
            pose.translate(0,0,.01);
            int color=target.getTeam()!=null&&target.getTeam().getName().equals("Red")?0xFF000000:0xFFFFFFFF;
            mc.font.drawInBatch(text,-mc.font.width(text)/2f,-3.5f,color,false,pose.last().pose(),BUFFER,Font.DisplayMode.NORMAL,0,15728880);
            pose.popPose();
        }
        BUFFER.endBatch();
    }
    private static void rectangle(org.joml.Matrix4f matrix,float x0,float y0,float x1,float y1,int color){
        var out=BUFFER.getBuffer(RenderType.textBackground());
        int r=color>>16&255,g=color>>8&255,b=color&255;
        out.vertex(matrix,x0,y0,0).color(r,g,b,255).uv2(15728880).endVertex();
        out.vertex(matrix,x0,y1,0).color(r,g,b,255).uv2(15728880).endVertex();
        out.vertex(matrix,x1,y1,0).color(r,g,b,255).uv2(15728880).endVertex();
        out.vertex(matrix,x1,y0,0).color(r,g,b,255).uv2(15728880).endVertex();
    }
    private TeamNumberMarkers(){}
}
