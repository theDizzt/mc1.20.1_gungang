package dev.gungang.acoustics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Only the owning team's packet reaches this client. All HUD surfaces share that same state. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class TeamPointHud {
    public static final KeyMapping POINT=new KeyMapping("gungang.key.team_point",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_Q,"key.categories.gungang");
    private static TeamPointState.View view=empty();
    private static TeamPointState.View empty(){return new TeamPointState.View("",0,List.of(),List.of());}
    public static void receive(String json){view=ServerFiles.GSON.fromJson(json,TeamPointState.View.class);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){view=empty();}
    private static boolean active() {
        var mc=Minecraft.getInstance();
        return mc.level!=null&&mc.player!=null&&view!=null&&TeamPointState.validTeam(view.team())&&mc.player.getTeam()!=null
                &&mc.player.getTeam().getName().equals(view.team().equals("ALPHA")?"Red":"Blue")
                &&view.members().stream().anyMatch(m->m.id().equals(mc.player.getUUID()));
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        var mc=Minecraft.getInstance();
        if(!active()||mc.screen!=null||!POINT.matches(event.getKey(),event.getScanCode()))return;
        // Q must not also discard the held weapon. Outside a team match vanilla input is untouched.
        if(mc.options.keyDrop.matches(event.getKey(),event.getScanCode())) {
            while(mc.options.keyDrop.consumeClick()){}mc.options.keyDrop.setDown(false);
        }
        while(POINT.consumeClick()){}
        if(event.getAction()==GLFW.GLFW_PRESS&&!mc.isPaused()&&mc.player.isAlive()&&!mc.player.isSpectator())
            GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.TeamPointRequest());
    }
    public static void badge(GuiGraphics g,UUID id,int x,int y,int size) {
        if(!active())return;
        var member=view.members().stream().filter(m->m.id().equals(id)).findFirst().orElse(null);
        if(member!=null)drawBadge(g,member.number(),x,y,size,view.color());
    }
    static void drawBadge(GuiGraphics g,int number,int x,int y,int size,int color){number(g,number,x+size-7,y+size-6,color);}
    private static void number(GuiGraphics g,int number,int cx,int cy,int color) {
        var font=Minecraft.getInstance().font;String text=Integer.toString(number);
        int width=Math.max(10,font.width(text)+4);
        g.fill(cx-width/2-1,cy-6,cx+(width+1)/2+1,cy+6,0xFF101820);
        g.fill(cx-width/2,cy-5,cx+(width+1)/2,cy+5,color|0xFF000000);
        g.drawString(font,text,cx-font.width(text)/2,cy-4,0xFFFFFFFF,false);
    }
    private static List<TeamPointState.Point> points() {
        if(!active())return List.of();
        String dimension=Minecraft.getInstance().level.dimension().location().toString();
        return view.points().stream().filter(p->p.dimension().equals(dimension)).toList();
    }
    static void compass(GuiGraphics g,int center,int y,double heading,double pixelsPerDegree) {
        var player=Minecraft.getInstance().player;if(player==null)return;
        drawCompass(g,points(),player.position(),view.color(),center,Math.max(6,y),heading,pixelsPerDegree);
    }
    static void drawCompass(GuiGraphics g,List<TeamPointState.Point> points,Vec3 position,int color,int center,int y,double heading,double pixelsPerDegree) {
        for(var point:points) {
            double angle=CompassMath.difference(CompassMath.bearing(point.x()-position.x,point.z()-position.z),heading);
            number(g,point.number(),center+(int)Math.round(Mth.clamp(angle,-60,60)*pixelsPerDegree),y,color);
        }
    }
    static void minimap(GuiGraphics g,double[] bounds,int left,int top,int width,int height,double scale) {
        drawMinimap(g,points(),view.color(),bounds,left,top,width,height,scale);
    }
    static void drawMinimap(GuiGraphics g,List<TeamPointState.Point> points,int color,double[] bounds,int left,int top,int width,int height,double scale) {
        for(var point:points) {
            int x=left+(int)Math.round((point.x()-bounds[0])*scale),z=top+(int)Math.round((point.z()-bounds[1])*scale);
            int mx=Math.min(7,width/2),my=Math.min(6,height/2);
            number(g,point.number(),Mth.clamp(x,left+mx,left+width-mx),Mth.clamp(z,top+my,top+height-my),color);
        }
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!active()||mc.options.hideGui)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffer=mc.renderBuffers().bufferSource();
        for(var point:points()) {
            Vec3 at=new Vec3(point.x(),point.y()+.5,point.z());double distance=at.distanceTo(camera);
            float scale=(float)Mth.clamp(distance*.0018,.025,.18);
            pose.pushPose();pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-scale,-scale,scale);
            String label=Integer.toString(point.number());
            mc.font.drawInBatch(label,-mc.font.width(label)/2f,0,0xFFFFFFFF,false,pose.last().pose(),buffer,Font.DisplayMode.SEE_THROUGH,view.color()|0xFF000000,15728880);
            String meters=Math.round(distance)+"m";
            mc.font.drawInBatch(meters,-mc.font.width(meters)/2f,10,0xFFFFFFFF,true,pose.last().pose(),buffer,Font.DisplayMode.SEE_THROUGH,0x80000000,15728880);
            pose.popPose();
        }
        buffer.endBatch();
    }
    private TeamPointHud() { }
}
