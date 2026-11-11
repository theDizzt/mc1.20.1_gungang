package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/**
 * A thrown 33 shuriken: straight, gravity-free flight with no damage falloff, one hit, then gone. A ricochet shuriken
 * ({@code hop} 1-3) bounces on from each body it hits to the nearest enemy in sight within the ricochet radius, as a
 * new shuriken homing on that body, until three bodies are hit; each hop deals its own damage (33 / 22 / 11).
 */
public final class ShurikenEntity extends ThrowableItemProjectile implements net.minecraftforge.entity.IEntityAdditionalSpawnData {
    /** Bodies a ricochet chain may hit at most. */
    public static final int MAX_HOPS=3;
    private double damage;
    private int hop,lifeTicks=100;
    private UUID homing;
    private final Set<UUID> hit=new HashSet<>();
    /** Client only: recent positions for the purple glow trail, newest first. */
    public final ArrayDeque<Vec3> trail=new ArrayDeque<>();
    public static final int TRAIL_POINTS=10;
    /**
     * The thrower's "shuriken" effect colours ({@link EffectColors}), sent in the spawn data: Forge's spawn packet
     * does not carry the projectile owner, so the client cannot look them up itself.
     */
    private int[] colors=EffectColors.server(null,"shuriken");
    public int[] colors(){return colors;}
    @Override public void writeSpawnData(net.minecraft.network.FriendlyByteBuf buffer){
        var owner=getOwner();int[] c=EffectColors.server(owner==null?null:owner.getUUID(),"shuriken");
        buffer.writeInt(c[0]);buffer.writeInt(c[1]);
    }
    @Override public void readSpawnData(net.minecraft.network.FriendlyByteBuf buffer){
        int first=buffer.readInt(),second=buffer.readInt();
        colors=new int[]{first&0xFFFFFF,second<0?-1:second&0xFFFFFF};
    }

    public ShurikenEntity(EntityType<? extends ShurikenEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected Item getDefaultItem(){return GadgetItems.SHURIKEN.get();}
    @Override protected float getGravity(){return 0;}
    void configure(double damage,int hop,int lifeTicks){this.damage=damage;this.hop=hop;this.lifeTicks=Math.max(1,lifeTicks);}
    private boolean ally(Entity entity){
        if(!(getOwner() instanceof LivingEntity owner))return false;
        if(entity==owner)return true;
        if(owner instanceof Player shooter&&entity instanceof Player victim&&!shooter.canHarmPlayer(victim))return true;
        return owner.isAlliedTo(entity);
    }
    @Override protected boolean canHitEntity(Entity entity){
        return super.canHitEntity(entity)&&entity instanceof LivingEntity&&!entity.isSpectator()&&!hit.contains(entity.getUUID())&&!ally(entity);
    }
    @Override protected void onHitEntity(EntityHitResult result){
        super.onHitEntity(result);
        if(level().isClientSide)return;
        var target=(LivingEntity)result.getEntity();
        hit.add(target.getUUID());
        if(!SafeZones.contains(target)&&!LobbyManager.respawning(target.getUUID())){
            // Three shuriken can land in the same tick: each one is its own hit.
            target.invulnerableTime=0;
            target.hurt(damageSources().thrown(this,getOwner()),(float)damage);
        }
        level().playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.TRIDENT_HIT,net.minecraft.sounds.SoundSource.PLAYERS,.6f,1.6f);
        if(hop>=1&&hop<MAX_HOPS)ricochet(target);
        discard();
    }
    /** The next body of a ricochet chain: the nearest enemy within the radius of {@code from}, in its sight. */
    private void ricochet(LivingEntity from){
        var c=ExtraGadgetConfig.shuriken;double radius=c.ricochetRadius();
        if(radius<=0)return;
        LivingEntity next=null;double best=radius*radius;
        for(var candidate:level().getEntitiesOfClass(LivingEntity.class,from.getBoundingBox().inflate(radius),e->e!=from&&e.isAlive()&&!e.isSpectator()
                &&CombatRules.target(e)&&!hit.contains(e.getUUID())&&!ally(e)&&!SafeZones.contains(e)&&!LobbyManager.respawning(e.getUUID()))){
            double distance=candidate.distanceToSqr(from);
            if(distance<=best&&from.hasLineOfSight(candidate)){best=distance;next=candidate;}
        }
        if(next==null)return;
        var bounce=GadgetItems.SHURIKEN_PROJECTILE.get().create(level());if(bounce==null)return;
        bounce.setOwner(getOwner());bounce.setItem(getItem().copy());
        bounce.hit.addAll(hit);
        var start=from.getBoundingBox().getCenter();bounce.setPos(start.x,start.y,start.z);
        bounce.configure(c.ricochetDamage(hop+1),hop+1,ExtraGadgetConfig.ticks(c.flightSeconds()));
        bounce.homing=next.getUUID();
        var aim=next.getBoundingBox().getCenter().subtract(start);
        bounce.shoot(aim.x,aim.y,aim.z,(float)c.projectileSpeed(),0);
        level().addFreshEntity(bounce);
    }
    @Override protected void onHitBlock(BlockHitResult result){
        super.onHitBlock(result);
        if(level().isClientSide)return;
        GimmickEntity.hit(level(),result.getBlockPos(),(float)damage);
        level().playSound(null,result.getBlockPos(),net.minecraft.sounds.SoundEvents.TRIDENT_HIT_GROUND,net.minecraft.sounds.SoundSource.PLAYERS,.6f,1.5f);
        discard();
    }
    @Override public void tick(){
        if(!level().isClientSide&&homing!=null&&((net.minecraft.server.level.ServerLevel)level()).getEntity(homing) instanceof LivingEntity target&&target.isAlive()){
            // A ricochet hop steers onto its body so a moving target cannot simply sidestep it.
            double speed=Math.max(.2,getDeltaMovement().length());
            var aim=target.getBoundingBox().getCenter().subtract(position());
            if(aim.lengthSqr()>1e-6){setDeltaMovement(aim.normalize().scale(speed));hasImpulse=true;}
        }
        super.tick();
        if(level().isClientSide){
            trail.addFirst(position());
            while(trail.size()>TRAIL_POINTS)trail.removeLast();
            if(random.nextInt(2)==0){
                level().addParticle(new net.minecraft.core.particles.DustParticleOptions(EffectColors.vector(EffectColors.at(colors,random.nextFloat())),.8f),getX(),getY(),getZ(),0,0,0);
            }
        }else if(tickCount>lifeTicks)discard();
    }
    @Override public boolean isOnFire(){return false;}
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        tag.putDouble("Damage",damage);tag.putInt("Hop",hop);tag.putInt("Life",lifeTicks);
        if(homing!=null)tag.putUUID("Homing",homing);
        var list=new ListTag();for(var id:hit)list.add(NbtUtils.createUUID(id));tag.put("Hit",list);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        damage=tag.getDouble("Damage");hop=tag.getInt("Hop");lifeTicks=Math.max(1,tag.getInt("Life"));
        homing=tag.hasUUID("Homing")?tag.getUUID("Homing"):null;
        hit.clear();for(var id:tag.getList("Hit",Tag.TAG_INT_ARRAY))hit.add(NbtUtils.loadUUID(id));
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
