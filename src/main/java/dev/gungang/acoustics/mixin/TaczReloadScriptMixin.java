package dev.gungang.acoustics.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TaCZ ticks a reload with whatever gun is in hand now, and gun scripts keep their reload state in the shooter's one
 * Lua cache, written by {@code start_reload}. When GunGang swaps the held gun mid-reload (weapon selection, respawn,
 * random rotation), the new gun's {@code tick_reload} reads a cache it never wrote (kar98: "interrupted_time" of nil)
 * and the error kicks the player ("Ticking player"). A reload therefore ends when the held gun changes, and a script
 * error ends the reload instead of propagating.
 */
@Pseudo
@Mixin(targets="com.tacz.guns.entity.shooter.LivingEntityReload",remap=false)
public abstract class TaczReloadScriptMixin {
    @Shadow private LivingEntity shooter;
    @Unique private static Field gungang$data,gungang$timestamp,gungang$gunItem,gungang$stateType;
    @Unique private static Method gungang$tickReload,gungang$getStateType,gungang$isReloading;
    @Unique private static Class<?> gungang$gunClass,gungang$stateClass;
    @Unique private static boolean gungang$unavailable,gungang$reported;
    @Unique private long gungang$stamp=-1;
    @Unique private String gungang$gun="";

    @Unique private static void gungang$resolve(Object self) throws ReflectiveOperationException {
        if(gungang$data!=null)return;
        Field data=self.getClass().getDeclaredField("data");data.setAccessible(true);
        Class<?> holder=data.getType();
        gungang$timestamp=holder.getField("reloadTimestamp");
        gungang$gunItem=holder.getField("currentGunItem");
        gungang$stateType=holder.getField("reloadStateType");
        gungang$gunClass=Class.forName("com.tacz.guns.api.item.gun.AbstractGunItem");
        gungang$stateClass=Class.forName("com.tacz.guns.api.entity.ReloadState");
        gungang$tickReload=gungang$gunClass.getMethod("tickReload",holder,ItemStack.class,LivingEntity.class);
        gungang$getStateType=gungang$stateClass.getMethod("getStateType");
        gungang$isReloading=gungang$getStateType.getReturnType().getMethod("isReloading");
        gungang$data=data;
    }

    @Inject(method="tickReloadState",at=@At("HEAD"),cancellable=true,require=0)
    private void gungang$guardedReload(CallbackInfoReturnable<Object> cir) {
        if(gungang$unavailable)return;
        try {
            gungang$resolve(this);
            Object data=gungang$data.get(this);
            if(data==null)return;
            long stamp=gungang$timestamp.getLong(data);
            if(stamp==-1){gungang$stamp=-1;return;}
            ItemStack stack=null;
            if(gungang$gunItem.get(data) instanceof Supplier<?> supplier&&supplier.get() instanceof ItemStack held)stack=held;
            String gun=stack!=null&&stack.hasTag()?stack.getTag().getString("GunId"):"";
            Object state=gungang$stateClass.getConstructor().newInstance();
            if(stamp!=gungang$stamp){gungang$stamp=stamp;gungang$gun=gun;}
            else if(!gun.equals(gungang$gun)){
                // Another gun is in hand: its script never started this reload.
                gungang$finish(data,state);cir.setReturnValue(state);return;
            }
            if(stack!=null&&gungang$gunClass.isInstance(stack.getItem())){
                try{state=gungang$tickReload.invoke(stack.getItem(),data,stack,shooter);}
                catch(InvocationTargetException failure){
                    if(!gungang$reported){
                        gungang$reported=true;
                        com.mojang.logging.LogUtils.getLogger().warn("[GunGang] TaCZ reload script of {} failed; the reload was stopped: {}",gun,String.valueOf(failure.getCause()));
                    }
                    state=gungang$stateClass.getConstructor().newInstance();
                }
            }
            gungang$finish(data,state);
            cir.setReturnValue(state);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {
            gungang$unavailable=true;
            com.mojang.logging.LogUtils.getLogger().warn("[GunGang] TaCZ reload guard disabled: {}",failure.toString());
        }
    }
    /** What TaCZ does after ticking: publish the state, and forget the reload once it ends. */
    @Unique private void gungang$finish(Object data,Object state) throws ReflectiveOperationException {
        Object type=gungang$getStateType.invoke(state);
        gungang$stateType.set(data,type);
        if(!(Boolean)gungang$isReloading.invoke(type)){gungang$timestamp.setLong(data,-1);gungang$stamp=-1;}
    }
}
