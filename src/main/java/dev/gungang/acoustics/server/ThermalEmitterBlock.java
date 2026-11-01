package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

public final class ThermalEmitterBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,8);
    public final String kind;
    public ThermalEmitterBlock(String kind){super(Properties.copy(Blocks.STONE).strength(2,6).noOcclusion());this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,4));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,PART);}
    public boolean geyser(){return kind.equals("geyser");}
    public static BlockPos center(BlockPos pos,BlockState state){int part=state.getValue(PART);return pos.offset(1-part%3,0,1-part/3);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        if(geyser())for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var pos=c.getClickedPos().offset(x,0,z);
            if(!c.getLevel().getWorldBorder().isWithinBounds(pos)||!c.getLevel().getBlockState(pos).canBeReplaced())return null;
        }
        return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        if(geyser()&&!level.isClientSide)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)level.setBlock(pos.offset(x,0,z),state.setValue(PART,(z+1)*3+x+1),3);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        super.onRemove(state,level,pos,next,moving);
        if(geyser()&&!next.is(this)&&!level.isClientSide){
            var center=center(pos,state);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                var other=center.offset(x,0,z);if(other.equals(pos))continue;var s=level.getBlockState(other);
                if(s.is(this)&&center(other,s).equals(center))level.destroyBlock(other,s.getValue(PART)==4);
            }
        }
    }
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return geyser()?Block.box(0,0,0,16,4,16):kind.equals("bath_toad")?Block.box(1,0,1,15,13,15):Block.box(4,0,4,12,12,12);}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return geyser()&&s.getValue(PART)!=4?null:new ThermalEmitterEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return l.isClientSide?null:createTickerHelper(type,HotSpringContent.ENTITY.get(),ThermalEmitterEntity::tick);}
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
}
