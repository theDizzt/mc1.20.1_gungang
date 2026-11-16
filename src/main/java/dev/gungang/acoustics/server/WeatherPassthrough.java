package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Rain and snow fall through barriers (invisible map walls/ceilings) and death zone markers: the precipitation surface
 * of a column is its heightmap top, lowered past any of those blocks to the first real block under them. Used by the
 * weather renderer, rain splashes/sounds and Level.isRainingAt.
 */
public final class WeatherPassthrough {
    private record Column(int x,int z,int height,int floor,Heightmap.Types type){}
    private static final class Columns {
        long tick=Long.MIN_VALUE;
        final java.util.Map<Column,Integer> heights=new java.util.HashMap<>();
    }
    // Integrated servers have separate render/server threads. Weak world keys release unloaded dimensions.
    private static final ThreadLocal<java.util.Map<Level,Columns>> CACHE=ThreadLocal.withInitial(java.util.WeakHashMap::new);
    private static final int MAX_COLUMNS=4096;
    public static boolean passes(BlockState state){return state.is(Blocks.BARRIER)||state.getBlock() instanceof DeathZoneBlock;}

    /** The column's precipitation height given its heightmap {@code height}; scanning stops at {@code floor}. */
    public static int height(LevelReader level,Heightmap.Types type,int x,int z,int height,int floor){
        floor=Math.max(floor,level.getMinBuildHeight());
        // Non-world readers have no reliable tick clock; keep their original live lookup.
        if(!(level instanceof Level world))return scan(level,type,x,z,height,floor);
        var columns=CACHE.get().computeIfAbsent(world,ignored->new Columns());
        long tick=world.getGameTime();
        if(columns.tick!=tick){columns.tick=tick;columns.heights.clear();}
        var key=new Column(x,z,height,floor,type);
        var cached=columns.heights.get(key);if(cached!=null)return cached;
        int result=scan(level,type,x,z,height,floor);
        if(columns.heights.size()<MAX_COLUMNS)columns.heights.put(key,result);
        return result;
    }
    private static int scan(LevelReader level,Heightmap.Types type,int x,int z,int height,int floor){
        var pos=new BlockPos.MutableBlockPos(x,height-1,z);
        // Only rescan columns whose normal surface is a pass-through block.
        if(!passes(level.getBlockState(pos)))return height;
        for(int y=height-1;y>=floor;--y){
            pos.setY(y);
            var state=level.getBlockState(pos);
            if(!passes(state)&&type.isOpaque().test(state))return y+1;
        }
        return floor;
    }

    public static BlockPos pos(LevelReader level,Heightmap.Types type,BlockPos top,int floor){
        int y=height(level,type,top.getX(),top.getZ(),top.getY(),floor);
        return y==top.getY()?top:new BlockPos(top.getX(),y,top.getZ());
    }
    private WeatherPassthrough(){}
}
