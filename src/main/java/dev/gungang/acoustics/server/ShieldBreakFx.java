package dev.gungang.acoustics.server;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** A shield giving out: shards of it burst from in front of the holder, so breaking reads apart from lowering it. */
public final class ShieldBreakFx {
    public static void burst(LivingEntity holder){
        if(!(holder.level() instanceof ServerLevel level))return;
        var look=holder.getLookAngle();
        double x=holder.getX()+look.x*.6,y=holder.getY()+holder.getBbHeight()*.55,z=holder.getZ()+look.z*.6;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,new ItemStack(GadgetItems.SHIELD.get())),x,y,z,28,.3,.35,.3,.12);
        level.sendParticles(ParticleTypes.CRIT,x,y,z,14,.25,.3,.25,.25);
        level.sendParticles(ParticleTypes.POOF,x,y,z,6,.15,.2,.15,.03);
        level.playSound(null,holder.blockPosition(),SoundEvents.ITEM_BREAK,SoundSource.PLAYERS,1,.8f);
    }
    private ShieldBreakFx(){}
}
