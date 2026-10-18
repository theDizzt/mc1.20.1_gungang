package dev.gungang.acoustics.server;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;

public final class EscortRail extends Entity {
    private static final EntityDataAccessor<Float> LENGTH=SynchedEntityData.defineId(EscortRail.class,EntityDataSerializers.FLOAT);
    public long revision;
    public EscortRail(EntityType<? extends EscortRail> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(){entityData.define(LENGTH,1f);}
    public float segmentLength(){return entityData.get(LENGTH);}
    public void segmentLength(float value){entityData.set(LENGTH,value);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){revision=tag.getLong("Revision");segmentLength(tag.getFloat("Length"));}
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.putLong("Revision",revision);tag.putFloat("Length",segmentLength());}
    @Override public void tick(){super.tick();if(!level().isClientSide&&revision!=EscortTracks.get((net.minecraft.server.level.ServerLevel)level()).revision)discard();}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
