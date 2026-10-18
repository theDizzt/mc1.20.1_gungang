package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class HudVisibility {
    private static boolean enabled=true;
    public static boolean enabled(){return enabled;}
    public static void receive(boolean value){enabled=value;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){enabled=true;}
    private HudVisibility(){}
}
