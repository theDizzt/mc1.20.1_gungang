package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

public final class ConveyorBlock extends BaseEntityBlock {
    public static final EnumProperty<RailShape> SHAPE=EnumProperty.create("shape",RailShape.class,s->!s.isAscending());
    public static final EnumProperty<Half> HALF=BlockStateProperties.HALF;
    public static final BooleanProperty REVERSED=BooleanProperty.create("reversed");
    private static final VoxelShape BOTTOM=Block.box(0,0,0,16,8,16),TOP=Block.box(0,8,0,16,16,16);
    public ConveyorBlock(){super(Properties.copy(Blocks.IRON_BLOCK).strength(2).noOcclusion());registerDefaultState(stateDefinition.any().setValue(SHAPE,RailShape.NORTH_SOUTH).setValue(HALF,Half.BOTTOM).setValue(REVERSED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(SHAPE,HALF,REVERSED);}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return s.getValue(HALF)==Half.TOP?TOP:BOTTOM;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        return defaultBlockState().setValue(HALF,c.getClickedFace()==Direction.DOWN||(c.getClickedFace()!=Direction.UP&&c.getClickLocation().y-c.getClickedPos().getY()>.5)?Half.TOP:Half.BOTTOM)
                .setValue(SHAPE,c.getHorizontalDirection().getAxis()==Direction.Axis.X?RailShape.EAST_WEST:RailShape.NORTH_SOUTH);
    }
    // A solid half block, deliberately not a SlabBlock: a second belt never creates a double slab.
    @Override public boolean canBeReplaced(BlockState s,BlockPlaceContext c){return false;}
    public static Direction[] ports(RailShape s){return switch(s){
        case EAST_WEST->new Direction[]{Direction.WEST,Direction.EAST};
        case NORTH_EAST->new Direction[]{Direction.NORTH,Direction.EAST};
        case NORTH_WEST->new Direction[]{Direction.NORTH,Direction.WEST};
        case SOUTH_EAST->new Direction[]{Direction.SOUTH,Direction.EAST};
        case SOUTH_WEST->new Direction[]{Direction.SOUTH,Direction.WEST};
        default->new Direction[]{Direction.NORTH,Direction.SOUTH};};}
    public static Direction output(BlockState s){return ports(s.getValue(SHAPE))[s.getValue(REVERSED)?0:1];}
    public static boolean hasPort(BlockState s,Direction d){return s.getBlock() instanceof ConveyorBlock&&Arrays.asList(ports(s.getValue(SHAPE))).contains(d);}
    public static boolean sameHeight(BlockState a,BlockState b){return b.getBlock() instanceof ConveyorBlock&&a.getValue(HALF)==b.getValue(HALF);}
    @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override public void neighborChanged(BlockState s,Level l,BlockPos p,Block b,BlockPos from,boolean moved){if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        var available=new ArrayList<Direction>();
        for(var d:Direction.Plane.HORIZONTAL){
            var next=pos.relative(d);if(!level.hasChunkAt(next))continue;
            var s=level.getBlockState(next);if(!sameHeight(state,s))continue;
            int count=0;for(var port:ports(s.getValue(SHAPE))){var other=next.relative(port);if(level.hasChunkAt(other)&&sameHeight(s,level.getBlockState(other))&&hasPort(level.getBlockState(other),port.getOpposite()))count++;}
            if(hasPort(s,d.getOpposite())||count<2)available.add(d);
        }
        RailShape chosen=state.getValue(SHAPE);int best=-1;
        // Existing shape wins ties, so parallel tracks and T junctions do not continually rewire.
        var candidates=new ArrayList<RailShape>();candidates.add(chosen);for(var s:SHAPE.getPossibleValues())if(s!=chosen)candidates.add(s);
        for(var shape:candidates){int score=0;for(var d:ports(shape))if(available.contains(d))score++;if(score>best){chosen=shape;best=score;}}
        var updated=state.setValue(SHAPE,chosen);
        if(level.getBlockEntity(pos) instanceof ConveyorEntity belt&&!belt.initialized){
            for(var d:ports(chosen)){
                var neighbor=level.getBlockState(pos.relative(d));
                if(sameHeight(updated,neighbor)&&hasPort(neighbor,d.getOpposite())&&level.getBlockEntity(pos.relative(d)) instanceof ConveyorEntity other&&other.initialized){
                    belt.speed=other.speed;
                    Direction exit=output(neighbor)==d.getOpposite()?Arrays.stream(ports(chosen)).filter(x->x!=d).findFirst().orElseThrow():d;
                    updated=updated.setValue(REVERSED,ports(chosen)[0]==exit);break;
                }
            }
            belt.initialized=true;belt.setChanged();
        }
        if(!updated.equals(state))level.setBlock(pos,updated,3);
        if(level.getBlockEntity(pos) instanceof ConveyorEntity belt)belt.sync();
    }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new ConveyorEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return createTickerHelper(t,MotionBlocks.CONVEYOR_ENTITY.get(),ConveyorEntity::tick);}
    @Override public BlockState rotate(BlockState s,Rotation r){return transform(s,r::rotate);}
    @Override public BlockState mirror(BlockState s,Mirror m){return transform(s,m::mirror);}
    private BlockState transform(BlockState s,java.util.function.UnaryOperator<Direction> f){
        var pair=ports(s.getValue(SHAPE));var a=f.apply(pair[0]);var b=f.apply(pair[1]);var out=f.apply(output(s));
        for(var shape:SHAPE.getPossibleValues()){var p=ports(shape);if(Arrays.asList(p).containsAll(List.of(a,b)))return s.setValue(SHAPE,shape).setValue(REVERSED,p[0]==out);}
        return s;
    }
}
