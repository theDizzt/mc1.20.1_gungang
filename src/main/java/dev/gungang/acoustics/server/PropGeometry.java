package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Shared visible parts and collision bounds for beds, doors, gas tanks and multipart furniture. */
public final class PropGeometry {
    public record Part(BlockPos offset,BlockState state){}
    /** 215: the crops a farmland disguise grows, fully grown; its MODE picks one. */
    public static final int CROPS=4;
    public static BlockState crop(int index){
        return switch(Math.floorMod(index,CROPS)){
            case 0->Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7);
            case 1->Blocks.CARROTS.defaultBlockState().setValue(BlockStateProperties.AGE_7,7);
            case 2->Blocks.POTATOES.defaultBlockState().setValue(BlockStateProperties.AGE_7,7);
            default->Blocks.BEETROOTS.defaultBlockState().setValue(BlockStateProperties.AGE_3,3);
        };
    }
    public static List<Part> parts(PropEntity prop){
        if(prop.item().equals("minecraft:sugar_cane")){var result=new ArrayList<Part>();for(int y=0;y<prop.height();y++)result.add(new Part(BlockPos.ZERO.above(y),prop.state()));return result;}
        if(prop.item().equals("minecraft:farmland")&&prop.state().getBlock() instanceof FarmBlock){var result=new ArrayList<>(parts(prop.state()));result.add(new Part(BlockPos.ZERO.above(),crop(prop.mode())));return result;}
        return parts(prop.state());
    }
    public static List<Part> parts(BlockState state){
        var result=new ArrayList<Part>();
        if(state.getBlock() instanceof ThermalEmitterBlock emitter&&emitter.geyser()){
            // The 3x3 geyser floor, centred on the hider like the placed block is on its centre part.
            for(int part=0;part<9;part++)result.add(new Part(new BlockPos(part%3-1,0,part/3-1),state.setValue(ThermalEmitterBlock.PART,part)));
        }else if(state.getBlock() instanceof MultiPartBlock multi){
            // 235 garden fountain, 241 easel: every cell, the anchor drawing the whole model.
            for(int i=0;i<multi.partCount();i++)result.add(new Part(multi.offset(i,multi.facing(state)),state.setValue(MultiPartBlock.PART,i)));
        }else if(state.getBlock() instanceof FurnitureBlock furniture){
            for(int i=0;i<furniture.partCount();i++)result.add(new Part(furniture.offset(i,state.getValue(FurnitureBlock.FACING)),state.setValue(FurnitureBlock.PART,i)));
        }else{
            result.add(new Part(BlockPos.ZERO,state));
            if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))result.add(new Part(BlockPos.ZERO.above(),state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER)));
            if(state.getBlock() instanceof BedBlock)result.add(new Part(BlockPos.ZERO.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)),state.setValue(BlockStateProperties.BED_PART,BedPart.HEAD)));
            // 218/219: a fired piston shows its head in the cell it faces.
            if(state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock&&state.getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED)){
                var facing=state.getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING);
                result.add(new Part(BlockPos.ZERO.relative(facing),Blocks.PISTON_HEAD.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonHeadBlock.FACING,facing)
                        .setValue(net.minecraft.world.level.block.piston.PistonHeadBlock.TYPE,state.is(Blocks.STICKY_PISTON)?PistonType.STICKY:PistonType.DEFAULT)));
            }
            if(state.getBlock()==GimmickBlocks.GAS.get())result.add(new Part(BlockPos.ZERO.above(),state.setValue(GimmickBlock.TOP,true)));
        }
        return result;
    }
    /**
     * 243 wall clock, 244 mirror: their shape sits at the back of the cell (to hang on a wall), but the disguise's thin
     * body is centred on the hider, so the model and hitbox are shifted by this much (blocks) to centre them on the
     * body too; otherwise the clock sank into whatever wall the hider stood against. Zero for every other prop.
     */
    public static net.minecraft.world.phys.Vec3 thinOffset(PropEntity prop){
        if(!PartyBlocks.thin(prop.item()))return net.minecraft.world.phys.Vec3.ZERO;
        var shape=prop.state().getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO);
        if(shape.isEmpty())return net.minecraft.world.phys.Vec3.ZERO;
        var b=shape.bounds();
        return new net.minecraft.world.phys.Vec3(.5-(b.minX+b.maxX)/2,0,.5-(b.minZ+b.maxZ)/2);
    }
    private PropGeometry(){}
}
