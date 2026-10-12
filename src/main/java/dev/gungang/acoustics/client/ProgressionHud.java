package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
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
    static void draw(GuiGraphics g,int x,int y,int width) {
        var font=Minecraft.getInstance().font;String level=String.valueOf(progress.level());
        g.drawString(font,level,x,y,0xFFFFFFFF,true);
        int left=x+font.width(level)+4,right=x+width;
        g.fill(left,y+5,right,y+8,0x80465264);
        g.fill(left,y+5,left+Math.round((right-left)*progress.fraction()),y+8,0xFFFFFFFF);
    }
    private ProgressionHud() { }
}
