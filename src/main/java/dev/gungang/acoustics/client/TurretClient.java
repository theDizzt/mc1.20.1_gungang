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
    private static boolean holding,working,repair;
    private static int heartbeat;
    private static TurretEntity nearby(boolean fix){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||GadgetClient.placing())return null;
        return mc.level.getEntitiesOfClass(TurretEntity.class,mc.player.getBoundingBox().inflate(10),t->fix?Turrets.canRepair(mc.player,t):Turrets.canReload(mc.player,t)).stream().findFirst().orElse(null);
    }
    private static TurretEntity ownedNearby(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||GadgetClient.placing())return null;
        return mc.level.getEntitiesOfClass(TurretEntity.class,mc.player.getBoundingBox().inflate(10),t->Turrets.canInteract(mc.player,t)).stream().findFirst().orElse(null);
    }
    public static boolean key(int key,int scan,int action){return input(OffhandMacro.ATTACK.matches(key,scan),OffhandMacro.USE.matches(key,scan),action);}
    public static boolean mouse(int button,int action){return input(OffhandMacro.ATTACK.matchesMouse(button),OffhandMacro.USE.matchesMouse(button),action);}
    private static boolean input(boolean attack,boolean use,int action){
        if(!attack&&!use)return false;
        if(holding){if(action==GLFW.GLFW_RELEASE&&(repair?attack:use))release();return true;}
        if(action!=GLFW.GLFW_PRESS||OffhandMacro.busy())return false;
        boolean fix=attack;if(ownedNearby()==null)return false;
        holding=true;working=true;repair=fix;heartbeat=0;
        LobbyClient.action("turretReload",repair?"repair_down":"down","");return true;
    }
    public static boolean servicing(){return holding;}
    private static void release(){holding=false;working=false;heartbeat=0;if(Minecraft.getInstance().getConnection()!=null)LobbyClient.action("turretReload","up","");}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!holding)return;
        var mc=Minecraft.getInstance();
        if(!mc.isWindowActive()||mc.screen!=null||mc.isPaused()||mc.player==null||!mc.player.isAlive()){release();return;}
        if(nearby(repair)==null){if(working)LobbyClient.action("turretReload","up","");working=false;return;}
        if(working&&++heartbeat>=10){heartbeat=0;LobbyClient.action("turretReload",repair?"repair_hold":"hold","");}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){holding=false;working=false;heartbeat=0;}
    @SubscribeEvent public static void hint(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.screen!=null||mc.options.hideGui||!HudVisibility.enabled())return;
        var reload=nearby(false);var fix=nearby(true);var turret=ownedNearby();if(turret==null)return;
        String text="터렛 "+turret.ammo()+"/"+turret.capacity()+" · HP "+Math.round(turret.getHealth())+"/"+Math.round(turret.getMaxHealth());
        String keys=(reload==null?"":OffhandMacro.USE.getTranslatedKeyMessage().getString()+" 길게 재장전 ")+(fix==null?"":OffhandMacro.ATTACK.getTranslatedKeyMessage().getString()+" 길게 수리");
        if(keys.isEmpty())keys="탄약·체력 최대";
        var g=event.getGuiGraphics();g.drawCenteredString(mc.font,text,g.guiWidth()/2,g.guiHeight()/2+14,0xFF8CC8FF);
        g.drawCenteredString(mc.font,keys,g.guiWidth()/2,g.guiHeight()/2+26,0xFFFFFFFF);
    }
    private TurretClient(){}
}
