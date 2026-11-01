package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import it.unimi.dsi.fastutil.longs.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Per-dimension secret-wand marks: invisible blocks keep working, passable blocks keep their looks. Read by the secret mixins. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SecretBlocks extends SavedData {
    public static final byte INVISIBLE=1,PASSABLE=2;
    private static final Map<ResourceKey<Level>,SecretBlocks> SERVER=new ConcurrentHashMap<>();
    // Copy-on-write: collision and chunk-meshing threads read these without locking.
    private static volatile Long2ByteMap client=Long2ByteMaps.EMPTY_MAP;
    private volatile Long2ByteMap modes=Long2ByteMaps.EMPTY_MAP;

    public static SecretBlocks get(ServerLevel level){return level.getDataStorage().computeIfAbsent(SecretBlocks::load,SecretBlocks::new,"gungang_secret_blocks");}
    private static SecretBlocks load(CompoundTag tag){
        var data=new SecretBlocks();var map=new Long2ByteOpenHashMap();
        long[] pos=tag.getLongArray("Positions");byte[] mode=tag.getByteArray("Modes");
        for(int i=0;i<Math.min(pos.length,mode.length);i++)if(mode[i]!=0)map.put(pos[i],mode[i]);
        data.modes=map;return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var map=modes;long[] pos=new long[map.size()];byte[] mode=new byte[map.size()];int i=0;
        for(var e:map.long2ByteEntrySet()){pos[i]=e.getLongKey();mode[i++]=e.getByteValue();}
        tag.putLongArray("Positions",pos);tag.putByteArray("Modes",mode);return tag;
    }

    /** Mark at pos for a live level (or one of its chunks); other getters such as worldgen regions see plain blocks. */
    public static byte mode(BlockGetter getter,BlockPos pos){
        Level level=getter instanceof Level l?l:getter instanceof LevelChunk c?c.getLevel():null;
        if(level==null)return 0;
        Long2ByteMap map;
        if(level.isClientSide)map=client;
        else{var data=SERVER.get(level.dimension());if(data==null)return 0;map=data.modes;}
        return map.isEmpty()?0:map.get(pos.asLong());
    }
    public static byte clientMode(BlockPos pos){var map=client;return map.isEmpty()?0:map.get(pos.asLong());}
    public static Long2ByteMap client(){return client;}
    public static void client(Long2ByteMap next){client=next;}

    public static void set(ServerLevel level,BlockPos pos,byte mode){
        var data=get(level);var next=new Long2ByteOpenHashMap(data.modes);long key=pos.asLong();
        if(mode==0){if(next.remove(key)==0)return;}else if(next.put(key,mode)==mode)return;
        data.modes=next;data.setDirty();SERVER.put(level.dimension(),data);
        var packet=new GungangNetwork.SecretBlockSync(false,new long[]{key},new byte[]{mode});
        for(var player:level.players())GungangNetwork.send(player,packet);
    }
    private static void sync(ServerPlayer player){
        var map=get(player.serverLevel()).modes;long[] pos=new long[map.size()];byte[] mode=new byte[map.size()];int i=0;
        for(var e:map.long2ByteEntrySet()){pos[i]=e.getLongKey();mode[i++]=e.getByteValue();}
        GungangNetwork.send(player,new GungangNetwork.SecretBlockSync(true,pos,mode));
    }

    @SubscribeEvent public static void load(LevelEvent.Load e){if(e.getLevel() instanceof ServerLevel level)SERVER.put(level.dimension(),get(level));}
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel level)SERVER.remove(level.dimension());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){SERVER.clear();}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void travel(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    /** A block that turns into air (mined, exploded, pushed away) drops its mark so a later placement starts plain. */
    @SubscribeEvent public static void changed(BlockEvent.NeighborNotifyEvent e){
        if(e.getLevel() instanceof ServerLevel level&&e.getState().isAir()&&mode(level,e.getPos())!=0)set(level,e.getPos(),(byte)0);
    }
}
