package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/** Only recolors existing wool and point-colored glass inside the capture volume and its floor. */
final class CaptureFloor {
    private static final int READS_PER_TICK=1024,CHANGES_PER_TICK=128;
    private static final long WORK_NANOS=1_000_000;
    private static final TicketType<ChunkPos> TICKET=TicketType.create("gungang_capture_floor",Comparator.comparingLong(ChunkPos::toLong));
    // Old neutral restorations finish before a new map is scanned, including overlapping areas.
    private final ArrayDeque<Job> jobs=new ArrayDeque<>();
    private Job active;
    void prepare(MinecraftServer server,ArenaMap map) {
        reset();
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(map.dimension())));
        if(level==null||map.capture()==null)return;
        var a=map.capture().area();
        int x0=(int)Math.floor(a.minX()),y0=Math.max(level.getMinBuildHeight(),(int)Math.floor(a.minY())-1),z0=(int)Math.floor(a.minZ());
        int x1=(int)Math.ceil(a.maxX())-1,y1=Math.min(level.getMaxBuildHeight()-1,(int)Math.ceil(a.maxY())-1),z1=(int)Math.ceil(a.maxZ())-1;
        if(x1<x0||y1<y0||z1<z0)return;
        if(((long)x1-x0+1)*((long)y1-y0+1)*((long)z1-z0+1)>65536) {
            com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Capture floor recoloring skipped: area exceeds 65536 blocks ({})",map.id());return;
        }
        active=new Job(level,BlockPos.betweenClosed(x0,y0,z0,x1,y1,z1).iterator());
        jobs.addLast(active);
    }
    void color(int owner) {
        if(active!=null)active.color(owner);
    }
    /** Runs even in the lobby so end-of-match restoration is also spread across ticks. */
    void tick(){
        long deadline=System.nanoTime()+WORK_NANOS;
        int reads=0,changes=0;
        while(!jobs.isEmpty()&&reads<READS_PER_TICK&&changes<CHANGES_PER_TICK&&System.nanoTime()<deadline){
            var job=jobs.peekFirst();
            if(job.done()){
                job.release();
                if(job.retired){jobs.removeFirst();continue;}
                return;
            }
            // -1 yields while a ticket loads the next chunk. No synchronous getChunk/getBlockState.
            int changed=job.step();if(changed<0)return;
            reads++;changes+=changed;
        }
    }
    private static final class Job {
        final ServerLevel level;
        final List<BlockPos> positions=new ArrayList<>();
        final Set<ChunkPos> tickets=new HashSet<>();
        Iterator<BlockPos> scanning;
        BlockPos pending;
        int cursor,owner;
        boolean retired;
        BlockState wool=Blocks.WHITE_WOOL.defaultBlockState(),glass=Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        Job(ServerLevel level,Iterator<BlockPos> scanning){this.level=level;this.scanning=scanning;}
        void color(int next){
            if(owner==next)return;
            owner=next;cursor=0;
            DyeColor dye=next==0?DyeColor.WHITE:teamDye(next>0?"ALPHA":"BRAVO");
            wool=BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft",dye.getName()+"_wool")).defaultBlockState();
            glass=BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft",dye.getName()+"_stained_glass")).defaultBlockState();
        }
        boolean done(){return scanning==null&&cursor>=positions.size();}
        int step(){
            if(scanning!=null&&pending==null){
                if(!scanning.hasNext()){scanning=null;return 0;}
                pending=scanning.next().immutable();
            }
            var pos=scanning!=null?pending:positions.get(cursor);
            var cp=new ChunkPos(pos);
            var chunk=level.getChunkSource().getChunkNow(cp.x,cp.z);
            if(chunk==null){
                if(tickets.add(cp))level.getChunkSource().addRegionTicket(TICKET,cp,0,cp);
                return -1;
            }
            var state=chunk.getBlockState(pos);
            if(scanning!=null){
                if(state.is(BlockTags.WOOL)||state.is(Blocks.WHITE_STAINED_GLASS)||state.is(Blocks.RED_STAINED_GLASS)||state.is(Blocks.BLUE_STAINED_GLASS))positions.add(pos);
                pending=null;return 0;
            }
            cursor++;
            BlockState target=state.is(BlockTags.WOOL)?wool:state.getBlock() instanceof StainedGlassBlock?glass:null;
            return target!=null&&!state.is(target.getBlock())&&level.setBlock(pos,target,2)?1:0;
        }
        void release(){
            for(var cp:tickets)level.getChunkSource().removeRegionTicket(TICKET,cp,0,cp);
            tickets.clear();
        }
        void retire(){
            retired=true;
            // Discovery has not changed any blocks yet; cancelling it needs no restoration.
            if(scanning!=null){scanning=null;pending=null;positions.clear();cursor=0;}
            color(0);
        }
    }
    private static DyeColor teamDye(String team) {
        int rgb=LobbyManager.options().color(team);
        if(rgb==TeamOptions.defaults().color(team)) return team.equals("ALPHA")?DyeColor.RED:DyeColor.BLUE;
        int best=Integer.MAX_VALUE; DyeColor closest=DyeColor.WHITE;
        for(var dye:DyeColor.values()) {
            int other=dye.getTextColor();
            int r=(rgb>>16&255)-(other>>16&255),g=(rgb>>8&255)-(other>>8&255),b=(rgb&255)-(other&255);
            int distance=r*r+g*g+b*b;
            if(distance<best) { best=distance; closest=dye; }
        }
        return closest;
    }
    void reset() {if(active!=null){active.retire();active=null;}}
    /** Shutdown has no following ticks: finish neutral restoration before the world is saved. */
    void shutdown(){
        reset();
        try{
            for(var job:jobs)for(var pos:job.positions){
                var state=job.level.getBlockState(pos);
                var target=state.is(BlockTags.WOOL)?Blocks.WHITE_WOOL:state.getBlock() instanceof StainedGlassBlock?Blocks.WHITE_STAINED_GLASS:null;
                if(target!=null&&!state.is(target))job.level.setBlock(pos,target.defaultBlockState(),2);
            }
        }finally{clear();}
    }
    void clear(){for(var job:jobs)job.release();jobs.clear();active=null;}
}
