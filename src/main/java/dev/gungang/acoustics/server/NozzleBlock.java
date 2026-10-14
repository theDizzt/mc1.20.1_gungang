package dev.gungang.acoustics.server;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.*;

/** Source-fluid logging, like vanilla waterlogging, with acid and lava support. */
public final class NozzleBlock extends GimmickBlock implements LiquidBlockContainer, BucketPickup {
    public static final IntegerProperty FLUID=IntegerProperty.create("fluid",0,3);

    public NozzleBlock(){
        super("nozzle");
        registerDefaultState(defaultBlockState().setValue(FLUID,0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);
        builder.add(FLUID);
    }

    private static int fluidId(Fluid fluid){
        if(fluid.isSame(Fluids.WATER))return 1;
        if(fluid.isSame(Fluids.LAVA))return 2;
        if(fluid.isSame(GimmickBlocks.ACID_SOURCE.get()))return 3;
        return 0;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        return defaultBlockState().setValue(FLUID,fluidId(context.getLevel().getFluidState(context.getClickedPos()).getType()));
    }

    @Override public FluidState getFluidState(BlockState state){
        return switch(state.getValue(FLUID)){
            case 1->Fluids.WATER.getSource(false);
            case 2->Fluids.LAVA.getSource(false);
            case 3->GimmickBlocks.ACID_SOURCE.get().getSource(false);
            default->Fluids.EMPTY.defaultFluidState();
        };
    }

    @Override public boolean canPlaceLiquid(BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){
        return state.getValue(FLUID)==0&&fluidId(fluid)!=0;
    }

    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){
        if(!canPlaceLiquid(level,pos,state,fluid.getType()))return false;
        if(!level.isClientSide()){
            var filled=state.setValue(FLUID,fluidId(fluid.getType()));
            level.setBlock(pos,filled,3);
            scheduleFluid(level,pos,filled);
        }
        return true;
    }

    private static void scheduleFluid(LevelAccessor level,BlockPos pos,BlockState state){
        var fluid=state.getFluidState();
        if(!fluid.isEmpty())level.scheduleTick(pos,fluid.getType(),fluid.getType().getTickDelay(level));
    }

    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos){
        scheduleFluid(level,pos,state);
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }

    @Override public ItemStack pickupBlock(LevelAccessor level,BlockPos pos,BlockState state){
        var fluid=state.getFluidState();
        if(fluid.isEmpty())return ItemStack.EMPTY;
        level.setBlock(pos,state.setValue(FLUID,0),3);
        return new ItemStack(fluid.getType().getBucket());
    }

    @Override public Optional<SoundEvent> getPickupSound(){return Fluids.WATER.getPickupSound();}
    @Override public Optional<SoundEvent> getPickupSound(BlockState state){return state.getFluidState().getType().getPickupSound();}

    @Override public int getLightEmission(BlockState state,BlockGetter level,BlockPos pos){
        return switch(state.getValue(FLUID)){case 2->15;case 3->12;default->0;};
    }

    @Override public void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        super.entityInside(state,level,pos,entity);
        if(state.getValue(FLUID)==3)GimmickBlocks.ACID_BLOCK.get().entityInside(GimmickBlocks.ACID_BLOCK.get().defaultBlockState(),level,pos,entity);
    }
}
