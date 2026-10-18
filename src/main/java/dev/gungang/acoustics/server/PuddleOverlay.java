package dev.gungang.acoustics.server;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
public final class PuddleOverlay extends Entity {
    public PuddleOverlay(EntityType<?> type,Level level){super(type,level);setNoGravity(true);}
    public boolean acid(){return getType()==GimmickBlocks.ACID_PUDDLE_OVERLAY.get();}
    @Override protected void defineSynchedData(){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override public boolean isPickable(){return true;}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){if(source.getEntity() instanceof net.minecraft.world.entity.player.Player p&&p.isCreative()){if(!level().isClientSide)discard();return true;}return false;}
    @Override public void tick(){super.tick();if(!level().isClientSide)for(var entity:level().getEntities(this,getBoundingBox().inflate(0,.1,0)))PuddleBlock.splash(level(),entity,acid());}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
