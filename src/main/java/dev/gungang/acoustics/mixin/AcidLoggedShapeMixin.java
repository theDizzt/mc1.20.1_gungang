package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.AcidLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Placing into acid source acidlogs the block, and acidlogged blocks keep their acid ticking like water. */
@Mixin({FenceBlock.class, IronBarsBlock.class, StairBlock.class})
public abstract class AcidLoggedShapeMixin {
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void gungang$placeInAcid(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(AcidLogging.placement(cir.getReturnValue(), context.getLevel(), context.getClickedPos()));
    }

    @Inject(method = "updateShape", at = @At("HEAD"))
    private void gungang$tickAcid(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
        AcidLogging.scheduleTick(state, level, pos);
    }
}
