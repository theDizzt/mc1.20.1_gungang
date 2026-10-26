package dev.gungang.acoustics.mixin;

import java.lang.reflect.Field;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TaCZ keeps one Lua script cache per shooter and clears it only when the draw packet arrives, but ticks heat for
 * whatever is in the main hand every tick. A tick between a slot change and that packet hands the new gun's script
 * the previous gun's table (LAS-5 to railgun: "procd" is nil and tick_heat crashes the server). Clear it as draw would.
 */
@Pseudo
@Mixin(targets="com.tacz.guns.entity.shooter.LivingEntityHeat",remap=false)
public abstract class TaczHeatScriptMixin {
    @Shadow private LivingEntity shooter;
    @Unique private static Field gungang$data,gungang$scriptData;
    @Unique private static boolean gungang$unavailable;
    @Unique private String gungang$scriptGun="";
    @Inject(method="tickHeat",at=@At("HEAD"),require=0)
    private void gungang$resetForeignScriptData(CallbackInfo ci) {
        var stack=shooter.getMainHandItem();
        String gun=stack.hasTag()?stack.getTag().getString("GunId"):"";
        if(gun.isEmpty()||gun.equals(gungang$scriptGun)||gungang$unavailable)return;
        try {
            if(gungang$data==null) {
                gungang$data=getClass().getDeclaredField("data");gungang$data.setAccessible(true);
                gungang$scriptData=gungang$data.getType().getField("scriptData");
            }
            Object holder=gungang$data.get(this);
            // The first gun seen keeps its cache: nothing else has written to it yet.
            if(holder!=null&&!gungang$scriptGun.isEmpty())gungang$scriptData.set(holder,null);
            gungang$scriptGun=gun;
        } catch(ReflectiveOperationException|RuntimeException failure) {
            gungang$unavailable=true;
            com.mojang.logging.LogUtils.getLogger().warn("[GunGang] TaCZ script cache reset disabled: {}",failure.toString());
        }
    }
}
