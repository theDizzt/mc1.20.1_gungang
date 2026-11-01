package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.*;

/** Acid counterpart of vanilla waterlogging for fences, panes, iron bars and stairs (wired in by mixins). */
public final class AcidLogging {
    public static final BooleanProperty ACIDLOGGED=BooleanProperty.create("acidlogged");
    public static final BooleanProperty HOTLOGGED=BooleanProperty.create("hot_spring_logged");

    public static boolean supports(Block block){return block instanceof CrossCollisionBlock||block instanceof StairBlock||block instanceof SlabBlock;}
    public static boolean hot(BlockState state){return state.hasProperty(HOTLOGGED)&&state.getValue(HOTLOGGED);}
    private static boolean isHot(Fluid fluid){return HotSpringContent.SOURCE.get().isSame(fluid);}
    public static boolean acid(BlockState state){return state.hasProperty(ACIDLOGGED)&&state.getValue(ACIDLOGGED);}
    private static boolean isAcid(Fluid fluid){return GimmickBlocks.ACID_SOURCE.get().isSame(fluid);}

    public static BlockState placement(BlockState state,LevelReader level,BlockPos pos){
        if(state!=null&&state.hasProperty(BlockStateProperties.SLAB_TYPE)&&state.getValue(BlockStateProperties.SLAB_TYPE)==net.minecraft.world.level.block.state.properties.SlabType.DOUBLE)
            return state.setValue(ACIDLOGGED,false).setValue(HOTLOGGED,false);
        if(state!=null&&state.hasProperty(HOTLOGGED)&&isHot(level.getFluidState(pos).getType()))return state.setValue(HOTLOGGED,true).setValue(ACIDLOGGED,false).setValue(BlockStateProperties.WATERLOGGED,false);
        return state!=null&&state.hasProperty(ACIDLOGGED)&&isAcid(level.getFluidState(pos).getType())?state.setValue(ACIDLOGGED,true):state;
    }

    public static FluidState fluid(BlockState state){return hot(state)?HotSpringContent.SOURCE.get().getSource(false):acid(state)?GimmickBlocks.ACID_SOURCE.get().getSource(false):null;}

    public static void scheduleTick(BlockState state,LevelAccessor level,BlockPos pos){
        if(hot(state)){var fluid=HotSpringContent.SOURCE.get();level.scheduleTick(pos,fluid,fluid.getTickDelay(level));}
        if(acid(state)){var fluid=GimmickBlocks.ACID_SOURCE.get();level.scheduleTick(pos,fluid,fluid.getTickDelay(level));}
    }

    public static boolean canPlace(BlockState state,Fluid fluid){
        if(state.hasProperty(BlockStateProperties.SLAB_TYPE)&&state.getValue(BlockStateProperties.SLAB_TYPE)==net.minecraft.world.level.block.state.properties.SlabType.DOUBLE)return false;
        if(hot(state))return false;
        if(isHot(fluid))return !state.getValue(BlockStateProperties.WATERLOGGED)&&!acid(state);
        if(fluid==Fluids.WATER)return !acid(state);
        return isAcid(fluid)&&!state.getValue(BlockStateProperties.WATERLOGGED)&&!acid(state);
    }

    public static boolean place(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){
        if(!canPlace(state,fluid.getType())||state.getValue(BlockStateProperties.WATERLOGGED))return false;
        if(!level.isClientSide()){
            level.setBlock(pos,state.setValue(isHot(fluid.getType())?HOTLOGGED:isAcid(fluid.getType())?ACIDLOGGED:BlockStateProperties.WATERLOGGED,true),3);
            level.scheduleTick(pos,fluid.getType(),fluid.getType().getTickDelay(level));
        }
        return true;
    }

    public static ItemStack pickup(LevelAccessor level,BlockPos pos,BlockState state){
        if(hot(state)){
            level.setBlock(pos,state.setValue(HOTLOGGED,false),3);
            if(!state.canSurvive(level,pos))level.destroyBlock(pos,true);
            return new ItemStack(HotSpringContent.BUCKET.get());
        }
        boolean acid=acid(state);
        if(!acid&&!state.getValue(BlockStateProperties.WATERLOGGED))return ItemStack.EMPTY;
        level.setBlock(pos,state.setValue(acid?ACIDLOGGED:BlockStateProperties.WATERLOGGED,false),3);
        if(!state.canSurvive(level,pos))level.destroyBlock(pos,true);
        return new ItemStack(acid?GimmickBlocks.ACID_BUCKET.get():Items.WATER_BUCKET);
    }

    public static void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(hot(state)&&entity instanceof net.minecraft.world.entity.LivingEntity living)HotSpringEffect.touch(living);
        if(acid(state))GimmickBlocks.ACID_BLOCK.get().entityInside(GimmickBlocks.ACID_BLOCK.get().defaultBlockState(),level,pos,entity);
    }

    private AcidLogging(){}
}
