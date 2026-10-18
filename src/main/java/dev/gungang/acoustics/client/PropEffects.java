package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.PropEntity;
import dev.gungang.acoustics.server.PropGeometry;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Disguises are entities, so the world never ticks them like blocks. This plays their block effects on the client:
 * animateTick particles (torch/candle flames, lava drips), client block-entity tickers (campfire smoke) and light.
 * Light is a client-only invisible light block in the prop's air cell, restored when the prop moves or disappears,
 * so nothing is written to the server world.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class PropEffects {
    private record Glow(BlockPos pos,BlockState original){}
    private static final Map<Integer,Glow> GLOWS=new HashMap<>();
    private static final Map<Integer,Map<BlockPos,BlockEntity>> TICKING=new HashMap<>();
    private static ClientLevel last;
    /** Vanilla samples a given nearby block for animateTick about once every 44 ticks. */
    private static final int ANIMATE_CHANCE=44;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level!=last){GLOWS.clear();TICKING.clear();last=level;}
        if(level==null||mc.isPaused())return;
        var seen=new HashSet<Integer>();
        for(var entity:level.entitiesForRendering()){
            if(!(entity instanceof PropEntity prop)||prop.isRemoved())continue;
            seen.add(prop.getId());
            int emission=0;
            var entities=TICKING.computeIfAbsent(prop.getId(),id->new HashMap<>());
            for(var part:PropGeometry.parts(prop.state())){
                var state=part.state();var pos=prop.blockPosition().offset(part.offset());
                emission=Math.max(emission,state.getLightEmission());
                if(level.random.nextInt(ANIMATE_CHANCE)==0)state.getBlock().animateTick(state,level,pos,level.random);
                // Chest lids already animate in PropRenderer.
                if(state.getBlock() instanceof EntityBlock block&&!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock))clientTicker(level,block,state,pos,part.offset(),entities);
            }
            BlockPos cell=BlockPos.containing(prop.getX(),prop.getY()+.5,prop.getZ());
            glow(level,prop.getId(),emission>0?cell:null,emission);
        }
        for(var id:List.copyOf(GLOWS.keySet()))if(!seen.contains(id))glow(level,id,null,0);
        TICKING.keySet().retainAll(seen);
    }
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void clientTicker(ClientLevel level,EntityBlock block,BlockState state,BlockPos pos,BlockPos offset,Map<BlockPos,BlockEntity> cache){
        var be=cache.get(offset);
        if(be==null||be.getBlockState().getBlock()!=state.getBlock()){be=block.newBlockEntity(pos,state);if(be==null)return;be.setLevel(level);cache.put(offset,be);}
        be.setBlockState(state);
        var ticker=block.getTicker(level,state,(net.minecraft.world.level.block.entity.BlockEntityType<T>)be.getType());
        if(ticker!=null)ticker.tick(level,pos,state,(T)be);
    }
    private static void glow(ClientLevel level,int id,BlockPos pos,int emission){
        var current=GLOWS.get(id);
        if(current!=null&&current.pos().equals(pos)&&level.getBlockState(pos).is(Blocks.LIGHT))return;
        if(current!=null){
            GLOWS.remove(id);
            if(level.getBlockState(current.pos()).is(Blocks.LIGHT))level.setBlock(current.pos(),current.original(),3);
        }
        if(pos==null||!level.getBlockState(pos).isAir())return;
        GLOWS.put(id,new Glow(pos,level.getBlockState(pos)));
        level.setBlock(pos,Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,emission),3);
    }
    private PropEffects(){}
}
