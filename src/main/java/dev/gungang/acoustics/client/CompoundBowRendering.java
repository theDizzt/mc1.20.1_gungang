package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.CompoundBow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class CompoundBowRendering {
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hand(RenderHandEvent event){
        var player=Minecraft.getInstance().player;
        if(event.getHand()==InteractionHand.OFF_HAND&&player!=null&&CompoundBow.isBow(player.getMainHandItem()))event.setCanceled(true);
    }
}
