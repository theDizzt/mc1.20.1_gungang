package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Rain and snow fall through barriers (invisible map walls/ceilings) and death zone markers: the precipitation surface
 * of a column is its heightmap top, lowered past any of those blocks to the first real block under them. Used by the
 * weather renderer, rain splashes/sounds and Level.isRainingAt.
 */
public final class WeatherPassthrough {
    public static boolean passes(BlockState state){return state.is(Blocks.BARRIER)||state.getBlock() instanceof DeathZoneBlock;}

    /** The column's precipitation height given its heightmap {@code height}; scanning stops at {@code floor}. */
    public static int height(LevelReader level,Heightmap.Types type,int x,int z,int height,int floor){
        var pos=new BlockPos.MutableBlockPos(x,height-1,z);
        // Only rescan columns whose normal surface is a pass-through block.
        if(!passes(level.getBlockState(pos)))return height;
        floor=Math.max(floor,level.getMinBuildHeight());
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
