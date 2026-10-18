package dev.gungang.acoustics.server;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.*;

public final class EscortCart extends Entity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> TRAVEL=net.minecraft.network.syncher.SynchedEntityData.defineId(EscortCart.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    public EscortCart(EntityType<? extends EscortCart> type,Level level){super(type,level);noPhysics=true;setInvulnerable(true);}
    @Override protected void defineSynchedData(){entityData.define(TRAVEL,0f);}
    public float travel(){return entityData.get(TRAVEL);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean canBeCollidedWith(){return true;}
    @Override public boolean isPickable(){return true;}
    @Override public boolean isPushable(){return false;}
    @Override protected AABB makeBoundingBox(){
        double angle=Math.toRadians(getYRot()),x=Math.abs(Math.cos(angle))*1.5+Math.abs(Math.sin(angle))*2.5,z=Math.abs(Math.sin(angle))*1.5+Math.abs(Math.cos(angle))*2.5;
        return new AABB(getX()-x,getY(),getZ()-z,getX()+x,getY()+2.5,getZ()+z);
    }
    /** Standing on the deck: shared by the server (fall reset) and the client (carrying), so both agree on who rides. */
    public boolean carries(net.minecraft.world.entity.player.Player p){
        var box=getBoundingBox();
        return p.isAlive()&&!p.isSpectator()&&box.inflate(.1,.3,.1).intersects(p.getBoundingBox())&&p.getY()>=box.maxY-.2&&p.getY()<=box.maxY+.3&&p.getDeltaMovement().y<=.1;
    }
    /** Riders are carried by their own client (EscortRiding); teleporting them here would freeze their movement and aim while acks are pending. */
    public void advance(Vec3 next,Vec3 direction){
        var old=position();double oldYaw=getYRot(),yaw=oldYaw+net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(-direction.x,direction.z))-oldYaw);
        entityData.set(TRAVEL,travel()+(float)old.distanceTo(next));
        for(var p:level().getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,getBoundingBox().inflate(.1,.3,.1),this::carries))p.fallDistance=0;
        setYRot((float)yaw);setPos(next);setBoundingBox(makeBoundingBox());
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&!MatchManager.isEscortCart(this))discard();}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){setYRot(getYRot()+net.minecraft.util.Mth.wrapDegrees(yaw-getYRot()));setXRot(pitch);setPos(x,y,z);setBoundingBox(makeBoundingBox());}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
