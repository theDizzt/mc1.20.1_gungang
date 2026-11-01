package dev.gungang.acoustics.server;

import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** A separate effect because this mod intentionally disables vanilla regeneration potions. */
public final class HotSpringEffect extends MobEffect {
    public HotSpringEffect(){super(MobEffectCategory.BENEFICIAL,0x65D7FA);}
    public static boolean eligible(LivingEntity entity){return entity instanceof Player&&entity.isAlive()&&!entity.isSpectator()&&!entity.isOnFire()&&entity.getRemainingFireTicks()<=0;}
    public static void touch(LivingEntity entity){
        if(entity.level().isClientSide)return;
        if(!eligible(entity)){entity.removeEffect(HotSpringContent.HEALING.get());return;}
        // Contact refreshes duration, but not the heal phase (overlapping cells cannot stack heals).
        var current=entity.getEffect(HotSpringContent.HEALING.get());
        int duration=30;if(current!=null)duration-=Math.floorMod(duration-current.getDuration(),10);
        entity.addEffect(new MobEffectInstance(HotSpringContent.HEALING.get(),duration,0,false,false,true));
    }
    @Override public boolean isDurationEffectTick(int duration,int amplifier){return duration%10==0;}
    @Override public void applyEffectTick(LivingEntity entity,int amplifier){if(!entity.level().isClientSide&&eligible(entity))entity.heal(1);}
}
