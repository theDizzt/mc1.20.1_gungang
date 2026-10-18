package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class StationEntity extends BlockEntity {
    private long readyAt;
    private final Map<UUID,Long> touches=new HashMap<>();
    private final Map<UUID,Long> actions=new HashMap<>();
    public StationEntity(BlockPos pos,BlockState state){super(StationBlocks.ENTITY.get(),pos,state);}
    public long remaining(){return level==null?0:Math.max(0,readyAt-level.getGameTime());}
    public void interact(ServerPlayer player,boolean stepping){
        if(level==null||!player.isAlive()||player.isSpectator()||LobbyManager.respawning(player.getUUID()))return;
        var block=(StationBlock)getBlockState().getBlock();long now=level.getGameTime();
        if(block.locked(player))return;
        if(block.healthPack()) {
            if(!stepping||remaining()>0||player.getHealth()>=player.getMaxHealth())return;
            var config=block.kind==StationBlock.Kind.SMALL?GadgetConfig.smallHealthPack:GadgetConfig.largeHealthPack;
            if(config.heal()<=0)return;
            float before=player.getHealth();player.heal((float)config.heal());
            if(player.getHealth()<=before)return;
            player.playNotifySound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.2f);
            readyAt=now+(long)Math.ceil(config.cooldownSeconds()*20);
            level.setBlock(worldPosition,getBlockState().setValue(StationBlock.COOLING,remaining()>0),3);sync();return;
        }
        if(stepping){Long previous=touches.put(player.getUUID(),now);if(previous!=null&&now-previous<10)return;}
        if(now-actions.getOrDefault(player.getUUID(),-100L)<10)return;
        actions.put(player.getUUID(),now);LobbyManager.station(player,block.kind,worldPosition);
    }
    public static void tick(Level level,BlockPos pos,BlockState state,StationEntity entity){
        if(state.getValue(StationBlock.COOLING)&&entity.remaining()==0){level.setBlock(pos,state.setValue(StationBlock.COOLING,false),3);entity.sync();}
        // Also detect stationary players: movement callbacks alone do not cover waiting on a pack.
        var shape=state.getCollisionShape(level,pos);
        double top=shape.isEmpty()?1:shape.max(net.minecraft.core.Direction.Axis.Y);
        var block=(StationBlock)state.getBlock();
        var target=block.interactionEntity(level,pos,state);
        if(target instanceof StationEntity station)
            for(var player:level.getEntitiesOfClass(ServerPlayer.class,new net.minecraft.world.phys.AABB(pos.getX(),pos.getY()+top,pos.getZ(),pos.getX()+1,pos.getY()+top+.15,pos.getZ()+1)))
                if(player.getY()>=pos.getY()+top-.01&&player.getY()<=pos.getY()+top+.15)station.interact(player,true);
        if(level.getGameTime()%200==0){entity.touches.values().removeIf(t->level.getGameTime()-t>200);entity.actions.values().removeIf(t->level.getGameTime()-t>200);}
    }
    private void sync(){setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putLong("ReadyAt",readyAt);}
    @Override public void load(CompoundTag tag){super.load(tag);readyAt=Math.max(0,tag.getLong("ReadyAt"));}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
