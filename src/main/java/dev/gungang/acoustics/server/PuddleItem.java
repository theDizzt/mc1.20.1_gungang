package dev.gungang.acoustics.server;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
public final class PuddleItem extends BlockItem {
    public PuddleItem(){super(GimmickBlocks.PUDDLE.get(),new Properties());}
    @Override public InteractionResult useOn(UseOnContext c){
        var level=c.getLevel();var pos=c.getClickedPos();var state=level.getBlockState(pos);var p=c.getPlayer();
        boolean overlap=state.is(net.minecraft.tags.BlockTags.FENCES)||state.getBlock() instanceof net.minecraft.world.level.block.AbstractGlassBlock||state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
        if(!overlap)return super.useOn(c);
        if(p==null||!level.mayInteract(p,pos)||!p.mayUseItemAt(pos,c.getClickedFace(),c.getItemInHand()))return InteractionResult.FAIL;
        if(!level.getEntitiesOfClass(PuddleOverlay.class,new net.minecraft.world.phys.AABB(pos)).isEmpty())return InteractionResult.FAIL;
        if(!level.isClientSide){var entity=GimmickBlocks.PUDDLE_OVERLAY.get().create(level);entity.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);if(!level.addFreshEntity(entity))return InteractionResult.FAIL;if(!p.isCreative())c.getItemInHand().shrink(1);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
