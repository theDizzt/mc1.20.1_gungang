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
    // Visual-only state for the client: fuse length for the beam ramp, and the detonation edge for shake/flash.
    private static final EntityDataAccessor<Integer> FUSE=SynchedEntityData.defineId(OrdnanceEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DETONATED=SynchedEntityData.defineId(OrdnanceEntity.class,EntityDataSerializers.BOOLEAN);
    /** Ticks the smoke column and embers linger after the blast; purely cosmetic. */
    public static final int AFTERMATH=120;
    private UUID owner;
    private long expires;
    private int detonatedAt=-1;
    public OrdnanceEntity(EntityType<? extends OrdnanceEntity> type,Level level) {super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData() {entityData.define(ORBITAL,false);entityData.define(FUSE,100);entityData.define(DETONATED,false);}
    public boolean orbital() {return entityData.get(ORBITAL);}
    public int fuse() {return entityData.get(FUSE);}
    public boolean detonated() {return entityData.get(DETONATED);}
    /** Client tick count at detonation, or -1. */
    public int detonatedAt() {return detonatedAt;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(!DETONATED.equals(key)||!level().isClientSide||!detonated()||detonatedAt>=0)return;
        detonatedAt=tickCount;
        // Skip shake/flash when the entity only streamed in during the aftermath.
        if(tickCount>0)net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->dev.gungang.acoustics.client.OrbitalImpactFx.trigger(position()));
    }
    public void setup(Entity owner,boolean orbital) {
        this.owner=owner==null?null:owner.getUUID();entityData.set(ORBITAL,orbital);
        int ticks=orbital?ExtraGadgetConfig.ticks(ExtraGadgetConfig.orbital.delaySeconds()):ExtraGadgetConfig.ticks(ExtraGadgetConfig.molotov.durationSeconds());
        expires=level().getGameTime()+ticks;entityData.set(FUSE,Math.max(1,ticks));
    }
    @Override public void tick() {
        super.tick();
        if(!(level() instanceof ServerLevel level))return;
        long now=level.getGameTime();
        if(orbital()&&detonated()) {
            if(now-expires>=AFTERMATH){discard();return;}
            aftermath(level,(int)(now-expires));return;
        }
        if(now>=expires) {
            // Do not deliver a stale surprise strike after an unloaded chunk returns.
            if(orbital()&&now<=expires+20){expires=now;explode(level);return;}
            discard();return;
        }
        if(orbital()) {
            long remaining=expires-now;
            // The warning ring pulses faster and the sky starts raining light in the final two seconds.
            if(tickCount%(remaining<=40?2:5)==0)for(int i=0;i<96;i++) {
                double angle=i*Math.PI*2/96;
                level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1,.05f,.8f),remaining<=40?2.2f:1.5f),
                        getX()+Math.cos(angle)*ExtraGadgetConfig.orbital.radius(),getY()+.15,getZ()+Math.sin(angle)*ExtraGadgetConfig.orbital.radius(),1,0,.08,0,0);
            }
            if(remaining<=40) {
                for(int i=0;i<3;i++)level.sendParticles(ParticleTypes.END_ROD,getX()+random.nextGaussian()*1.5,getY()+18+random.nextDouble()*20,getZ()+random.nextGaussian()*1.5,0,0,-1,0,2.5);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,getX(),getY()+.5,getZ(),12,2.5,.2,2.5,.05);
            }
            if(remaining==40)level.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE,net.minecraft.sounds.SoundSource.PLAYERS,6,.55f);
            if(remaining==12)level.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.WITHER_SHOOT,net.minecraft.sounds.SoundSource.PLAYERS,6,.5f);
            return;
        }
        if(tickCount%4==0) {
            for(int i=0;i<65;i++) {
                double angle=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble())*ExtraGadgetConfig.molotov.radius();
                level.sendParticles(ParticleTypes.FLAME,getX()+Math.cos(angle)*r,getY()+.08,getZ()+Math.sin(angle)*r,1,0,.08,0,.015);
            }
        }
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,new AABB(getX()-ExtraGadgetConfig.molotov.radius(),getY()-1,getZ()-ExtraGadgetConfig.molotov.radius(),getX()+ExtraGadgetConfig.molotov.radius(),getY()+3,getZ()+ExtraGadgetConfig.molotov.radius()),e->e.isAlive()&&!e.isSpectator())) {
            if(!OrdnanceRules.inFire(victim.getX()-getX(),victim.getY()-getY(),victim.getZ()-getZ())||victim.fireImmune()||victim.isInWaterRainOrBubble()||SafeZones.contains(victim))continue;
            if(SpawnGateProtection.shields(victim,position()))continue;
            victim.setSecondsOnFire(ExtraGadgetConfig.molotov.burnSeconds());
            AdrenalineDamage.burning(victim,owner,ExtraGadgetConfig.molotov.burnSeconds(),ExtraGadgetConfig.molotov.burnDamage());
        }
    }
    private void explode(ServerLevel level) {
        var source=level.damageSources().explosion(this,owner==null?null:level.getEntity(owner));
        double radius=ExtraGadgetConfig.orbital.radius();
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius),e->e.isAlive()&&!e.isSpectator()))
            if(OrdnanceRules.inBlast(victim.getX()-getX(),victim.getY()-getY(),victim.getZ()-getZ()))
                OrdnanceDamage.hurt(victim,source,OrdnanceRules.orbitalDamage(victim.position().distanceTo(position())));
        entityData.set(DETONATED,true);
        // Layered impact: deep blast, thunder crack and a sonic boom tail. Volume above 1 widens the hearing range.
        var pos=blockPosition();var players=net.minecraft.sounds.SoundSource.PLAYERS;
        level.playSound(null,pos,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,players,10,.5f);
        level.playSound(null,pos,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,players,10,.8f);
        level.playSound(null,pos,net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,players,10,.6f);
        level.playSound(null,pos,net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,players,8,.5f);
        level.playSound(null,pos,net.minecraft.sounds.SoundEvents.DRAGON_FIREBALL_EXPLODE,players,8,.6f);
        double x=getX(),y=getY(),z=getZ();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,x,y+1,z,40,radius*.5,3,radius*.5,0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,x,y+1,z,6,1.5,4,1.5,0);
        for(int i=0;i<5;i++)level.sendParticles(ParticleTypes.FLASH,x+random.nextGaussian()*3,y+1+i*2,z+random.nextGaussian()*3,1,0,0,0,0);
        // Fireball column and flying embers.
        for(int h=0;h<14;h+=2)level.sendParticles(ParticleTypes.EXPLOSION,x,y+2+h,z,6,1.2+h*.15,.8,1.2+h*.15,0);
        level.sendParticles(ParticleTypes.FLAME,x,y+1,z,180,radius*.35,1.5,radius*.35,.25);
        level.sendParticles(ParticleTypes.LAVA,x,y+1,z,90,radius*.3,.5,radius*.3,0);
        // Cosmetic lightning only: no damage, no fire.
        for(int i=0;i<3;i++) {
            var bolt=EntityType.LIGHTNING_BOLT.create(level);
            if(bolt==null)break;
            double angle=random.nextDouble()*Math.PI*2,r=i==0?0:2+random.nextDouble()*radius*.4;
            bolt.moveTo(x+Math.cos(angle)*r,y,z+Math.sin(angle)*r);bolt.setVisualOnly(true);level.addFreshEntity(bolt);
        }
        aftermath(level,0);
    }
    /** Expanding ground shockwave, then a rising smoke column with drifting embers that thins out. */
    private void aftermath(ServerLevel level,int age) {
        double x=getX(),y=getY(),z=getZ(),radius=ExtraGadgetConfig.orbital.radius();
        if(age<=10) {
            double ring=radius*(age+1)/11.0;
            for(int i=0;i<72;i++) {
                double angle=i*Math.PI*2/72;
                level.sendParticles(ParticleTypes.CLOUD,x+Math.cos(angle)*ring,y+.3,z+Math.sin(angle)*ring,0,Math.cos(angle),.05,Math.sin(angle),.6);
                if(i%3==0)level.sendParticles(ParticleTypes.LARGE_SMOKE,x+Math.cos(angle)*ring,y+.5,z+Math.sin(angle)*ring,1,.3,.2,.3,.02);
            }
        }
        float fade=1-age/(float)AFTERMATH;
        if(age%2==0)level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,x,y+1,z,Math.max(1,Math.round(10*fade)),2.5,1,2.5,.02);
        if(age%3==0)level.sendParticles(ParticleTypes.LARGE_SMOKE,x,y+2,z,Math.max(1,Math.round(24*fade)),radius*.3,1.5,radius*.3,.03);
        if(age%4==0&&age<AFTERMATH*2/3) {
            level.sendParticles(ParticleTypes.FLAME,x,y+.2,z,Math.round(40*fade),radius*.35,.1,radius*.35,.01);
            level.sendParticles(ParticleTypes.LAVA,x,y+.5,z,Math.round(6*fade),radius*.25,.2,radius*.25,0);
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {tag.putBoolean("Orbital",orbital());tag.putBoolean("Detonated",detonated());tag.putInt("Fuse",fuse());tag.putLong("Expires",expires);if(owner!=null)tag.putUUID("Owner",owner);}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {entityData.set(ORBITAL,tag.getBoolean("Orbital"));entityData.set(DETONATED,tag.getBoolean("Detonated"));if(tag.contains("Fuse"))entityData.set(FUSE,tag.getInt("Fuse"));expires=tag.getLong("Expires");owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
