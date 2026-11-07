package dev.gungang.acoustics.server;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** The gas barrel's drum has a front (labels), so it turns to face whoever places it; the top half copies the facing. */
public final class GasBarrelBlock extends GimmickBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public GasBarrelBlock(){super("gas_barrel");}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        var state=super.getStateForPlacement(c);
        return state==null?null:state.setValue(FACING,c.getHorizontalDirection().getOpposite());
    }
}
