package dev.gungang.acoustics.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TaCZ clears the hurt cooldown before every bullet, so each pellet of a shotgun blast is a full hurt: one damage
 * event packet (red flash, hurt sound, camera tilt) per pellet, twice when part of the damage ignores armor, to
 * everyone tracking the victim. One per entity per tick looks and sounds the same; health still updates every hit.
 */
@Mixin(LivingEntity.class)
public abstract class DamageBroadcastMixin {
    @Unique private long gungang$damageBroadcastTick=Long.MIN_VALUE;
    @Redirect(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;broadcastDamageEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void gungang$oncePerTick(Level level,Entity entity,DamageSource source){
        long now=level.getGameTime();
        if(!level.isClientSide()&&gungang$damageBroadcastTick==now)return;
        gungang$damageBroadcastTick=now;
        level.broadcastDamageEvent(entity,source);
    }
}
