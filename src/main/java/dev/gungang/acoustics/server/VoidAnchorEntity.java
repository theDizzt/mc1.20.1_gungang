package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** A thrown, destructible spear. Its lifetime belongs to the spear, not its thrower's life. */
public final class VoidAnchorEntity extends LivingEntity {
    private static final EntityDataAccessor<Boolean> PLANTED=SynchedEntityData.defineId(VoidAnchorEntity.class,EntityDataSerializers.BOOLEAN);
    private static final String TETHER="VoidAnchorTether";
    private UUID owner,victim;
    private String team="";
    private Vec3 flight=Vec3.ZERO;
    private static final double PULL_SPEED=1.0/4; // One block per four ticks, applied smoothly every tick.
    private double distance;
    private long expires;
    private boolean victimGravity;
    private LivingEntity pulled;
    public VoidAnchorEntity(EntityType<? extends LivingEntity> type,Level level) {super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData() {super.defineSynchedData();entityData.define(PLANTED,false);}
    public boolean planted() {return entityData.get(PLANTED);}
    public void launch(ServerPlayer p) {
        owner=p.getUUID();team=p.getTeam()==null?"":p.getTeam().getName();
        setPos(p.getEyePosition().add(p.getLookAngle().scale(.4)));flight=VoidAnchorFlight.launch(p.getLookAngle());
        setYRot(p.getYRot());setXRot(p.getXRot());setHealth(60);
    }
    @Override public void travel(Vec3 input) { }
    @Override public void move(MoverType type,Vec3 movement) { }
    @Override public boolean isInWall() {return false;}
    @Override public void tick() {
        super.tick();setDeltaMovement(Vec3.ZERO);
        if(!(level() instanceof ServerLevel level)||isRemoved())return;
        if(!isAlive()) {release();discard();return;}
        if(!planted()) {
            // Ballistic arc with a hard horizontal range limit followed by mandatory descent.
            Vec3 step=VoidAnchorFlight.step(flight,distance);
            if(step.lengthSqr()>1e-8) {
                setYRot((float)Math.toDegrees(Math.atan2(-step.x,step.z)));
                setXRot((float)Math.toDegrees(Math.atan2(-step.y,step.horizontalDistance())));
            }
            var hit=level.clip(new ClipContext(position(),position().add(step),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(hit.getType()==HitResult.Type.BLOCK) {
                setPos(PlacementSurface.anchorImpact(level,hit));
                entityData.set(PLANTED,true);expires=level.getGameTime()+120;setXRot(0);xRotO=0;
                level.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.TRIDENT_HIT_GROUND,net.minecraft.sounds.SoundSource.PLAYERS,1,.7f);
                acquire(level);
            } else {
                setPos(position().add(step));distance+=step.horizontalDistance();
                flight=VoidAnchorFlight.next(flight,distance);
                // Reaching 40 blocks stops forward travel, not the projectile: it falls until it hits terrain.
                if(getY()<level.getMinBuildHeight()-64||tickCount>400)discard();
            }
            return;
        }
        if(SafeZones.contains(this)) {discard();return;}
        if(level.getGameTime()>=expires) {explode(1);return;}
        if(victim==null) {acquire(level);return;}
        var entity=level.getEntity(victim);
        if(!(entity instanceof LivingEntity target)||!enemy(target)||target.isPassenger()) {release();return;}
        pulled=target;
        Vec3 offset=position().subtract(target.position());
        if(getBoundingBox().intersects(target.getBoundingBox())) {explode(2.5);return;}
        Vec3 motion=offset.length()>PULL_SPEED?offset.normalize().scale(PULL_SPEED):offset;
        target.setNoGravity(true);target.fallDistance=0;
        if(target instanceof ServerPlayer p) {
            PlayerImpulse.apply(p,motion);
            GungangNetwork.send(p,new GungangNetwork.AnchorPull(getUUID(),true,motion.x,motion.y,motion.z));
        } else {target.setDeltaMovement(Vec3.ZERO);target.move(MoverType.SELF,motion);target.setDeltaMovement(Vec3.ZERO);}
        if(tickCount%2==0) {
            Vec3 a=position().add(0,1,0),b=target.getBoundingBox().getCenter();
            int steps=Math.max(2,(int)(a.distanceTo(b)*5));
            var dust=new DustParticleOptions(new org.joml.Vector3f(.025f,.015f,.04f),1.15f);
            for(int i=0;i<=steps;i++) {var at=a.lerp(b,i/(double)steps);level.sendParticles(dust,at.x,at.y,at.z,1,.015,.015,.015,0);}
        }
    }
    private boolean enemy(LivingEntity e) {
        if(e==this||!e.isAlive()||e.isSpectator()||e.getUUID().equals(owner)||SafeZones.contains(e)||LobbyManager.respawning(e.getUUID())||FreeForAllMatch.blocked(e))return false;
        if(!(e instanceof Player||e instanceof BotEntity||e instanceof net.minecraft.world.entity.monster.Enemy))return false;
        if(e instanceof Player p&&p.isCreative())return false;
        return team.isEmpty()||e.getTeam()==null||!team.equals(e.getTeam().getName());
    }
    private void acquire(ServerLevel level) {
        var nearby=level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(10),e->enemy(e)&&targetDistance(e)<=100);
        // An enemy that cannot currently be pulled is not the same as an empty area.
        if(nearby.isEmpty()) {explode(1);return;}
        var target=nearby.stream().filter(e->!e.isPassenger()&&!tethered(level,e))
                .min(Comparator.comparingDouble(this::targetDistance)).orElse(null);
        if(target==null)return;
        if(target instanceof ServerPlayer p) {AirAssault.clear(p);SurvivalGadgets.stopJet(p);}
        victim=target.getUUID();pulled=target;victimGravity=target.isNoGravity();
        target.getPersistentData().putUUID(TETHER,getUUID());

    }
    private double targetDistance(LivingEntity target) {
        // Measure to the body, not just its feet: slabs/steps must not push a nearby target outside the radius.
        Vec3 center=getBoundingBox().getCenter();var box=target.getBoundingBox();
        return center.distanceToSqr(Math.max(box.minX,Math.min(box.maxX,center.x)),
                Math.max(box.minY,Math.min(box.maxY,center.y)),Math.max(box.minZ,Math.min(box.maxZ,center.z)));
    }
    private boolean tethered(ServerLevel level,LivingEntity target) {
        var tag=target.getPersistentData();if(!tag.hasUUID(TETHER))return false;
        var entity=level.getEntity(tag.getUUID(TETHER));
        if(entity instanceof VoidAnchorEntity anchor&&anchor.isAlive()&&!anchor.isRemoved()
                &&target.getUUID().equals(anchor.victim)&&level.getGameTime()<anchor.expires)return true;
        tag.remove(TETHER);return false;
    }
    private void release() {
        LivingEntity target=pulled;
        if(target==null&&victim!=null&&level() instanceof ServerLevel level&&level.getEntity(victim) instanceof LivingEntity e)target=e;
        if(target!=null&&target.getPersistentData().hasUUID(TETHER)&&target.getPersistentData().getUUID(TETHER).equals(getUUID())) {
            target.getPersistentData().remove(TETHER);target.setNoGravity(victimGravity);target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;
            if(target instanceof ServerPlayer p) {VoidAnchorPull.receive(p,getUUID(),false,Vec3.ZERO);PlayerImpulse.sync(p);GungangNetwork.send(p,new GungangNetwork.AnchorPull(getUUID(),false,0,0,0));}
        }
        victim=null;pulled=null;
    }
    private void explode(double half) {
        if(!(level() instanceof ServerLevel level))return;
        release();
        var ownerEntity=owner==null?null:level.getEntity(owner);
        var source=damageSources().explosion(this,ownerEntity);
        var area=new AABB(getX()-half,getY()-half,getZ()-half,getX()+half,getY()+half,getZ()+half);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,area,this::enemy))target.hurt(source,40);
        level.sendParticles(ParticleTypes.EXPLOSION,getX(),getY()+.5,getZ(),1,0,0,0,0);
        level.sendParticles(ParticleTypes.SMOKE,getX(),getY()+.5,getZ(),36,half*.5,half*.5,half*.5,.06);
        level.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.PLAYERS,1,.65f);
        discard();
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide||!planted()||isRemoved())return false;
        invulnerableTime=0;
        return super.hurt(source,amount);
    }
    @Override public void die(DamageSource source) {release();super.die(source);discard();}
    @Override public void remove(RemovalReason reason) {if(!level().isClientSide)release();super.remove(reason);}
    @Override public boolean isPickable() {return planted()&&isAlive();}
    @Override public boolean isPushable() {return false;}
    @Override public void knockback(double strength,double x,double z) { }
    @Override public void push(double x,double y,double z) { }
    @Override protected void pushEntities() { }
    @Override public Iterable<ItemStack> getArmorSlots() {return List.of();}
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) {return ItemStack.EMPTY;}
    @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack) { }
    @Override public HumanoidArm getMainArm() {return HumanoidArm.RIGHT;}
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);if(owner!=null)tag.putUUID("Owner",owner);tag.putString("Team",team);
        tag.putBoolean("Planted",planted());tag.putLong("Expires",expires);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;team=tag.getString("Team");
        entityData.set(PLANTED,tag.getBoolean("Planted"));expires=tag.getLong("Expires");
        // Unloaded projectiles fall vertically; planted ones keep their original deadline.
        if(!planted())distance=40;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
