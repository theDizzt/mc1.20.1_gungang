package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class TurretClient {
    private static boolean holding;
    private static boolean reloading;
    private static int heartbeat;
    private static TurretEntity nearby(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||GadgetClient.placing())return null;
        return mc.level.getEntitiesOfClass(TurretEntity.class,mc.player.getBoundingBox().inflate(10),t->Turrets.canReload(mc.player,t)).stream().findFirst().orElse(null);
    }
    /** Consume the inventory key before vanilla opens a screen or the loadout listener sees it. */
    public static boolean key(int key,int scan,int action){
        var mc=Minecraft.getInstance();if(!mc.options.keyInventory.matches(key,scan))return false;
        if(action==GLFW.GLFW_RELEASE){if(!holding)return false;release();return true;}
        if(holding)return true;
        if(action!=GLFW.GLFW_PRESS||nearby()==null)return false;
        holding=true;reloading=true;heartbeat=0;LobbyClient.action("turretReload","down","");return true;
    }
    private static void release(){holding=false;reloading=false;heartbeat=0;if(Minecraft.getInstance().getConnection()!=null)LobbyClient.action("turretReload","up","");}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!holding)return;
        var mc=Minecraft.getInstance();
        if(!mc.isWindowActive()){release();return;}
        // Keep consuming repeats until key-up, including after topping off the magazine.
        if(mc.screen!=null||nearby()==null){if(reloading)LobbyClient.action("turretReload","up","");reloading=false;return;}
        if(!reloading)return;
        if(++heartbeat>=10){heartbeat=0;LobbyClient.action("turretReload","hold","");}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){holding=false;reloading=false;heartbeat=0;}
    @SubscribeEvent public static void hint(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.screen!=null||mc.options.hideGui||!HudVisibility.enabled())return;
        var turret=nearby();if(turret==null)return;
        String text="터렛 "+turret.ammo()+"/"+turret.capacity()+" · "+mc.options.keyInventory.getTranslatedKeyMessage().getString()+" 길게 재장전";
        var g=event.getGuiGraphics();g.drawString(mc.font,text,(g.guiWidth()-mc.font.width(text))/2,g.guiHeight()/2+14,0xFF8CC8FF,true);
    }
    private TurretClient(){}
}
