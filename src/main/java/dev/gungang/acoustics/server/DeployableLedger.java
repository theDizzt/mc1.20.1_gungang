package dev.gungang.acoustics.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Counts remain authoritative when deployable chunks are unloaded. */
final class DeployableLedger extends SavedData {
    private record Entry(UUID owner,boolean wall) { }
    private final Map<UUID,Entry> entries=new HashMap<>();
    static DeployableLedger get(MinecraftServer server) {return server.overworld().getDataStorage().computeIfAbsent(DeployableLedger::load,DeployableLedger::new,"gungang_deployables");}
    private static DeployableLedger load(CompoundTag tag) {
        var data=new DeployableLedger();for(var value:tag.getList("Entries",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value;if(entry.hasUUID("Id")&&entry.hasUUID("Owner"))data.entries.put(entry.getUUID("Id"),new Entry(entry.getUUID("Owner"),entry.getBoolean("Wall")));
        }return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list=new ListTag();entries.forEach((id,entry)->{var row=new CompoundTag();row.putUUID("Id",id);row.putUUID("Owner",entry.owner());row.putBoolean("Wall",entry.wall());list.add(row);});tag.put("Entries",list);return tag;
    }
    void add(DeployableEntity entity) {entries.put(entity.getUUID(),new Entry(entity.owner(),entity.wall()));setDirty();}
    boolean contains(UUID id) {return entries.containsKey(id);}
    long count(UUID owner,boolean wall) {return entries.values().stream().filter(e->e.owner().equals(owner)&&e.wall()==wall).count();}
    void remove(UUID id) {if(entries.remove(id)!=null)setDirty();}
    void clear(UUID owner) {if(entries.values().removeIf(e->e.owner().equals(owner)))setDirty();}
    void clearAll() {entries.clear();setDirty();}
}
