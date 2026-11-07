package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class ProgressionHud {
    private static XpTable.Progress progress=XpTable.defaults().progress(0);
    private static boolean synced;
    /** The first sync after login snaps the gauge instead of playing a level-up from the default level. */
    public static void receive(String json) {progress=ServerFiles.GSON.fromJson(json,XpTable.Progress.class);if(!synced){synced=true;shown=-1;}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {progress=XpTable.defaults().progress(0);shown=-1;shownLevel=-1;synced=false;}
    static int level() {return progress.level();}
    /** The gauge's animated state: the gold fill eases toward the real progress; a level-up flashes and refills. */
    private static float shown=-1;private static int shownLevel=-1;private static long lastFrame,flashUntil;
    static void draw(GuiGraphics g,int x,int y,int width) {
        // Level emblem followed by the gold gauge (TacticalStyle.goldGauge, see-through track): freshly gained XP shows in
        // white until the gold catches up, and a level-up flashes.
        LevelEmblem.draw(g,progress.level(),x,y-3,14);
        int left=x+18,right=x+width,span=right-left;if(span<20)return;
        boolean max=progress.level()>=91;
        float target=max?1:Math.max(0,Math.min(1,progress.fraction()));
        long now=net.minecraft.Util.getMillis();float dt=lastFrame==0?1:Math.min(1,(now-lastFrame)/1000f);lastFrame=now;
        if(shown<0||shownLevel<0){shown=target;shownLevel=progress.level();}
        else if(progress.level()>shownLevel){shown=0;flashUntil=now+500;shownLevel=progress.level();}
        else if(progress.level()<shownLevel||target<shown){shown=target;shownLevel=progress.level();}
        else shown+=(target-shown)*Math.min(1,dt*4);
        if(Math.abs(target-shown)<.0005f)shown=target;
        int top=y+3,bottom=y+8;
        TacticalStyle.goldGauge(g,left,top,span,bottom-top,shown,target,!max);
        long flash=flashUntil-now;
        if(flash>0)g.fill(left-1,top-1,right+1,bottom+1,(int)(flash/500f*0xB0)<<24|0xFFF2D27A);
        // Progress to the next level in half-size digits, right-aligned above the gauge (MAX at the top level).
        var font=net.minecraft.client.Minecraft.getInstance().font;
        String percent=max?"MAX":String.format(java.util.Locale.ROOT,"%.2f%%",target*100);
        g.pose().pushPose();g.pose().translate(right-font.width(percent)*.5f,y-3,0);g.pose().scale(.5f,.5f,1);
        g.drawString(font,percent,0,0,max?0xFFF2D27A:0xF0F2E2B0,true);
        g.pose().popPose();
    }
    private ProgressionHud() { }
}
