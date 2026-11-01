package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class HologramBlock extends BaseEntityBlock {
    public HologramBlock(){super(Properties.copy(Blocks.IRON_BLOCK).strength(2,6).noOcclusion().lightLevel(s->7));}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(1,0,1,15,6,15);}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new HologramEntity(p,s);}
    @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player.isSpectator())return InteractionResult.FAIL;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!(level.getBlockEntity(pos) instanceof HologramEntity be))return InteractionResult.PASS;
        player.openMenu(be);
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState s,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!s.is(next.getBlock())&&!level.isClientSide&&level.getBlockEntity(pos) instanceof HologramEntity be){
            var stack=be.replace(ItemStack.EMPTY);if(!stack.isEmpty())Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);
        }
        super.onRemove(s,level,pos,next,moved);
    }
}
