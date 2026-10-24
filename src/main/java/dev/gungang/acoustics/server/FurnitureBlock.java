package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** One inventory item places a complete, rotatable station; every slice belongs to its anchor. */
public final class FurnitureBlock extends StationBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,8);
    private final Set<GlobalPos> removing=new HashSet<>();
    private final VoxelShape[][] shapes;
    public FurnitureBlock(Kind kind){
        super(kind);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(PART,anchorPart()));
        shapes=loadShapes(kind==Kind.WAR_TABLE?"war_table":"armory");
    }
    public int partCount(){return kind==Kind.WAR_TABLE?9:6;}
    public int anchorPart(){return kind==Kind.WAR_TABLE?4:0;}
    public int turns(Direction facing){return Math.floorMod(facing.get2DDataValue()+2+(kind==Kind.ARMORY?3:0),4);}
    public BlockPos offset(int part,Direction facing){
        int x=kind==Kind.WAR_TABLE?part%3-1:0,z=kind==Kind.WAR_TABLE?part/3-1:part%2;
        int y=kind==Kind.WAR_TABLE?0:part/2;
        for(int i=0;i<turns(facing);i++){int old=x;x=-z;z=old;}
        return new BlockPos(x,y,z);
    }
    public BlockPos anchor(BlockPos pos,BlockState state){return pos.subtract(offset(state.getValue(PART),state.getValue(FACING)));}
    public boolean isAnchor(BlockState state){return state.getValue(PART)==anchorPart();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(FACING,PART);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var state=defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
        var level=context.getLevel();var root=context.getClickedPos();
        for(int part=0;part<partCount();part++){
            var pos=root.offset(offset(part,state.getValue(FACING)));
            if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.hasChunkAt(pos)
                    ||!level.getBlockState(pos).canBeReplaced(BlockPlaceContext.at(context,pos,context.getClickedFace()))
                    ||!level.isUnobstructed(state.setValue(PART,part),pos,CollisionContext.empty()))return null;
        }
        return state;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);if(level.isClientSide)return;
        for(int part=0;part<partCount();part++)if(part!=anchorPart())level.setBlock(pos.offset(offset(part,state.getValue(FACING))),state.setValue(PART,part),3);
    }
    @Override public BlockEntity interactionEntity(Level level,BlockPos pos,BlockState state){
        var root=anchor(pos,state);var rootState=level.getBlockState(root);
        return rootState.is(this)&&isAnchor(rootState)&&rootState.getValue(FACING)==state.getValue(FACING)?level.getBlockEntity(root):null;
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        super.onRemove(state,level,pos,next,moving);
        if(level.isClientSide||next.is(this))return;
        var root=anchor(pos,state);var key=GlobalPos.of(level.dimension(),root);if(!removing.add(key))return;
        try {
            for(int part=0;part<partCount();part++){
                var at=root.offset(offset(part,state.getValue(FACING)));if(at.equals(pos))continue;
                var other=level.getBlockState(at);
                if(other.is(this)&&other.getValue(PART)==part&&other.getValue(FACING)==state.getValue(FACING))level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            }
        }finally{removing.remove(key);}
    }
    // Every slice derives its offset from FACING, so turning it turns the whole station (prop R key, structures).
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapes[Math.min(partCount()-1,state.getValue(PART))][turns(state.getValue(FACING))];}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    private static VoxelShape[][] loadShapes(String id){
        try(var input=FurnitureBlock.class.getResourceAsStream("/data/gungang/stations/"+id+".json")){
            if(input==null)throw new IllegalStateException("Missing furniture shapes: "+id);
            var parts=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            var result=new VoxelShape[parts.size()][4];
            for(int part=0;part<parts.size();part++)for(int turn=0;turn<4;turn++){
                var shape=Shapes.empty();
                for(var value:parts.get(part).getAsJsonArray()){
                    var box=value.getAsJsonArray();double x0=box.get(0).getAsDouble(),y0=box.get(1).getAsDouble(),z0=box.get(2).getAsDouble();
                    double x1=box.get(3).getAsDouble(),y1=box.get(4).getAsDouble(),z1=box.get(5).getAsDouble();
                    for(int t=0;t<turn;t++){double old0=x0,old1=x1;x0=16-z1;x1=16-z0;z0=old0;z1=old1;}
                    shape=Shapes.or(shape,Block.box(x0,y0,z0,x1,y1,z1));
                }
                result[part][turn]=shape.optimize();
            }
            return result;
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read furniture shapes",failure);}
    }
}
