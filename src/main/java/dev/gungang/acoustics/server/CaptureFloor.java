package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.StainedGlassBlock;

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
            if(!wool&&!(state.getBlock() instanceof StainedGlassBlock))continue;
            DyeColor dye=owner==0?DyeColor.WHITE:teamDye(owner>0?"ALPHA":"BRAVO");
            var block=BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft",dye.getName()+(wool?"_wool":"_stained_glass")));
            if(!state.is(block))level.setBlock(pos,block.defaultBlockState(),2);
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
    void reset() { color(0);positions.clear();level=null; }
}
