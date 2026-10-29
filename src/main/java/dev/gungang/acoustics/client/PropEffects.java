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
    /**
     * The model is drawn centred on the prop, not on its block cell; block code spawns particles from the cell corner.
     * While a prop's block code runs, PropParticleMixin shifts every new particle by this amount.
     */
    private static net.minecraft.world.phys.Vec3 particleOffset=net.minecraft.world.phys.Vec3.ZERO;
    public static net.minecraft.world.phys.Vec3 particleOffset(){return particleOffset;}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level!=last){GLOWS.clear();TICKING.clear();last=level;}
        if(level==null||mc.isPaused())return;
        var seen=new HashSet<Integer>();
        for(var entity:level.entitiesForRendering()){
            if(!(entity instanceof PropEntity prop)||prop.isRemoved())continue;
            seen.add(prop.getId());
            int emission=prop.item().equals("minecraft:end_crystal")?15:0;
            particleOffset=new net.minecraft.world.phys.Vec3(prop.getX()-.5-prop.blockPosition().getX(),prop.getY()-prop.blockPosition().getY(),prop.getZ()-.5-prop.blockPosition().getZ());
            try{
                for(var part:PropGeometry.parts(prop)){
                    var state=part.state();var pos=prop.blockPosition().offset(part.offset());
                    emission=Math.max(emission,state.getLightEmission());
                    if(level.random.nextInt(ANIMATE_CHANCE)==0)state.getBlock().animateTick(state,level,pos,level.random);
                    if(state.getBlock() instanceof EntityBlock block&&!(block instanceof dev.gungang.acoustics.server.ConveyorBlock))clientTicker(level,block,state,pos,blockEntity(prop,part.offset(),state,block));
                }
            }finally{particleOffset=net.minecraft.world.phys.Vec3.ZERO;}
            BlockPos cell=BlockPos.containing(prop.getX(),prop.getY()+.5,prop.getZ());
            glow(level,prop.getId(),emission>0?cell:null,emission);
        }
        for(var id:List.copyOf(GLOWS.keySet()))if(!seen.contains(id))glow(level,id,null,0);
        TICKING.keySet().retainAll(seen);
    }
    /**
     * The one client block entity of a prop part, shared by the ticker here and PropRenderer,
     * so what animates (enchanting book, chest lid) is what gets drawn. Null for blocks without one.
     */
    static BlockEntity blockEntity(PropEntity prop,BlockPos offset,BlockState state,EntityBlock block){
        var cache=TICKING.computeIfAbsent(prop.getId(),id->new HashMap<>());var be=cache.get(offset);
        if(be==null||be.getBlockState().getBlock()!=state.getBlock()){
            be=block.newBlockEntity(prop.blockPosition().offset(offset),state);
            if(be==null){cache.remove(offset);return null;}
            be.setLevel(prop.level());cache.put(offset,be);
            if(be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner)spawner.getSpawner().setEntityId(net.minecraft.world.entity.EntityType.CAVE_SPIDER,prop.level(),prop.level().random,prop.blockPosition());
        }
        be.setBlockState(state);
        if(be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign){
            var text=sign.getFrontText();String[] lines=prop.text().split(", ",3);
            for(int i=0;i<4;i++)text=text.setMessage(i,net.minecraft.network.chat.Component.literal(i<lines.length?lines[i]:""));
            sign.setText(text,true);sign.setText(text,false);
        }
        return be;
    }
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void clientTicker(ClientLevel level,EntityBlock block,BlockState state,BlockPos pos,BlockEntity be){
        if(be==null)return;
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
