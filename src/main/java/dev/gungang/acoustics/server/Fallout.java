package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 30 GP-31 Ultimatum: the shell's hit deals no damage, its blast deals the gun pack's explosion damage. Where it
 * bursts it leaves a green fallout cloud
 * ({@link ExtraGadgetConfig.Fallout}). Whichever cloud a body touches, it takes the fallout damage at once; each
 * interval it is still inside a cloud the next hit is a combo, startDamage + damageStep per combo up to maxDamage
 * (16, 19, 22, 25, 28, 31, 31 ...). Leaving every cloud before the next interval ends the combo. Teammates and the
 * shooter are hurt too.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class Fallout {
    public static final String GUN_ID="zeta:ultimatum";
    private static final DustParticleOptions GREEN=new DustParticleOptions(new org.joml.Vector3f(.42f,1f,.18f),2.2f);
    private static final DustParticleOptions DARK=new DustParticleOptions(new org.joml.Vector3f(.16f,.55f,.08f),3f);
    private record Zone(ServerLevel level,Vec3 center,long end,UUID owner) { }
    private static final class Exposure { int hits; long next; }
    private static final List<Zone> ZONES=new ArrayList<>();
    private static final Map<LivingEntity,Exposure> EXPOSED=new WeakHashMap<>();

    /** Standing in a fallout cloud (or just left it, until its next combo tick was due). */
    static boolean exposed(LivingEntity body){return EXPOSED.containsKey(body);}
    public static boolean is(ItemStack stack){var tag=stack.getTag();return tag!=null&&GUN_ID.equals(tag.getString("GunId"));}
    static boolean shell(Entity entity){return entity instanceof Las5Damage.BulletAccess bullet&&GUN_ID.equals(String.valueOf(bullet.getGunId()));}
    /** The explosion of an Ultimatum shell. */
    static boolean shell(Explosion explosion){
        return shell(explosion.getExploder())||explosion.getDamageSource()!=null&&shell(explosion.getDamageSource().getDirectEntity());
    }

    /** The shell's direct hit does nothing; only its blast (the gun pack's explosion damage) hurts. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent event){
        var source=event.getSource();
        if(!event.getEntity().level().isClientSide&&shell(source.getDirectEntity())&&!source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void burst(ExplosionEvent.Detonate event){
        if(!(event.getLevel() instanceof ServerLevel level)||!shell(event.getExplosion()))return;
        event.getAffectedBlocks().clear();
        var owner=event.getExplosion().getIndirectSourceEntity();
        if(owner==null&&event.getExplosion().getExploder() instanceof net.minecraft.world.entity.projectile.Projectile projectile)owner=projectile.getOwner() instanceof LivingEntity living?living:null;
        start(level,event.getExplosion().getPosition(),owner==null?null:owner.getUUID());
    }
    static void start(ServerLevel level,Vec3 center,UUID owner){
        ZONES.add(new Zone(level,center,level.getGameTime()+ExtraGadgetConfig.ticks(ExtraGadgetConfig.fallout.durationSeconds()),owner));
        level.sendParticles(DARK,center.x,center.y+.5,center.z,80,1.6,.6,1.6,0);
        level.playSound(null,net.minecraft.core.BlockPos.containing(center),net.minecraft.sounds.SoundEvents.GENERIC_EXTINGUISH_FIRE,net.minecraft.sounds.SoundSource.PLAYERS,1.4f,.5f);
    }
    /** Whether a body's box touches the cloud. */
    private static boolean inside(LivingEntity body,Zone zone,double radius){
        var box=body.getBoundingBox();var c=zone.center();
        double dx=Math.max(Math.max(box.minX-c.x,c.x-box.maxX),0),dy=Math.max(Math.max(box.minY-c.y,c.y-box.maxY),0),dz=Math.max(Math.max(box.minZ-c.z,c.z-box.maxZ),0);
        return dx*dx+dy*dy+dz*dz<=radius*radius;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||ZONES.isEmpty()&&EXPOSED.isEmpty())return;
        var config=ExtraGadgetConfig.fallout;double radius=config.radius();
        ZONES.removeIf(zone->zone.level().getGameTime()>=zone.end()||event.getServer().getLevel(zone.level().dimension())!=zone.level());
        // Each body inside some cloud, with the first cloud found (its owner takes the credit).
        var inside=new LinkedHashMap<LivingEntity,Zone>();
        for(var zone:ZONES){
            var level=zone.level();var c=zone.center();
            if(level.getGameTime()%3==0){
                int count=(int)Math.min(60,radius*radius*1.6);
                for(int i=0;i<count;i++){
                    double angle=level.random.nextDouble()*Math.PI*2,r=radius*Math.sqrt(level.random.nextDouble());
                    level.sendParticles(GREEN,c.x+Math.cos(angle)*r,c.y+level.random.nextDouble()*Math.min(2.5,radius),c.z+Math.sin(angle)*r,1,0,.05,0,0);
                }
            }
            for(var body:level.getEntitiesOfClass(LivingEntity.class,new AABB(c,c).inflate(radius+2),CombatRules::target))
                if(!inside.containsKey(body)&&!SafeZones.contains(body)&&inside(body,zone,radius))inside.put(body,zone);
        }
        for(var entry:inside.entrySet()){
            var body=entry.getKey();var zone=entry.getValue();long now=zone.level().getGameTime();
            var exposure=EXPOSED.get(body);
            if(exposure==null){exposure=new Exposure();EXPOSED.put(body,exposure);}
            else if(now<exposure.next)continue;
            hurt(body,zone,(float)config.damage(exposure.hits));
            exposure.hits++;exposure.next=now+ExtraGadgetConfig.ticks(config.intervalSeconds());
        }
        // Out of every cloud when the next combo was due: the combo ends.
        EXPOSED.entrySet().removeIf(e->{
            var body=e.getKey();
            return !inside.containsKey(body)&&(!body.isAlive()||body.isRemoved()||body.level().getGameTime()>=e.getValue().next);
        });
    }
    private static void hurt(LivingEntity body,Zone zone,float damage){
        if(damage<=0)return;
        var level=zone.level();
        var owner=zone.owner()==null?null:level.getServer().getPlayerList().getPlayer(zone.owner());
        Entity attacker=owner!=null?owner:zone.owner()==null?null:level.getEntity(zone.owner());
        var type=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.INDIRECT_MAGIC);
        body.invulnerableTime=0;
        // The cloud's owner keeps the kill credit; fallout hurts teammates too (area damage bypasses friendly-fire
        // protection), and it pushes nobody towards the owner.
        CombatRules.quietly(()->OrdnanceDamage.hurt(body,new DamageSource(type,null,attacker),damage));
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){ZONES.clear();EXPOSED.clear();}
    private Fallout(){}
}
