package dev.gungang.acoustics.server;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
/** A persistent reset generation also restores unloaded chunks on their next tick. */
public final class GimmickEpoch extends SavedData {
    long generation;
    public static GimmickEpoch get(ServerLevel level){return level.getDataStorage().computeIfAbsent(t->{var d=new GimmickEpoch();d.generation=t.getLong("Generation");return d;},GimmickEpoch::new,"gungang_gimmicks");}
    public static void reset(MinecraftServer server){CompoundBow.clearArrows(server);PropSummons.clear(server);var match=MatchManager.propHunt();if(match!=null)match.abilities.resetTransient();for(var level:server.getAllLevels()){var d=get(level);d.generation++;d.setDirty();}}
    @Override public CompoundTag save(CompoundTag tag){tag.putLong("Generation",generation);return tag;}
}
