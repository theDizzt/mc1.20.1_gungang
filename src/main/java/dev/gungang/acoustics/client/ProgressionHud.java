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
    public static void receive(String json) {progress=ServerFiles.GSON.fromJson(json,XpTable.Progress.class);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {progress=XpTable.defaults().progress(0);}
    static int level() {return progress.level();}
    static void draw(GuiGraphics g,int x,int y,int width) {
        // Level emblem followed by a grid gauge: ten cells split by one-pixel gaps, a tick over every cell boundary
        // (a taller one at half), each cell filled in proportion from gold into white, and a bright head at the fill.
        LevelEmblem.draw(g,progress.level(),x,y-3,14);
        int left=x+18,right=x+width,span=right-left;if(span<CELLS*2)return;
        float fraction=Math.max(0,Math.min(1,progress.fraction()));
        for(int i=0;i<CELLS;i++){
            int x0=left+span*i/CELLS,x1=left+span*(i+1)/CELLS-(i<CELLS-1?1:0);
            g.fill(x0,y+5,x1,y+8,0x40FFFFFF);
            float cell=Math.max(0,Math.min(1,fraction*CELLS-i));
            if(cell>0)g.fill(x0,y+5,x0+Math.max(1,Math.round((x1-x0)*cell)),y+8,ArenaScreen.blend(TacticalStyle.GOLD,0xFFFFFFFF,(i+cell)/CELLS));
            if(i>0){boolean half=i==CELLS/2;g.fill(x0-1,half?y+2:y+3,x0,y+4,half?0xC0FFFFFF:0x60FFFFFF);}
        }
        if(fraction>0&&fraction<1){int head=left+Math.round(span*fraction);g.fill(head-1,y+4,head,y+9,0xFFFFFFFF);}
        // Progress to the next level in tiny half-size digits, right-aligned above the gauge.
        var font=net.minecraft.client.Minecraft.getInstance().font;
        String percent=String.format(java.util.Locale.ROOT,"%.2f%%",fraction*100);
        g.pose().pushPose();g.pose().translate(right-font.width(percent)*.5f,y-3,0);g.pose().scale(.5f,.5f,1);
        g.drawString(font,percent,0,0,0xD0FFFFFF,false);
        g.pose().popPose();
    }
    private static final int CELLS=10;
    private ProgressionHud() { }
}
