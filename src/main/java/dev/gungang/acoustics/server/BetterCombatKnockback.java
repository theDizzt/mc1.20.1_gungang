package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.lang.invoke.*;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Better Combat lowers a target's knockback multiplier (down to 0.1) for one swing and restores it afterwards; a swing
 * that throws midway never restores it, so that entity shrugs off every later knockback until it is recreated.
 * Swings run while packets are handled, never inside an entity tick, so restoring it every tick cannot cut one short.
 * Optional integration: without Better Combat this does nothing.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class BetterCombatKnockback {
    private static final MethodHandle RESET=find();
    private static boolean failed;
    private static MethodHandle find(){
        try{
            var type=Class.forName("net.bettercombat.logic.knockback.ConfigurableKnockback");
            return MethodHandles.publicLookup().findVirtual(type,"setKnockbackMultiplier_BetterCombat",MethodType.methodType(void.class,float.class))
                    .asType(MethodType.methodType(void.class,Object.class,float.class));
        }catch(ReflectiveOperationException absent){return null;}
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        if(RESET==null||failed||event.getEntity().level().isClientSide)return;
        try{RESET.invokeExact((Object)event.getEntity(),1f);}
        catch(Throwable error){failed=true;com.mojang.logging.LogUtils.getLogger().warn("Better Combat knockback reset unavailable",error);}
    }
    private BetterCombatKnockback(){}
}
