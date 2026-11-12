package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class ArcShockClient {
    private static LocalPlayer target;
    private static long until;
    private static float yaw,pitch;
    public static void receive(int ticks,float y,float p){
        target=Minecraft.getInstance().player;if(target==null)return;
        until=target.tickCount+Math.max(0,Math.min(20,ticks));yaw=y;pitch=p;hold();
    }
    public static boolean locked(){
        var player=Minecraft.getInstance().player;
        return player!=null&&player==target&&player.isAlive()&&!player.isSpectator()&&player.tickCount<until;
    }
    private static void hold(){if(locked()){target.setYRot(yaw);target.yRotO=yaw;target.setXRot(pitch);target.xRotO=pitch;}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END){if(locked())hold();else target=null;}}
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){target=null;until=0;}
    private ArcShockClient(){}
}
