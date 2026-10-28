package dev.gungang.acoustics.server;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** One hit on a living target; block impacts remain until the throw cooldown ends. */
public final class HandAxeEntity extends ThrowableItemProjectile {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> EMBEDDED=
            net.minecraft.network.syncher.SynchedEntityData.defineId(HandAxeEntity.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private long expiresAt;
    private Vec3 impact;
    private float impactYaw,impactPitch;
    @Override protected void defineSynchedData() {super.defineSynchedData();entityData.define(EMBEDDED,false);}
    public boolean embedded() {return entityData.get(EMBEDDED);}
    public void expireAfter(int ticks) {expiresAt=level().getGameTime()+Math.max(1,ticks);}
    public HandAxeEntity(EntityType<? extends HandAxeEntity> type,Level level) {super(type,level);}
    @Override protected Item getDefaultItem() {return GadgetItems.HAND_AXE.get();}
    @Override protected float getGravity() {return (float)HandAxeRules.GRAVITY;}
    @Override protected boolean canHitEntity(Entity entity) {return super.canHitEntity(entity)&&entity instanceof LivingEntity&&!entity.isSpectator();}
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if(level().isClientSide)return;
        var target=(LivingEntity)hit.getEntity();
        // EntityHitResult may contain the entity center; recover the actual flight contact.
        var contact=target.getBoundingBox().clip(position(),position().add(getDeltaMovement())).orElse(hit.getLocation());
        boolean headshot=HandAxeRules.headshot(contact.y,target.getY(),target.getBbHeight(),target.getEyeY());
        target.hurt(damageSources().thrown(this,getOwner()),(float)HandAxeRules.damage(ExtraGadgetConfig.handAxe.throwDamage(),headshot));
        level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.TRIDENT_HIT,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
        discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if(level().isClientSide)return;
        level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.TRIDENT_HIT_GROUND,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
        impact=hit.getLocation();
        setPos(impact);setDeltaMovement(Vec3.ZERO);setNoGravity(true);
        var normal=hit.getDirection();
        setYRot(normal.getAxis().isVertical()?getYRot():normal.getOpposite().toYRot());
        setXRot(normal==net.minecraft.core.Direction.UP?90:normal==net.minecraft.core.Direction.DOWN?-90:0);
        impactYaw=getYRot();impactPitch=getXRot();
        yRotO=getYRot();xRotO=getXRot();
        entityData.set(EMBEDDED,true);hasImpulse=true;
    }
    @Override public void tick() {
        if(!level().isClientSide&&expiresAt==0)expireAfter(ExtraGadgetConfig.ticks(ExtraGadgetConfig.handAxe.cooldownSeconds()));
        if(embedded()) {baseTick();setDeltaMovement(Vec3.ZERO);}
        else {
            super.tick();
            // ThrowableProjectile finishes moving after invoking onHitBlock.
            if(embedded()&&impact!=null) {
                setPos(impact);setDeltaMovement(Vec3.ZERO);
                setYRot(impactYaw);setXRot(impactPitch);yRotO=impactYaw;xRotO=impactPitch;
            }
        }
        if(!level().isClientSide&&level().getGameTime()>=expiresAt)discard();
    }
    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putBoolean("Embedded",embedded());tag.putLong("ExpiresAt",expiresAt);
    }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);entityData.set(EMBEDDED,tag.getBoolean("Embedded"));expiresAt=tag.getLong("ExpiresAt");
        if(embedded()) {impact=position();setNoGravity(true);setDeltaMovement(Vec3.ZERO);}
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
