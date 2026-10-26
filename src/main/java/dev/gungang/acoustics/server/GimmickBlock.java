package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public class GimmickBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active"),TOP=BooleanProperty.create("top");
    public final String id;
    public GimmickBlock(String id){super(Properties.of().strength(-1,3600000).noOcclusion().isViewBlocking((s,l,p)->false).isSuffocating((s,l,p)->false).sound(id.contains("glass")?SoundType.GLASS:id.contains("planks")?SoundType.WOOD:SoundType.METAL));this.id=id;registerDefaultState(stateDefinition.any().setValue(ACTIVE,true).setValue(TOP,false));}
    public boolean wall(){return id.startsWith("cracked_");}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(ACTIVE,TOP);}
    @Override public RenderShape getRenderShape(BlockState s){return s.getValue(ACTIVE)?RenderShape.MODEL:RenderShape.INVISIBLE;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        if(!s.getValue(ACTIVE))return Shapes.empty();
        return switch(id){case "uxo"->box(3,0,3,13,2,13);case "gas_barrel"->box(2,0,2,14,s.getValue(TOP)?8:16,14);case "nozzle","acid_nozzle"->box(6,0,6,10,16,10);case "cracked_glass_pane"->Shapes.or(box(7,0,0,9,16,16),box(0,0,7,16,16,9));default->Shapes.block();};
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return id.equals("gas_barrel")&&(!c.getLevel().getBlockState(c.getClickedPos().above()).canBeReplaced(c)||c.getClickedPos().getY()+1>=c.getLevel().getMaxBuildHeight())?null:defaultBlockState();}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState s,LivingEntity who,net.minecraft.world.item.ItemStack stack){if(id.equals("gas_barrel"))level.setBlock(pos.above(),s.setValue(TOP,true),3);}
    @Override public void onRemove(BlockState s,Level level,BlockPos p,BlockState next,boolean moving){
        if(!next.is(this)&&id.equals("gas_barrel")){var other=s.getValue(TOP)?p.below():p.above();if(level.getBlockState(other).is(this))level.setBlock(other,Blocks.AIR.defaultBlockState(),3);}
        super.onRemove(s,level,p,next,moving);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new GimmickEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState s,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,GimmickBlocks.ENTITY.get(),GimmickEntity::tick);}
    @Override public void onProjectileHit(Level level,BlockState state,BlockHitResult hit,Projectile projectile){
        if(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType()).getNamespace().equals("tacz"))return;
        if(projectile instanceof HandAxeEntity){
            if(!level.isClientSide&&!SafeZones.contains(projectile.getOwner()))GimmickEntity.hit(level,hit.getBlockPos(),(float)(ExtraGadgetConfig.handAxe.throwDamage()*AdrenalineDamage.multiplier(projectile.getOwner())));
            return;
        }
        if(!level.isClientSide)GimmickEntity.hit(level,hit.getBlockPos(),projectile instanceof AbstractArrow arrow?(float)Math.max(1,arrow.getBaseDamage()*arrow.getDeltaMovement().length()):1);
    }
    @Override public void onBlockExploded(BlockState state,Level level,BlockPos pos,Explosion explosion){GimmickEntity.hit(level,pos,100);}
    @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState s){return net.minecraft.world.level.material.PushReaction.BLOCK;}
}
