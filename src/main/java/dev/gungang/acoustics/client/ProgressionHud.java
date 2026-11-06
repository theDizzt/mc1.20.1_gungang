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
        // Level emblem followed by one solid gauge: dark outlined track, gold gradient fill with a top highlight and a
        // sheen sweeping across it now and then, notches every 10% (taller at half), freshly gained XP shown in white
        // until the gold catches up, and a flash on level-up.
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
        // Track with a one-pixel outline.
        g.fill(left-1,top-1,right+1,bottom+1,0xC0000000);
        g.fill(left,top,right,bottom,0xFF23241D);
        int gold=left+Math.round(span*shown),gained=left+Math.round(span*target);
        if(gained>gold)g.fill(gold,top,gained,bottom,0xE0FFFFFF);
        if(gold>left){
            // Gradient from deep gold to bright gold, a lighter top line and a darker bottom line.
            for(int px=left;px<gold;px++){
                float t=(px-left)/(float)Math.max(1,span);
                g.fill(px,top,px+1,bottom,ArenaScreen.blend(0xFF9C7A2A,0xFFF2D27A,t));
            }
            g.fill(left,top,gold,top+1,0x70FFFFFF);
            g.fill(left,bottom-1,gold,bottom,0x40000000);
            // Sheen: a soft bright band crossing the fill every 4 seconds.
            float phase=(now%4000)/1400f;
            if(phase<1){
                int c=left+Math.round((span+16)*phase)-8;
                for(int px=Math.max(left,c-6);px<Math.min(gold,c+6);px++){
                    int a=Math.round(70*(1-Math.abs(px-c)/6f));
                    if(a>0)g.fill(px,top,px+1,bottom,a<<24|0xFFFFFF);
                }
            }
            if(!max&&gold<right)g.fill(gold-1,top-1,gold,bottom+1,0xFFFFFFFF);
        }
        // Notches every 10% over the whole bar, a taller one at half.
        for(int i=1;i<10;i++){
            int nx=left+span*i/10;
            g.fill(nx,top,nx+1,bottom,0x55000000);
            if(i==5)g.fill(nx,top-2,nx+1,top-1,0xA0FFFFFF);
        }
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
