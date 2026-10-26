package dev.gungang.acoustics.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** A lobbed grenade becomes a stationary, six-second random arrow emitter on impact. */
public final class ArrowGrenadeEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> ACTIVE=SynchedEntityData.defineId(ArrowGrenadeEntity.class,EntityDataSerializers.BOOLEAN);
    private Vec3 anchor=Vec3.ZERO;
    private long expires,nextShot;
    private double damage=16,range=6,speed=1.2;
    private int interval=1;
    public ArrowGrenadeEntity(EntityType<? extends ArrowGrenadeEntity> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(ACTIVE,false);}
    @Override protected Item getDefaultItem(){return GadgetItems.ARROW_GRENADE.get();}
    @Override protected float getGravity(){return .05f;}
    public boolean active(){return entityData.get(ACTIVE);}
    @Override protected void onHitBlock(BlockHitResult hit){
        super.onHitBlock(hit);
        activate(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.2)));
    }
    @Override protected void onHitEntity(EntityHitResult hit){
        activate(hit.getLocation().subtract(getDeltaMovement().normalize().scale(.2)));
    }
    private void activate(Vec3 point){
        if(level().isClientSide||active()||isRemoved())return;
        if(SafeZones.contains(level(),point.x,point.y,point.z)){discard();return;}
        var config=ExtraGadgetConfig.arrowGrenade;
        anchor=point;setPos(point);setDeltaMovement(Vec3.ZERO);setNoGravity(true);
        damage=config.damage();range=config.projectileRange();speed=config.projectileSpeed();interval=config.emissionIntervalTicks();
        long now=level().getGameTime();expires=now+ExtraGadgetConfig.ticks(config.durationSeconds());nextShot=now+interval;
        entityData.set(ACTIVE,true);
        level().playSound(null,blockPosition(),SoundEvents.IRON_TRAPDOOR_OPEN,SoundSource.PLAYERS,.8f,1.5f);
        emit();
    }
    @Override public void tick(){
        if(!active()){
            super.tick();
            // ThrowableProjectile finishes its movement after onHit; restore the actual impact point.
            if(active()){setPos(anchor);setDeltaMovement(Vec3.ZERO);}
            else if(!level().isClientSide&&tickCount>=200)discard();
            return;
        }
        baseTick();setDeltaMovement(Vec3.ZERO);
        if(level().isClientSide){
            if(random.nextInt(4)==0)level().addParticle(ParticleTypes.END_ROD,getX(),getY()+.1,getZ(),0,.015,0);
            return;
        }
        long now=level().getGameTime();
        if(now>=expires||SafeZones.contains(this)){discard();return;}
        if(now>=nextShot){emit();nextShot=now+interval;}
    }
    private void emit(){
        var arrow=GadgetItems.ARROW_FRAGMENT.get().create(level());if(arrow==null)return;
        double y=random.nextDouble()*2-1,angle=random.nextDouble()*Math.PI*2,horizontal=Math.sqrt(1-y*y);
        var direction=new Vec3(Math.cos(angle)*horizontal,y,Math.sin(angle)*horizontal);
        arrow.launch(getOwner(),position(),direction,damage,range,speed);
        if(level().addFreshEntity(arrow)&&level().getGameTime()%4==0)
            level().playSound(null,blockPosition(),SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,.35f,1.5f+random.nextFloat()*.3f);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);tag.putBoolean("Active",active());tag.putLong("Expires",expires);tag.putLong("NextShot",nextShot);
        tag.putDouble("ShardDamage",damage);tag.putDouble("ShardRange",range);tag.putDouble("ShardSpeed",speed);tag.putInt("Interval",interval);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);entityData.set(ACTIVE,tag.getBoolean("Active"));anchor=position();
        expires=tag.getLong("Expires");nextShot=tag.getLong("NextShot");damage=tag.getDouble("ShardDamage");
        range=tag.getDouble("ShardRange");speed=tag.getDouble("ShardSpeed");interval=Math.max(1,tag.getInt("Interval"));
        if(active()){setNoGravity(true);setDeltaMovement(Vec3.ZERO);}
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
