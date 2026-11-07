package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** TaCZ hides the vanilla crosshair for this gun, so draw after the HUD. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class BlowpipeCrosshair {
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||mc.options.hideGui||mc.player.isSpectator()||!mc.options.getCameraType().isFirstPerson())return;
        var stack=mc.player.getMainHandItem();var tag=stack.getTag();
        if(tag==null||!"bggp:blowgun".equals(tag.getString("GunId")))return;
        var g=event.getGuiGraphics();int x=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()/2;
        g.fill(x-5,y-1,x+6,y+2,0xCC000000);g.fill(x-1,y-5,x+2,y+6,0xCC000000);
        g.fill(x-4,y,x+5,y+1,0xFFFFFFFF);g.fill(x,y-4,x+1,y+5,0xFFFFFFFF);
    }
    private BlowpipeCrosshair(){}
}
