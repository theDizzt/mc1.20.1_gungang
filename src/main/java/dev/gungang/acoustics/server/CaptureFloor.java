package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

/** Only recolors existing wool and point-colored glass inside the capture volume and its floor. */
final class CaptureFloor {
    private ServerLevel level;
    private final List<BlockPos> positions=new ArrayList<>();
    private int previous=2;
    void prepare(MinecraftServer server,ArenaMap map) {
        positions.clear();previous=2;
        level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(map.dimension())));
        if(level==null||map.capture()==null)return;
        var a=map.capture().area();
        int x0=(int)Math.floor(a.minX()),y0=Math.max(level.getMinBuildHeight(),(int)Math.floor(a.minY())-1),z0=(int)Math.floor(a.minZ());
        int x1=(int)Math.ceil(a.maxX())-1,y1=Math.min(level.getMaxBuildHeight()-1,(int)Math.ceil(a.maxY())-1),z1=(int)Math.ceil(a.maxZ())-1;
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>65536) {
            com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Capture floor recoloring skipped: area exceeds 65536 blocks ({})",map.id());return;
        }
        for(var pos:BlockPos.betweenClosed(x0,y0,z0,x1,y1,z1)) {
            var state=level.getBlockState(pos);
            if(state.is(BlockTags.WOOL)||state.is(Blocks.WHITE_STAINED_GLASS)||state.is(Blocks.RED_STAINED_GLASS)||state.is(Blocks.BLUE_STAINED_GLASS))positions.add(pos.immutable());
        }
        color(0);
    }
    void color(int owner) {
        if(level==null||previous==owner)return;
        previous=owner;
        for(var pos:positions) {
            var state=level.getBlockState(pos);
            boolean wool=state.is(BlockTags.WOOL);
            if(!wool&&!state.is(Blocks.WHITE_STAINED_GLASS)&&!state.is(Blocks.RED_STAINED_GLASS)&&!state.is(Blocks.BLUE_STAINED_GLASS))continue;
            var block=wool?(owner>0?Blocks.RED_WOOL:owner<0?Blocks.BLUE_WOOL:Blocks.WHITE_WOOL)
                    :(owner>0?Blocks.RED_STAINED_GLASS:owner<0?Blocks.BLUE_STAINED_GLASS:Blocks.WHITE_STAINED_GLASS);
            if(!state.is(block))level.setBlock(pos,block.defaultBlockState(),2);
        }
    }
    void reset() { color(0);positions.clear();level=null; }
}
