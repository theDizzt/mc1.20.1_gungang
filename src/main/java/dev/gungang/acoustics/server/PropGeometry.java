package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Shared visible parts and collision bounds for beds, doors, gas tanks and multipart furniture. */
public final class PropGeometry {
    public record Part(BlockPos offset,BlockState state){}
    public static List<Part> parts(PropEntity prop){
        if(prop.item().equals("minecraft:sugar_cane")){var result=new ArrayList<Part>();for(int y=0;y<prop.height();y++)result.add(new Part(BlockPos.ZERO.above(y),prop.state()));return result;}
        return parts(prop.state());
    }
    public static List<Part> parts(BlockState state){
        var result=new ArrayList<Part>();
        if(state.getBlock() instanceof ThermalEmitterBlock emitter&&emitter.geyser()){
            // The 3x3 geyser floor, centred on the hider like the placed block is on its centre part.
            for(int part=0;part<9;part++)result.add(new Part(new BlockPos(part%3-1,0,part/3-1),state.setValue(ThermalEmitterBlock.PART,part)));
        }else if(state.getBlock() instanceof FurnitureBlock furniture){
            for(int i=0;i<furniture.partCount();i++)result.add(new Part(furniture.offset(i,state.getValue(FurnitureBlock.FACING)),state.setValue(FurnitureBlock.PART,i)));
        }else{
            result.add(new Part(BlockPos.ZERO,state));
            if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))result.add(new Part(BlockPos.ZERO.above(),state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER)));
            if(state.getBlock() instanceof BedBlock)result.add(new Part(BlockPos.ZERO.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)),state.setValue(BlockStateProperties.BED_PART,BedPart.HEAD)));
            if(state.getBlock()==GimmickBlocks.GAS.get())result.add(new Part(BlockPos.ZERO.above(),state.setValue(GimmickBlock.TOP,true)));
        }
        return result;
    }
    private PropGeometry(){}
}
