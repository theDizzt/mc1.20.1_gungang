package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The bleeding status: a victim takes {@code damage} once a second until its time runs out. Applied by the katana spin
 * and by any weapon with {@code elements.bleedDamage}. A new bleed replaces a weaker or shorter one only where it is
 * stronger or longer, so a light graze never cuts a heavy bleed short. The registered {@link BleedingEffect} runs
 * alongside for the status icon; removing it (health packs, respawns, milk ...) ends the bleed, as do safe zones.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class Bleeding {
    private record Bleed(UUID attacker,int ticksLeft,double damage) { }
    private static final Map<LivingEntity,Bleed> BLEEDING=new WeakHashMap<>();
    private static final net.minecraft.core.particles.DustParticleOptions BLOOD=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.55f,0,0),1.1f);

    /** Starts or extends bleeding: the stronger damage and the longer time of the running and the new bleed win. */
    public static void apply(LivingEntity victim,LivingEntity attacker,int ticks,double damagePerSecond){
        if(victim==null||victim.level().isClientSide||ticks<=0||damagePerSecond<=0||victim==attacker)return;
        var running=BLEEDING.get(victim);
        UUID by=attacker==null?running==null?null:running.attacker():attacker.getUUID();
        if(running!=null){ticks=Math.max(ticks,running.ticksLeft());damagePerSecond=Math.max(damagePerSecond,running.damage());}
        BLEEDING.put(victim,new Bleed(by,ticks,damagePerSecond));
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(GimmickBlocks.BLEEDING.get(),ticks,0,false,false,true));
    }
    public static boolean bleeding(LivingEntity entity){return BLEEDING.containsKey(entity);}
    /** Bleed damage still to come: one tick every 20 ticks left (the last one at 0). */
    static double pending(LivingEntity entity){
        var bleed=BLEEDING.get(entity);if(bleed==null||bleed.ticksLeft()<=0)return 0;
        return ((bleed.ticksLeft()-1)/20+1)*bleed.damage();
    }
    public static void clear(LivingEntity entity){
        if(BLEEDING.remove(entity)!=null&&!entity.level().isClientSide)entity.removeEffect(GimmickBlocks.BLEEDING.get());
    }
    @SubscribeEvent public static void died(LivingDeathEvent event){BLEEDING.remove(event.getEntity());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||BLEEDING.isEmpty())return;
        // Damage can kill and clear entries, so iterate over a snapshot.
        for(var entry:new ArrayList<>(BLEEDING.entrySet())){
            var victim=entry.getKey();var bleed=entry.getValue();
            if(!victim.isAlive()||victim.isRemoved()||victim.isSpectator()||SafeZones.contains(victim)){clear(victim);continue;}
            // The status was removed (cure, milk, immunity): the bleed ends with it.
            if(!victim.hasEffect(GimmickBlocks.BLEEDING.get())){BLEEDING.remove(victim);continue;}
            int left=bleed.ticksLeft()-1;
            if(left<=0)BLEEDING.remove(victim);else BLEEDING.put(victim,new Bleed(bleed.attacker(),left,bleed.damage()));
            if(!(victim.level() instanceof ServerLevel level))continue;
            if(left%5==0)level.sendParticles(BLOOD,victim.getX(),victim.getY()+victim.getBbHeight()*.55,victim.getZ(),2,victim.getBbWidth()*.3,victim.getBbHeight()*.2,victim.getBbWidth()*.3,0);
            if(left%20!=0)continue;
            var attacker=bleed.attacker()==null?null:level.getEntity(bleed.attacker());
            var type=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.MAGIC);
            victim.invulnerableTime=0;
            // Bleeding keeps the attacker for kill credit, but a bleed tick must not shove the victim towards them.
            CombatRules.quietly(()->victim.hurt(new DamageSource(type,null,attacker),(float)bleed.damage()));
        }
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){BLEEDING.clear();}
    private Bleeding(){}
}
