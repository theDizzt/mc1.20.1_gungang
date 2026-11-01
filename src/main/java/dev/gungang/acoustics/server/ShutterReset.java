package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Shares the existing map-object reset epoch, including chunks unloaded during a reset. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class ShutterReset {
    private static final String KEY="GunGangShutterReset";
    private static final Map<LevelChunk,Long> epochs=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<LevelChunk> loaded=Collections.newSetFromMap(new WeakHashMap<>());
    @SubscribeEvent public static void read(ChunkDataEvent.Load e){
        if(e.getChunk() instanceof LevelChunk chunk)epochs.put(chunk,e.getData().contains(KEY)?e.getData().getLong(KEY):-1L);
    }
    @SubscribeEvent public static void save(ChunkDataEvent.Save e){
        var access=e.getChunk();
        if(access instanceof net.minecraft.world.level.chunk.ImposterProtoChunk wrapper)access=wrapper.getWrapped();
        if(access instanceof LevelChunk chunk)e.getData().putLong(KEY,epochs.getOrDefault(chunk,-1L));
    }
    @SubscribeEvent public static void load(ChunkEvent.Load e){
        if(e.getLevel() instanceof ServerLevel&&e.getChunk() instanceof LevelChunk chunk)loaded.add(chunk);
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload e){if(e.getChunk() instanceof LevelChunk chunk)loaded.remove(chunk);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.LevelTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(e.level instanceof ServerLevel level))return;
        for(var chunk:List.copyOf(loaded))if(chunk.getLevel()==level)catchUp(level,chunk);
    }
    static void catchUp(ServerLevel level,LevelChunk chunk){
        long epoch=GimmickEpoch.get(level).generation;
        if(epochs.getOrDefault(chunk,-1L)!=epoch){
            if(epoch>0)resetChunk(level,chunk);
            epochs.put(chunk,epoch);chunk.setUnsaved(true);
        }
    }
    public static void reset(ServerLevel level){
        long epoch=GimmickEpoch.get(level).generation;
        for(var chunk:List.copyOf(loaded))if(chunk.getLevel()==level){resetChunk(level,chunk);epochs.put(chunk,epoch);chunk.setUnsaved(true);}
    }
    static void resetChunk(ServerLevel level,LevelChunk chunk){
        var levers=new ArrayList<BlockPos>();var shutters=new ArrayList<BlockPos>();
        var sections=chunk.getSections();
        for(int i=0;i<sections.length;i++){
            var section=sections[i];
            if(!section.maybeHas(s->s.getBlock() instanceof LeverBlock||s.getBlock() instanceof ShutterBlock))continue;
            int baseY=chunk.getSectionYFromSectionIndex(i)*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var state=section.getBlockState(x,y,z);
                if(state.getBlock() instanceof LeverBlock&&state.getValue(LeverBlock.POWERED))levers.add(new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z));
                else if(state.getBlock() instanceof ShutterBlock)shutters.add(new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z));
            }
        }
        for(var pos:levers){
            var state=level.getBlockState(pos);level.setBlock(pos,state.setValue(LeverBlock.POWERED,false),3);
            var face=state.getValue(LeverBlock.FACE);
            var support=face==AttachFace.FLOOR?pos.below():face==AttachFace.CEILING?pos.above():pos.relative(state.getValue(LeverBlock.FACING).getOpposite());
            level.updateNeighborsAt(support,state.getBlock());
        }
        for(var pos:shutters)if(level.getBlockEntity(pos) instanceof ShutterEntity shutter)shutter.resetClosed();
    }
    private ShutterReset(){}
}
