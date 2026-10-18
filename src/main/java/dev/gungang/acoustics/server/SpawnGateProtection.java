package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SpawnGateProtection {
    public static final String BLOCKED="GunGangSpawnGateBlocked";
    /** Geometry-only trace: a shooter's team must never make an attack ray pass through. */
    public static boolean crosses(BlockGetter level,Vec3 from,Vec3 to){
        if(from==null||to==null||!Double.isFinite(from.lengthSqr())||!Double.isFinite(to.lengthSqr()))return false;
        if(level.getBlockState(net.minecraft.core.BlockPos.containing(from)).getBlock() instanceof SpawnGateBlock)return true;
        return BlockGetter.traverseBlocks(from,to,level,(world,pos)->world.getBlockState(pos).getBlock() instanceof SpawnGateBlock?Boolean.TRUE:null,world->Boolean.FALSE);
    }
    public static boolean shields(Entity target,Vec3 origin){
        return crosses(target.level(),origin,target.getBoundingBox().getCenter())||crosses(target.level(),origin,target.getEyePosition());
    }
    public static boolean separates(Entity source,Entity target){return source!=null&&source.level()==target.level()&&shields(target,source.getEyePosition());}
    public static boolean blocked(Entity target,net.minecraft.world.damagesource.DamageSource source){
        if(shields(target,source.getSourcePosition()))return true;
        if(source.getDirectEntity() instanceof Projectile projectile)
            return projectile.getPersistentData().getBoolean(BLOCKED)||shields(target,new Vec3(projectile.xOld,projectile.yOld,projectile.zOld));
        return false;
    }
    public static boolean bulletBlocked(Projectile bullet,Vec3 target){
        return bullet.getPersistentData().getBoolean(BLOCKED)||crosses(bullet.level(),new Vec3(bullet.xOld,bullet.yOld,bullet.zOld),target);
    }
    private static void absorb(Entity projectile){projectile.getPersistentData().putBoolean(BLOCKED,true);projectile.discard();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void impact(net.minecraftforge.event.entity.ProjectileImpactEvent event){
        if(event.getRayTraceResult() instanceof BlockHitResult hit&&event.getEntity().level().getBlockState(hit.getBlockPos()).getBlock() instanceof SpawnGateBlock){
            if(!event.getEntity().level().isClientSide)absorb(event.getEntity());event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(net.minecraftforge.event.entity.living.LivingAttackEvent event){if(blocked(event.getEntity(),event.getSource()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event){if(blocked(event.getEntity(),event.getSource()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void explosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event){
        var origin=event.getExplosion().getPosition();
        event.getAffectedEntities().removeIf(entity->shields(entity,origin));
        event.getAffectedBlocks().removeIf(pos->event.getLevel().getBlockState(pos).getBlock() instanceof SpawnGateBlock||crosses(event.getLevel(),origin,Vec3.atCenterOf(pos)));
    }
    @SuppressWarnings("unchecked") public static void registerTacz(){try{
        var type=(Class<Event>)Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");var world=type.getMethod("getLevel");var hit=type.getMethod("getHitResult");var ammo=type.getMethod("getAmmo");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,type,event->{try{
            var level=(Level)world.invoke(event);var result=(BlockHitResult)hit.invoke(event);
            if(!level.isClientSide&&level.getBlockState(result.getBlockPos()).getBlock() instanceof SpawnGateBlock){absorb((Entity)ammo.invoke(event));if(event.isCancelable())event.setCanceled(true);}
        }catch(ReflectiveOperationException error){com.mojang.logging.LogUtils.getLogger().error("Spawn gate bullet interception failed",error);}});
    }catch(ClassNotFoundException absent){/* Optional gun integration. */}catch(ReflectiveOperationException error){com.mojang.logging.LogUtils.getLogger().warn("Spawn gate TaCZ hook unavailable",error);}}
    private SpawnGateProtection(){}
}
