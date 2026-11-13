package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ScopeWheel runs first; HotbarLock runs last. Each wheel notch moves the fire selector: wheel down to the next mode,
 * wheel up to the previous one, wrapping around at either end.
 */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class FireModeWheel {
    private static java.lang.reflect.Method gun,operator,select,gunId,index,gunData,modes;
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
        boolean back=event.getScrollDelta()>0;
        String action=M95.is(stack)?"m95Mode":ShurikenItem.is(stack)?"shurikenMode":TwinBlades.is(stack)?"bladesReset":null;
        if(action!=null){event.setCanceled(true);if(coolingDown())return;GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action(action,back?"-1":"",""));changed();return;}
        if(unavailable)return;
        try{
            if(gun==null){
                gun=Class.forName("com.tacz.guns.api.item.IGun").getMethod("getIGunOrNull",net.minecraft.world.item.ItemStack.class);
                var type=Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
                operator=type.getMethod("fromLocalPlayer",net.minecraft.client.player.LocalPlayer.class);
                select=type.getMethod("fireSelect");
                gunId=Class.forName("com.tacz.guns.api.item.IGun").getMethod("getGunId",net.minecraft.world.item.ItemStack.class);
                index=Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class);
                gunData=Class.forName("com.tacz.guns.resource.index.CommonGunIndex").getMethod("getGunData");
                modes=Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData").getMethod("getFireModeSet");
            }
            var iGun=gun.invoke(null,stack);
            if(iGun==null)return;
            event.setCanceled(true);if(coolingDown())return;
            // TaCZ only steps forward; going back one mode is (modes - 1) steps forward round the cycle.
            int steps=back?Math.max(1,modeCount(iGun,stack)-1):1;
            var player=operator.invoke(null,mc.player);
            for(int i=0;i<steps;i++)select.invoke(player);
            changed();
        }catch(ReflectiveOperationException|LinkageError error){
            unavailable=true;com.mojang.logging.LogUtils.getLogger().warn("TaCZ wheel fire selector unavailable",error);
        }
    }
    /** Fire modes of the held gun (its pack data), at least 1. */
    private static int modeCount(Object iGun,net.minecraft.world.item.ItemStack stack)throws ReflectiveOperationException{
        var id=gunId.invoke(iGun,stack);
        var found=(java.util.Optional<?>)index.invoke(null,id);
        if(found.isEmpty())return 1;
        var list=(java.util.List<?>)modes.invoke(gunData.invoke(found.get()));
        return list==null?1:Math.max(1,list.size());
    }
    private FireModeWheel(){}
}
