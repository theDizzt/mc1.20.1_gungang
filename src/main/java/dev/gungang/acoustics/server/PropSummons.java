package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.*;

/** Tagged, epoch-bound non-mob ability actors. Their only combat targets are active seekers. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class PropSummons {
    public static final String TAG="gungang_prop_summon";
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> DAMAGE=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,new net.minecraft.resources.ResourceLocation("gungang","prop_ability"));
    public static PropSummonEntity spawn(ServerLevel level,PropSummonEntity.Kind kind,UUID owner,Vec3 at,float health,float damage,boolean invulnerable){
        var mob=PropSummonEntity.TYPE.get().create(level);if(mob==null)return null;mob.kind(kind);
        mob.addTag(TAG);mob.getPersistentData().putUUID("PropOwner",owner);mob.getPersistentData().putLong("PropEpoch",GimmickEpoch.get(level).generation);
        mob.getPersistentData().putFloat("PropDamage",damage);mob.getPersistentData().putInt("PropAttack",0);
        mob.setMaxUpStep(1);mob.setInvulnerable(invulnerable);
        mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);mob.setHealth(health);
        mob.setPos(at);if(!level.addFreshEntity(mob))return null;return mob;
    }
    public static void clear(MinecraftServer server){if(server==null)return;for(var level:server.getAllLevels()){var list=new ArrayList<Entity>();level.getAllEntities().forEach(e->{if(e.getTags().contains(TAG))list.add(e);});list.forEach(Entity::discard);}}
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(!e.getLevel().isClientSide&&e.loadedFromDisk()&&e.getEntity().getTags().contains(TAG))e.setCanceled(true); // Saved summons never survive a restart/chunk reload.
    }
    @SubscribeEvent public static void drops(net.minecraftforge.event.entity.living.LivingDropsEvent e){if(e.getEntity().getTags().contains(TAG))e.setCanceled(true);}
    @SubscribeEvent public static void experience(net.minecraftforge.event.entity.living.LivingExperienceDropEvent e){if(e.getEntity().getTags().contains(TAG))e.setDroppedExperience(0);}
    public static void tick(PropHuntMatch match,PropSummonEntity mob,Vec3 orbit,int index){
        if(mob==null||mob.isRemoved())return;
        if(!mob.isAlive()){mob.discard();return;}
        boolean bee=mob.kind()==PropSummonEntity.Kind.BEE;
        boolean hiding=match.hiding();
        if((!match.hunting()&&!hiding)||GimmickEpoch.get((ServerLevel)mob.level()).generation!=mob.getPersistentData().getLong("PropEpoch")){mob.discard();return;}
        var target=match.hunting()?match.seekers().stream().filter(p->p.level()==mob.level()&&p.distanceToSqr(mob)<48*48&&(!bee||p.distanceToSqr(orbit)<=16*16)).min(Comparator.comparingDouble(p->p.distanceToSqr(mob))).orElse(null):null;
        if(bee&&target==null&&mob.distanceToSqr(orbit)>12*12)mob.setPos(orbit.add(Math.cos(index*2.1)*1.8,1.2,Math.sin(index*2.1)*1.8));
        Vec3 goal=target==null?orbit:target.position().add(0,bee?1:0,0);
        if(bee&&target==null)goal=orbit.add(Math.cos(match.now()*.05+index*2.1)*1.8,1.2+Math.sin(match.now()*.07+index)*.3,Math.sin(match.now()*.05+index*2.1)*1.8);
        var delta=goal.subtract(mob.position());double speed=bee?.18:.17;
        Vec3 motion=delta.lengthSqr()<.04?Vec3.ZERO:delta.normalize().scale(speed);
        if(!bee)motion=new Vec3(motion.x,mob.horizontalCollision?.3:-.12,motion.z);
        mob.setDeltaMovement(motion);mob.move(MoverType.SELF,motion);mob.setYRot((float)(Math.atan2(-motion.x,motion.z)*180/Math.PI));mob.setYHeadRot(mob.getYRot());
        int now=mob.getServer().getTickCount();
        if(target!=null&&mob.distanceToSqr(target)<2.4&&now>=mob.getPersistentData().getInt("PropAttack")){
            mob.getPersistentData().putInt("PropAttack",now+20);
            var source=new net.minecraft.world.damagesource.DamageSource(mob.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE),mob);
            if(target.hurt(source,mob.getPersistentData().getFloat("PropDamage"))&&mob.kind()==PropSummonEntity.Kind.SPIDER)
                target.addEffect(new MobEffectInstance(MobEffects.POISON,100,0));
        }
    }
    private PropSummons(){}
}
