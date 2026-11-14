package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A lobbed grenade emits simultaneous spherical arrow bursts on impact (every emissionIntervalTicks).
 * <p>The 34 arrows of a burst are not entities. Each used to be a tracked arrow sending a spawn, a position every tick
 * and a removal to every player in range (some 340 packets per player and burst, hundreds of entities per grenade).
 * They fly here as virtual shards with the same speed, range and hit rules: traced against blocks once per burst and
 * against bodies every tick with one shared entity query. Clients draw them from one
 * {@link GungangNetwork.ArrowBurst} packet per burst ({@code client/ArrowBursts}).
 */
public final class ArrowGrenadeEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> ACTIVE=SynchedEntityData.defineId(ArrowGrenadeEntity.class,EntityDataSerializers.BOOLEAN);
    /** Hit tolerance around bodies, as the arrow fragments had. */
    private static final double HIT_MARGIN=.45;
    /** The burst directions, shared with the client: a horizontal ring of 16, rings of 8 at ±45° and both poles. */
    public static final List<Vec3> DIRECTIONS=directions();
    private Vec3 anchor=Vec3.ZERO;
    private long expires,nextShot;
    private double damage=16,range=6,speed=1.2;
    private int interval=20;
    /** A shard in flight: its direction, how far it has flown, and where the blocks stop it. */
    private static final class Shard {
        final Vec3 direction;final double stop;double flown;boolean first=true;
        Shard(Vec3 direction,double stop){this.direction=direction;this.stop=stop;}
    }
    private final List<Shard> shards=new ArrayList<>();

    public ArrowGrenadeEntity(EntityType<? extends ArrowGrenadeEntity> type,Level level){super(type,level);}
    private static List<Vec3> directions(){
        var list=new ArrayList<Vec3>();
        for(int ring=-1;ring<=1;ring++){
            int count=ring==0?16:8;double pitch=ring*Math.PI/4,horizontal=Math.cos(pitch);
            for(int i=0;i<count;i++){double angle=2*Math.PI*i/count;list.add(new Vec3(Math.cos(angle)*horizontal,Math.sin(pitch),Math.sin(angle)*horizontal));}
        }
        list.add(new Vec3(0,1,0));list.add(new Vec3(0,-1,0));
        return List.copyOf(list);
    }
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
        fly();
        if(now>=expires||SafeZones.contains(this)){discard();return;}
        if(now>=nextShot){emit();nextShot=now+interval;}
    }
    private void emit(){
        // Blocks do not move during a burst: each shard's block stop is traced once.
        var origin=position();var stops=new float[DIRECTIONS.size()];
        for(int i=0;i<stops.length;i++){
            var direction=DIRECTIONS.get(i);
            var clip=level().clip(new ClipContext(origin,origin.add(direction.scale(range)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            double stop=clip.getType()==HitResult.Type.MISS?range:clip.getLocation().distanceTo(origin);
            shards.add(new Shard(direction,stop));stops[i]=(float)stop;
        }
        GungangNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(()->this),
                new GungangNetwork.ArrowBurst(origin.x,origin.y,origin.z,(float)speed,stops));
        level().playSound(null,blockPosition(),SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,.35f,1.5f+random.nextFloat()*.3f);
    }
    /** One tick of every shard in flight (the fragments' old step): speed blocks, hitting the first body on the way. */
    private void fly(){
        if(shards.isEmpty())return;
        var level=(ServerLevel)level();var origin=position();var owner=getOwner();
        var bodies=level.getEntities(this,getBoundingBox().inflate(range+HIT_MARGIN+1),e->e.isAlive()&&!e.isSpectator()&&e.canBeHitByProjectile()
                &&!(owner instanceof net.minecraft.world.entity.player.Player shooter&&e!=owner&&e instanceof net.minecraft.world.entity.player.Player player&&!shooter.canHarmPlayer(player)));
        for(var it=shards.iterator();it.hasNext();){
            var shard=it.next();
            var from=origin.add(shard.direction.scale(shard.flown));
            double next=Math.min(shard.stop,shard.flown+speed);var to=origin.add(shard.direction.scale(next));
            Entity hit=null;double best=Double.MAX_VALUE;
            for(var body:bodies){
                if(body.isRemoved()||!body.isAlive())continue;
                var box=body.getBoundingBox().inflate(HIT_MARGIN);
                // Born inside a body: no entry face to trace, so the overlap counts (as it did for the fragments).
                if(shard.first&&(body instanceof EscortCart cart?cart.modelContains(from):box.contains(from))){hit=body;break;}
                var clip=box.clip(from,to);
                if(clip.isPresent()){double d=clip.get().distanceToSqr(from);if(d<best){best=d;hit=body;}}
            }
            shard.first=false;
            if(hit!=null){strike(hit);it.remove();continue;}
            shard.flown=next;
            if(shard.flown>=shard.stop-1e-6){
                if(shard.stop<range-1e-6)gimmick(level,origin.add(shard.direction.scale(shard.stop+.05)));
                it.remove();
            }
        }
    }
    /** A shard's hit: its full damage, no hurt cooldown checked or left behind (as the arrow fragments). */
    private void strike(Entity target){
        var source=new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ArrowFragmentEntity.DAMAGE),this,getOwner());
        int immunity=target.invulnerableTime;target.invulnerableTime=0;
        try{
            if(target==getOwner()&&target instanceof LivingEntity living)OrdnanceDamage.hurt(living,source,(float)damage);
            else target.hurt(source,(float)damage);
        }finally{target.invulnerableTime=immunity;}
    }
    /** A shard stuck in a gimmick (cracked wall, gas barrel ...) chips it, as an arrow did. */
    private void gimmick(ServerLevel level,Vec3 at){
        var pos=BlockPos.containing(at);
        if(level.getBlockState(pos).getBlock() instanceof GimmickBlock)GimmickEntity.hit(level,pos,(float)Math.max(1,2*speed));
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
