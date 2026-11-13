package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ScopeWheel runs first; HotbarLock runs last. Each wheel notch advances the selector. */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class FireModeWheel {
    private static java.lang.reflect.Method gun,operator,select;
    private static boolean unavailable;
    private static long lastChange;
    private static boolean changed;
    private static boolean coolingDown(){return changed&&System.nanoTime()-lastChange<(long)(AcousticConfig.WHEEL_FIRE_SELECT_DELAY.get()*1_000_000_000L);}
    private static void changed(){lastChange=System.nanoTime();changed=true;}
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event){
        var mc=Minecraft.getInstance();
        if(event.isCanceled()||!AcousticConfig.WHEEL_FIRE_SELECT.get()||event.getScrollDelta()==0
                ||mc.screen!=null||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator())return;
        var stack=mc.player.getMainHandItem();
        String action=M95.is(stack)?"m95Mode":ShurikenItem.is(stack)?"shurikenMode":TwinBlades.is(stack)?"bladesReset":null;
        if(action!=null){event.setCanceled(true);if(coolingDown())return;GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action(action,"",""));changed();return;}
        if(unavailable)return;
        try{
            if(gun==null){
                gun=Class.forName("com.tacz.guns.api.item.IGun").getMethod("getIGunOrNull",net.minecraft.world.item.ItemStack.class);
                var type=Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
                operator=type.getMethod("fromLocalPlayer",net.minecraft.client.player.LocalPlayer.class);
                select=type.getMethod("fireSelect");
            }
            if(gun.invoke(null,stack)==null)return;
            event.setCanceled(true);if(coolingDown())return;
            select.invoke(operator.invoke(null,mc.player));changed();
        }catch(ReflectiveOperationException|LinkageError error){
            unavailable=true;com.mojang.logging.LogUtils.getLogger().warn("TaCZ wheel fire selector unavailable",error);
        }
    }
    private FireModeWheel(){}
}
