package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block several cells large placed by one item. Part 0 is the anchor: it alone draws the whole model (its elements
 * reach into the neighbouring cells) and owns any block entity; the other parts are invisible and only collide.
 * Each part's shape is the block's outline, given in pixels relative to the anchor cell for a north-facing block,
 * cut to that part's cell. Prop disguises use the same parts (PropGeometry).
 */
public abstract class MultiPartBlock extends BaseEntityBlock {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,7);
    private final Map<BlockState,VoxelShape> shapes=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<BlockPos> removing=java.util.concurrent.ConcurrentHashMap.newKeySet();
    protected MultiPartBlock(Properties properties){super(properties);}

    public abstract int partCount();
    /** Cell of {@code part} relative to the anchor, before turning by FACING: x to the right (+x of the model), y up, z back. */
    protected abstract BlockPos cell(int part);
    /** The outline in model pixels relative to the anchor cell, facing north. */
    protected abstract List<AABB> outline();
    protected boolean faced(){return false;}

    public Direction facing(BlockState state){return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)?state.getValue(BlockStateProperties.HORIZONTAL_FACING):Direction.NORTH;}
    /** Offset of {@code part} from the anchor in the world, as the blockstate turns the model (y rotation, clockwise). */
    public BlockPos offset(int part,Direction facing){var c=cell(part);var v=turn(c.getX(),c.getZ(),facing);return new BlockPos((int)Math.round(v[0]),c.getY(),(int)Math.round(v[1]));}
    /** Model x/z (blocks or pixels/16) turned for {@code facing}: north is as modelled, east a quarter turn clockwise. */
    private static double[] turn(double x,double z,Direction facing){
        return switch(facing){case EAST->new double[]{-z,x};case SOUTH->new double[]{-x,-z};case WEST->new double[]{z,-x};default->new double[]{x,z};};
    }
    public BlockPos anchor(BlockPos pos,BlockState state){return pos.subtract(offset(state.getValue(PART),facing(state)));}
    public boolean isAnchor(BlockState state){return state.getValue(PART)==0;}

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(PART);if(faced())builder.add(BlockStateProperties.HORIZONTAL_FACING);}
    @Override public RenderShape getRenderShape(BlockState state){return isAnchor(state)?RenderShape.MODEL:RenderShape.INVISIBLE;}

    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return shapes.computeIfAbsent(state,s->{
            var off=offset(s.getValue(PART),facing(s));VoxelShape shape=Shapes.empty();
            for(var box:outline()){
                // Turn about the anchor cell's centre (as the blockstate turns the model), then move into this part's cell.
                var a=turn(box.minX/16-.5,box.minZ/16-.5,facing(s));var b=turn(box.maxX/16-.5,box.maxZ/16-.5,facing(s));
                double x1=Math.min(a[0],b[0])+.5-off.getX(),x2=Math.max(a[0],b[0])+.5-off.getX(),z1=Math.min(a[1],b[1])+.5-off.getZ(),z2=Math.max(a[1],b[1])+.5-off.getZ();
                double y1=box.minY/16-off.getY(),y2=box.maxY/16-off.getY();
                x1=Math.max(0,x1);z1=Math.max(0,z1);y1=Math.max(0,y1);x2=Math.min(1,x2);z2=Math.min(1,z2);y2=Math.min(1,y2);
                if(x2>x1&&y2>y1&&z2>z1)shape=Shapes.or(shape,Shapes.box(x1,y1,z1,x2,y2,z2));
            }
            return shape.optimize();
        });
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var state=defaultBlockState().setValue(PART,0);
        if(faced())state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,context.getHorizontalDirection().getOpposite());
        var level=context.getLevel();var root=context.getClickedPos();
        for(int part=1;part<partCount();part++){
            var pos=root.offset(offset(part,facing(state)));
            if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.getBlockState(pos).canBeReplaced(BlockPlaceContext.at(context,pos,context.getClickedFace())))return null;
        }
        return state;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);if(level.isClientSide)return;
        for(int part=1;part<partCount();part++)level.setBlock(pos.offset(offset(part,facing(state))),state.setValue(PART,part),3);
    }
    /** Breaking any part removes the whole block (only the anchor's loot table drops the item). */
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        super.onRemove(state,level,pos,next,moving);
        if(level.isClientSide||next.is(this))return;
        var root=anchor(pos,state).immutable();if(!removing.add(root))return;
        try{
            for(int part=0;part<partCount();part++){
                var at=root.offset(offset(part,facing(state)));if(at.equals(pos))continue;
                var other=level.getBlockState(at);
                if(other.is(this)&&other.getValue(PART)==part&&facing(other)==facing(state))level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            }
        }finally{removing.remove(root);}
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){return faced()?state.setValue(BlockStateProperties.HORIZONTAL_FACING,rotation.rotate(facing(state))):state;}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return faced()?state.rotate(mirror.getRotation(facing(state))):state;}
}
