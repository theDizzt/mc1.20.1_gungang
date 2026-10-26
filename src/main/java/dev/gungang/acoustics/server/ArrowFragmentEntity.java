package dev.gungang.acoustics.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Real arrow collision and visuals, fixed per-hit damage, no gravity, pickup or travel beyond its range. */
public final class ArrowFragmentEntity extends AbstractArrow {
    private static final float HIT_MARGIN=.45f;
    private static final net.minecraft.resources.ResourceKey<DamageType> DAMAGE=net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DAMAGE_TYPE,new net.minecraft.resources.ResourceLocation("gungang","arrow_fragment"));
    private double distanceLimit=6,travelled,damage=16;
    public ArrowFragmentEntity(EntityType<? extends ArrowFragmentEntity> type,Level level){super(type,level);setNoGravity(true);pickup=Pickup.DISALLOWED;}
    public void launch(Entity owner,Vec3 origin,Vec3 direction,double damage,double range,double speed){
        setOwner(owner);setPos(origin);this.damage=damage;distanceLimit=range;travelled=0;
        shoot(direction.x,direction.y,direction.z,(float)speed,0);setCritArrow(false);
    }
    @Override public void tick(){
        if(level().isClientSide){super.tick();return;}
        double left=distanceLimit-travelled;
        if(left<=1e-6||tickCount>=40){discard();return;}
        // Vanilla arrow tracing only finds a box's entry face. A fragment emitted
        // inside a body has no entry face, so resolve that overlap before moving.
        // These are detached grenade fragments, not arrows leaving a shooter's bow.
        var owner=getOwner();
        for(var target:level().getEntities(this,getBoundingBox().inflate(HIT_MARGIN),entity->entity.canBeHitByProjectile()&&entity.getBoundingBox().inflate(HIT_MARGIN).contains(position()))){
            if(target!=owner&&owner instanceof net.minecraft.world.entity.player.Player shooter
                    &&target instanceof net.minecraft.world.entity.player.Player player&&!shooter.canHarmPlayer(player))continue;
            var hit=new EntityHitResult(target,position());
            if(!net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this,hit))onHit(hit);
            if(isRemoved())return;
        }
        Vec3 velocity=getDeltaMovement();
        if(velocity.length()>left)setDeltaMovement(velocity.normalize().scale(left));
        Vec3 before=position();super.tick();travelled+=position().distanceTo(before);
        if(!level().isClientSide&&(travelled>=distanceLimit-1e-6||inGround))discard();
    }
    @Override protected EntityHitResult findHitEntity(Vec3 from,Vec3 to){
        // AbstractArrow already clips 'to' against blocks before asking for entity hits.
        return net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level(),this,from,to,
                getBoundingBox().expandTowards(to.subtract(from)).inflate(1),this::canHitEntity,HIT_MARGIN);
    }
    @Override protected void onHitEntity(EntityHitResult hit){
        if(level().isClientSide)return;
        var source=new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE),this,getOwner());
        if(hit.getEntity()==getOwner()&&hit.getEntity() instanceof LivingEntity living)
            OrdnanceDamage.hurt(living,source,(float)damage);
        else hit.getEntity().hurt(source,(float)damage);
        discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit){
        super.onHitBlock(hit);if(!level().isClientSide)discard();
    }
    @Override protected ItemStack getPickupItem(){return ItemStack.EMPTY;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putDouble("ShardRange",distanceLimit);tag.putDouble("Travelled",travelled);tag.putDouble("ShardDamage",damage);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);distanceLimit=tag.getDouble("ShardRange");travelled=tag.getDouble("Travelled");damage=tag.getDouble("ShardDamage");setNoGravity(true);pickup=Pickup.DISALLOWED;}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
