package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Saves the replaced blocks so a point transition, match end or restart can restore the map. */
public final class NormandyBeacon extends SavedData {
    private final List<CompoundTag> original=new ArrayList<>();
    public static NormandyBeacon get(MinecraftServer server) {return server.overworld().getDataStorage().computeIfAbsent(NormandyBeacon::load,NormandyBeacon::new,"gungang_normandy_marker");}
    private static NormandyBeacon load(CompoundTag tag) {var data=new NormandyBeacon();for(var entry:tag.getList("Blocks",Tag.TAG_COMPOUND))data.original.add(((CompoundTag)entry).copy());return data;}
    @Override public CompoundTag save(CompoundTag tag) {var list=new ListTag();original.forEach(list::add);tag.put("Blocks",list);return tag;}
    public void restore(MinecraftServer server) {
        for(var tag:original) {
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(tag.getString("Dimension"))));
            if(level==null)continue;
            var pos=BlockPos.of(tag.getLong("Pos"));
            level.setBlock(pos,NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),tag.getCompound("State")),3);
            var entity=level.getBlockEntity(pos);
            if(entity!=null&&tag.contains("Entity")) {entity.load(tag.getCompound("Entity"));entity.setChanged();}
        }
        original.clear();setDirty();
    }
    private void replace(ServerLevel level,BlockPos pos,BlockState state) {
        if(!level.isInWorldBounds(pos))return;
        var tag=new CompoundTag();tag.putString("Dimension",level.dimension().location().toString());tag.putLong("Pos",pos.asLong());
        tag.put("State",NbtUtils.writeBlockState(level.getBlockState(pos)));
        var entity=level.getBlockEntity(pos);if(entity!=null)tag.put("Entity",entity.saveWithFullMetadata());
        original.add(tag);level.setBlock(pos,state,3);setDirty();
    }
    public void activate(MinecraftServer server,ArenaMap map,int stage) {
        restore(server);
        if(map.normandy()==null||stage>=4)return;
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(map.dimension())));if(level==null)return;
        var center=map.normandy().points().get(stage).center();var pos=BlockPos.containing(center.x(),center.y(),center.z());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)replace(level,pos.offset(x,-2,z),Blocks.IRON_BLOCK.defaultBlockState());
        replace(level,pos.below(),Blocks.BEACON.defaultBlockState());
        var glass=switch(stage) {case 0->Blocks.BLUE_STAINED_GLASS;case 1->Blocks.LIME_STAINED_GLASS;case 2->Blocks.ORANGE_STAINED_GLASS;default->Blocks.RED_STAINED_GLASS;};
        replace(level,pos,glass.defaultBlockState());
    }
}
