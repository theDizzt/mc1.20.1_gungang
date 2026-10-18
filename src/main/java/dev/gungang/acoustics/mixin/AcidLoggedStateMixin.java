package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.AcidLogging;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds the acidlogged property to waterloggable fences, panes, iron bars and stairs. */
@Mixin(Block.class)
public abstract class AcidLoggedStateMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/StateDefinition$Builder;create(Ljava/util/function/Function;Lnet/minecraft/world/level/block/state/StateDefinition$Factory;)Lnet/minecraft/world/level/block/state/StateDefinition;"))
    private StateDefinition<Block, BlockState> gungang$addAcidLogged(StateDefinition.Builder<Block, BlockState> builder,
            Function<Block, BlockState> defaultState, StateDefinition.Factory<Block, BlockState> factory) {
        if (AcidLogging.supports((Block) (Object) this)) builder.add(AcidLogging.ACIDLOGGED);
        return builder.create(defaultState, factory);
    }

    // BooleanProperty lists true first, so states built from stateDefinition.any() would start acidlogged.
    @ModifyVariable(method = "registerDefaultState", at = @At("HEAD"), argsOnly = true)
    private BlockState gungang$dryDefault(BlockState state) {
        return state.hasProperty(AcidLogging.ACIDLOGGED) ? state.setValue(AcidLogging.ACIDLOGGED, false) : state;
    }
}
