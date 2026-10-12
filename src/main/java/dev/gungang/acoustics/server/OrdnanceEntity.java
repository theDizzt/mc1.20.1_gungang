package dev.gungang.acoustics.server;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** A committed strike survives owner movement, equipment changes, and death. */
public final class OrdnanceEntity extends Entity {
    private static final EntityDataAccessor<Boolean> ORBITAL=SynchedEntityData.defineId(OrdnanceEntity.class,EntityDataSerializers.BOOLEAN);
    private UUID owner;
    private long expires;
    public OrdnanceEntity(EntityType<? extends OrdnanceEntity> type,Level level) {super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData() {entityData.define(ORBITAL,false);}
    public boolean orbital() {return entityData.get(ORBITAL);}
    public void setup(Entity owner,boolean orbital) {
        this.owner=owner==null?null:owner.getUUID();entityData.set(ORBITAL,orbital);
        expires=level().getGameTime()+(orbital?OrdnanceRules.ORBITAL_DELAY:OrdnanceRules.FIRE_DURATION);
    }
    @Override public void tick() {
        super.tick();
        if(!(level() instanceof ServerLevel level))return;
        long now=level.getGameTime();
        if(now>=expires) {
            // Do not deliver a stale surprise strike after an unloaded chunk returns.
            if(orbital()&&now<=expires+20)explode(level);
            discard();return;
        }
        if(orbital()) {
            if(tickCount%5==0)for(int i=0;i<96;i++) {
                double angle=i*Math.PI*2/96;
                level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1,.05f,.8f),1.5f),
                        getX()+Math.cos(angle)*OrdnanceRules.ORBITAL_RADIUS,getY()+.15,getZ()+Math.sin(angle)*OrdnanceRules.ORBITAL_RADIUS,1,0,.08,0,0);
            }
            return;
        }
        if(tickCount%4==0) {
            for(int i=0;i<65;i++) {
                double angle=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble())*OrdnanceRules.FIRE_RADIUS;
                level.sendParticles(ParticleTypes.FLAME,getX()+Math.cos(angle)*r,getY()+.08,getZ()+Math.sin(angle)*r,1,0,.08,0,.015);
            }
        }
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,new AABB(getX()-5,getY()-1,getZ()-5,getX()+5,getY()+3,getZ()+5),e->e.isAlive()&&!e.isSpectator())) {
            if(!OrdnanceRules.inFire(victim.getX()-getX(),victim.getY()-getY(),victim.getZ()-getZ())||victim.fireImmune()||victim.isInWaterRainOrBubble()||SafeZones.contains(victim))continue;
            victim.setSecondsOnFire(OrdnanceRules.BURN_SECONDS);
        }
    }
    private void explode(ServerLevel level) {
        var source=level.damageSources().explosion(this,owner==null?null:level.getEntity(owner));
        double radius=OrdnanceRules.ORBITAL_RADIUS;
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius),e->e.isAlive()&&!e.isSpectator()))
            if(OrdnanceRules.inBlast(victim.getX()-getX(),victim.getY()-getY(),victim.getZ()-getZ()))
                OrdnanceDamage.hurt(victim,source,OrdnanceRules.orbitalDamage(victim.position().distanceTo(position())));
        level.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.PLAYERS,8,.65f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,getX(),getY()+1,getZ(),28,12,3,12,0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,getX(),getY()+1,getZ(),1,0,0,0,0);
        level.sendParticles(ParticleTypes.FLASH,getX(),getY()+1,getZ(),1,0,0,0,0);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {tag.putBoolean("Orbital",orbital());tag.putLong("Expires",expires);if(owner!=null)tag.putUUID("Owner",owner);}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {entityData.set(ORBITAL,tag.getBoolean("Orbital"));expires=tag.getLong("Expires");owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
