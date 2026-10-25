package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.gungang.acoustics.GungangAcoustics;

/** Persistent marker index and track generation; stale unloaded rails self-remove on loading. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class EscortTracks extends SavedData {
    private final Set<BlockPos> markers=new HashSet<>();
    private static final Set<ServerLevel> DIRTY=Collections.newSetFromMap(new WeakHashMap<>());
    long revision;
    public static EscortTracks get(ServerLevel level){return level.getDataStorage().computeIfAbsent(EscortTracks::load,EscortTracks::new,"gungang_escort_tracks");}
    private static EscortTracks load(CompoundTag tag){var data=new EscortTracks();data.revision=tag.getLong("Revision");for(long p:tag.getLongArray("Markers"))data.markers.add(BlockPos.of(p));return data;}
    @Override public CompoundTag save(CompoundTag tag){tag.putLong("Revision",revision);tag.putLongArray("Markers",markers.stream().mapToLong(BlockPos::asLong).toArray());return tag;}
    public static void changed(ServerLevel level,BlockPos pos,boolean add){var data=get(level);if(add)data.markers.add(pos.immutable());else data.markers.remove(pos);data.setDirty();DIRTY.add(level);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        for(var level:List.copyOf(DIRTY)){
            DIRTY.remove(level);var active=MatchManager.escort();
            get(level).rebuild(level,active!=null&&active.cart.level()==level?active.route:null);
        }
    }
    public void rebuild(ServerLevel level,EscortRoute selected){
        // Index scan results as well, so pre-existing structures are picked up on selection.
        if(selected!=null)markers.addAll(selected.markers);
        revision++;setDirty();
        var oldRails=new ArrayList<EscortRail>();
        for(var e:level.getAllEntities())if(e instanceof EscortRail r)oldRails.add(r);
        oldRails.forEach(EscortRail::discard);
        var remaining=new HashSet<>(markers);
        if(selected!=null){render(level,selected);remaining.removeAll(selected.markers);}
        while(!remaining.isEmpty()){
            var group=new ArrayList<BlockPos>();var queue=new ArrayDeque<BlockPos>();var first=remaining.iterator().next();remaining.remove(first);queue.add(first);
            while(!queue.isEmpty()){
                var p=queue.remove();if(EscortRoute.kind(level,p)<0)continue;group.add(p);
                for(var q:List.copyOf(remaining))if(p.distSqr(q)<=256){remaining.remove(q);queue.add(q);}
            }
            try{render(level,EscortRoute.build(level,group,16));}catch(IllegalArgumentException ignored){/* Incomplete editing route: no misleading rails. */}
        }
    }
    private void render(ServerLevel level,EscortRoute route){
        if(route.length>2048)throw new IllegalArgumentException("호위 경로는 2048블록 이하여야 합니다.");
        for(double d=0;d<route.length;){
            double step=Math.min(.75,route.length-d);
            while(step>.125&&route.direction(d).dot(route.direction(d+step))<.996)step/=2;
            var a=route.at(d);var b=route.at(d+step);var direction=b.subtract(a);var rail=EscortBlocks.RAIL.get().create(level);
            rail.revision=revision;rail.segmentLength((float)direction.length()+.03f);rail.setPos(a.lerp(b,.5));
            rail.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));rail.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z))));level.addFreshEntity(rail);
            d+=step;
        }
    }
    private static final Map<ServerLevel,Map<BlockPos,Boolean>> MATCH_HIDDEN=new WeakHashMap<>();
    /** Every mode hides the escort editor markers while a match runs; {@link #showMarkers} puts the previous flags back. */
    public static void hideMarkers(net.minecraft.server.MinecraftServer server){
        showMarkers();
        for(var level:server.getAllLevels()){
            var previous=new HashMap<BlockPos,Boolean>();
            for(var p:List.copyOf(get(level).markers)){
                var state=level.getBlockState(p);if(!(state.getBlock() instanceof EscortBlocks.Marker))continue;
                previous.put(p,state.getValue(EscortBlocks.Marker.HIDDEN));
                if(!state.getValue(EscortBlocks.Marker.HIDDEN))level.setBlock(p,state.setValue(EscortBlocks.Marker.HIDDEN,true),3);
            }
            if(!previous.isEmpty())MATCH_HIDDEN.put(level,previous);
        }
    }
    public static void showMarkers(){
        for(var entry:MATCH_HIDDEN.entrySet())for(var marker:entry.getValue().entrySet()){
            var level=entry.getKey();var state=level.getBlockState(marker.getKey());
            if(state.getBlock() instanceof EscortBlocks.Marker&&state.getValue(EscortBlocks.Marker.HIDDEN)!=marker.getValue())level.setBlock(marker.getKey(),state.setValue(EscortBlocks.Marker.HIDDEN,marker.getValue()),3);
        }
        MATCH_HIDDEN.clear();
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){DIRTY.clear();MATCH_HIDDEN.clear();}
    @SubscribeEvent public static void starting(net.minecraftforge.event.server.ServerStartingEvent e){
        // A process crash must not leave editor markers permanently hidden, nor escort spawn gates switched on.
        EscortSpawnGateBlock.deactivate(e.getServer());
        for(var level:e.getServer().getAllLevels())for(var p:List.copyOf(get(level).markers)){
            var state=level.getBlockState(p);
            if(state.getBlock() instanceof EscortBlocks.Marker&&state.getValue(EscortBlocks.Marker.HIDDEN))level.setBlock(p,state.setValue(EscortBlocks.Marker.HIDDEN,false),3);
        }
    }
}
