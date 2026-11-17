package dev.gungang.acoustics.server;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

/** One lobby request, advanced only on the server thread. Never waits for chunk I/O. */
final class EscortRouteScan implements AutoCloseable {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("gungang_escort_scan",Comparator.comparingLong(ChunkPos::toLong));
    private final ServerLevel level;
    private final EscortRules rules;
    private final int minX,minY,minZ,maxX,maxY,maxZ;
    private final long revision;
    private final int started;
    private final Set<ChunkPos> tickets=new HashSet<>();
    private final Map<BlockPos,Integer> markers=new HashMap<>();
    private final BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
    private int cx,cz,x,y,z;
    private LevelChunk chunk;
    private CompletableFuture<LevelChunk> loading;
    private CompletableFuture<EscortRoute> building;
    private boolean closed;
    private boolean chunkHasMarker;

    EscortRouteScan(ServerLevel level,EscortRules rules){
        rules.validate();this.level=level;this.rules=rules;revision=EscortTracks.markerRevision();
        started=level.getServer().getTickCount();
        var a=rules.scanArea();
        minX=(int)Math.floor(a.minX());maxX=(int)Math.floor(a.maxX());
        minY=Math.max(level.getMinBuildHeight(),(int)Math.floor(a.minY()));
        maxY=Math.min(level.getMaxBuildHeight()-1,(int)Math.floor(a.maxY()));
        minZ=(int)Math.floor(a.minZ());maxZ=(int)Math.floor(a.maxZ());
        cx=minX>>4;cz=minZ>>4;
    }

    /** Null means still preparing. Budget covers all block reads for this lobby's sole request. */
    EscortRoute advance(){
        if(closed)throw new IllegalStateException("취소된 호위 경로 검사입니다.");
        if(level.getServer().getTickCount()-started>2400)throw new IllegalStateException("호위 경로 검사 시간이 초과되었습니다. 청크 로딩 상태를 확인하고 다시 시도하세요.");
        if(revision!=EscortTracks.markerRevision())throw new IllegalStateException("검사 중 호위 경로 블록이 변경되었습니다. 다시 시도하세요.");
        if(building!=null)return building.isDone()?building.join():null;
        long deadline=System.nanoTime()+2_000_000;
        int reads=0;
        while(minY<=maxY&&cz<=(maxZ>>4)){
            if(reads>=8192||System.nanoTime()>=deadline)return null;
            if(chunk==null){
                var cp=new ChunkPos(cx,cz);
                if(tickets.add(cp))level.getChunkSource().addRegionTicket(TICKET,cp,0,cp);
                if(loading!=null){
                    if(!loading.isDone())return null;
                    chunk=loading.join();loading=null;
                    if(chunk==null)throw new IllegalStateException("호위 경로 청크를 불러오지 못했습니다: "+cp);
                }else chunk=level.getChunkSource().getChunkNow(cx,cz);
                if(chunk==null){
                    var source=level.getChunkSource();int loadX=cx,loadZ=cz;
                    // Like BotNavGraph: off-thread entry queues the request to the server without
                    // invoking the API's blocking server-thread branch. No world reads on workers.
                    loading=CompletableFuture.supplyAsync(()->source.getChunkFuture(loadX,loadZ,ChunkStatus.FULL,true))
                            .thenCompose(f->f).thenApply(r->r.left().filter(LevelChunk.class::isInstance).map(LevelChunk.class::cast).orElse(null));
                    return null;
                }
                x=Math.max(minX,cx<<4);z=Math.max(minZ,cz<<4);y=minY;
                chunkHasMarker=false;
            }
            int endX=Math.min(maxX,(cx<<4)+15),endZ=Math.min(maxZ,(cz<<4)+15);
            // Read the retained chunk directly; getBlockState on the level could synchronously load it.
            var state=chunk.getBlockState(pos.set(x,y,z));reads++;
            if(state.getBlock() instanceof EscortBlocks.Marker marker){
                chunkHasMarker=true;
                markers.put(pos.immutable(),marker.kind);
                if(markers.size()>512)throw new IllegalArgumentException("호위 경로: 지정 블록은 512개 이하여야 합니다.");
            }
            if(++x>endX){x=Math.max(minX,cx<<4);if(++z>endZ){z=Math.max(minZ,cz<<4);y++;}}
            if(y>maxY){
                // Only marker chunks must stay loaded until the caller installs the route.
                if(!chunkHasMarker){var cp=new ChunkPos(cx,cz);level.getChunkSource().removeRegionTicket(TICKET,cp,0,cp);tickets.remove(cp);}
                chunk=null;if(++cx>(maxX>>4)){cx=minX>>4;cz++;}
            }
        }
        var snapshot=Map.copyOf(markers);
        building=CompletableFuture.supplyAsync(()->EscortRoute.build(snapshot,rules.linkDistance()));
        return null;
    }

    @Override public void close(){
        if(closed)return;closed=true;
        for(var cp:tickets)level.getChunkSource().removeRegionTicket(TICKET,cp,0,cp);
        tickets.clear();chunk=null;markers.clear();
        // Outstanding chunk requests may finish, but no callback can mutate lobby state.
        if(building!=null)building.cancel(false);
    }
}
