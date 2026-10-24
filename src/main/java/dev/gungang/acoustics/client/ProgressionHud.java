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
        // Level emblem followed by a slim track; the fill fades from mint into white.
        LevelEmblem.draw(g,progress.level(),x,y-3,14);
        int left=x+18,right=x+width,filled=Math.round((right-left)*progress.fraction());
        g.fill(left,y+5,right,y+7,0x50FFFFFF);
        for(int i=0;i<filled;i+=2)g.fill(left+i,y+5,left+Math.min(filled,i+2),y+7,ArenaScreen.blend(0xFF70CDBF,0xFFFFFFFF,i/(float)Math.max(1,right-left)));
    }
    private ProgressionHud() { }
}
