package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.GlueGunItem;
import dev.gungang.acoustics.server.GungangNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 37 glue gun controls: holding the attack key fires (paced like the server), TaCZ's reload key swaps the glue stick
 * (TaCZ itself ignores it for a non-gun item) and holding the use key zooms.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class GlueGunInput {
    private static KeyMapping reload;
    private static boolean keyMissing,reloadDown;
    private static long lastShot=Long.MIN_VALUE/2;
    /** Eased zoom, 0 (none) to 1 (fully zoomed). */
    private static float zoom,zoomOld;
    /** When the last shot left, for the first-person recoil (System.nanoTime seconds). */
    private static double shotAt=-10;

    private static boolean holding(){var mc=Minecraft.getInstance();return mc.player!=null&&GlueGunItem.is(mc.player.getMainHandItem());}
    private static void send(String action){GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action(action,"",""));}

    /** Neither button does its vanilla job with the glue gun: no swing, no block use. */
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void keys(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();
        if(mc.screen!=null||!holding()||!(event.isAttack()||event.isUseItem()))return;
        event.setCanceled(true);event.setSwingHand(false);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        zoomOld=zoom;
        boolean held=holding()&&mc.screen==null&&mc.getConnection()!=null&&mc.player.isAlive()&&!mc.player.isSpectator();
        zoom=Math.max(0,Math.min(1,zoom+(held&&mc.options.keyUse.isDown()?.34f:-.34f)));
        if(!held){reloadDown=false;return;}
        if(reload==null&&!keyMissing){
            try{reload=(KeyMapping)Class.forName("com.tacz.guns.client.input.ReloadKey").getField("RELOAD_KEY").get(null);}
            catch(ReflectiveOperationException|LinkageError missing){keyMissing=true;}
        }
        var stack=mc.player.getMainHandItem();
        boolean reloading=reload!=null&&reload.isDown();
        if(reloading&&!reloadDown)send("glueReload");
        reloadDown=reloading;
        if(!mc.options.keyAttack.isDown()||GlueGunItem.reloadEnd(stack)>0||FreeForAllHud.blocked()||GadgetClient.placing())return;
        // Fire only as fast as the server does, so every shot seen is a real shot.
        long now=mc.level.getGameTime();
        if(now-lastShot>=0&&now-lastShot<GlueGunItem.shotTicks(stack))return;
        lastShot=now;
        GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("glueFire",zoom>=.5f?"zoom":"",""));
        if(GlueGunItem.ammo(stack)>0||mc.player.getAbilities().instabuild)shotAt=System.nanoTime()/1e9;
    }
    /** Right click held: narrow the view to the configured multiplier, eased over a few ticks. */
    @SubscribeEvent public static void fov(net.minecraftforge.client.event.ViewportEvent.ComputeFov event){
        if(zoom<=0&&zoomOld<=0||!holding())return;
        var mc=Minecraft.getInstance();
        float t=zoomOld+(zoom-zoomOld)*(float)event.getPartialTick();t=t*t*(3-2*t);
        double multiplier=GlueGunItem.zoom(mc.player.getMainHandItem());
        event.setFOV(event.getFOV()*(1-(1-multiplier)*t));
    }
    /** Eased aim-down-sights progress, 0 (hip) to 1 (zoomed, gun centred). */
    static float aim(float partial){float t=zoomOld+(zoom-zoomOld)*partial;return t*t*(3-2*t);}
    /** Recoil strength: 1 the moment a shot leaves, settling back to 0 over a quarter second. */
    static float recoil(){double since=System.nanoTime()/1e9-shotAt;if(since<0||since>=.25)return 0;float t=(float)(since/.25);return (1-t)*(1-t);}
    private GlueGunInput(){}
}
