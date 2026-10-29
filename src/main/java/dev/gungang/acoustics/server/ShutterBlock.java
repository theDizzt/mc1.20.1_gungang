package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Coplanar tiles form one rolling shutter. The normal axis separates parallel/orthogonal doors. */
public final class ShutterBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    public static final double MIN=6.5/16,MAX=9.5/16,RAIL=1/16d,HEADER=2/16d;
    public ShutterBlock(){
        super(Properties.copy(Blocks.IRON_BLOCK).strength(3,6).noOcclusion().dynamicShape().isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
        registerDefaultState(stateDefinition.any().setValue(AXIS,Direction.Axis.Z));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(AXIS);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        var axis=c.getHorizontalDirection().getAxis();
        // Extending a tile by clicking its edge inherits its plane, regardless of the player's camera angle.
        var neighbor=c.getClickedPos().relative(c.getClickedFace().getOpposite());var state=c.getLevel().getBlockState(neighbor);
        if(state.getBlock() instanceof ShutterBlock&&c.getClickedFace().getAxis()!=state.getValue(AXIS))axis=state.getValue(AXIS);
        return defaultBlockState().setValue(AXIS,axis);
    }
    public static Direction[] connections(BlockState s){return s.getValue(AXIS)==Direction.Axis.Z?new Direction[]{Direction.DOWN,Direction.UP,Direction.WEST,Direction.EAST}:new Direction[]{Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH};}
    public static boolean joined(BlockGetter l,BlockPos p,BlockState s,Direction d){
        if(d.getAxis()==s.getValue(AXIS))return false;
        var other=l.getBlockState(p.relative(d));return other.getBlock() instanceof ShutterBlock&&other.getValue(AXIS)==s.getValue(AXIS);
    }
    public static VoxelShape panel(BlockState s,double x1,double y1,double x2,double y2){
        if(y2<=y1||x2<=x1)return Shapes.empty();
        return s.getValue(AXIS)==Direction.Axis.Z?Shapes.box(x1,y1,MIN,x2,y2,MAX):Shapes.box(MIN,y1,x1,MAX,y2,x2);
    }
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return panel(s,0,0,1,1);}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        var be=l.getBlockEntity(p);double bottom=be instanceof ShutterEntity shutter?shutter.localBottom(0):0;
        var shape=panel(s,0,bottom,1,1);var directions=connections(s);
        if(!joined(l,p,s,directions[2]))shape=Shapes.or(shape,panel(s,0,0,RAIL,1));
        if(!joined(l,p,s,directions[3]))shape=Shapes.or(shape,panel(s,1-RAIL,0,1,1));
        if(!joined(l,p,s,Direction.UP))shape=Shapes.or(shape,panel(s,0,1-HEADER,1,1));
        return shape;
    }
    @Override public VoxelShape getOcclusionShape(BlockState s,BlockGetter l,BlockPos p){return Shapes.empty();}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override public void neighborChanged(BlockState s,Level l,BlockPos p,Block b,BlockPos from,boolean moved){if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override public BlockState updateShape(BlockState s,Direction d,BlockState other,LevelAccessor l,BlockPos p,BlockPos adjacent){l.scheduleTick(p,this,1);return s;}
    @Override public void tick(BlockState s,ServerLevel l,BlockPos p,net.minecraft.util.RandomSource random){ShutterEntity.refresh(l,p);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new ShutterEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide?null:createTickerHelper(t,ShutterBlocks.ENTITY.get(),ShutterEntity::tick);}
    @Override public BlockState rotate(BlockState s,Rotation r){return r==Rotation.CLOCKWISE_90||r==Rotation.COUNTERCLOCKWISE_90?s.setValue(AXIS,s.getValue(AXIS)==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X):s;}
}
