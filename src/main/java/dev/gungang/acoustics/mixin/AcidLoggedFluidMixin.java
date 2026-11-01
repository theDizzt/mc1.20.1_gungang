package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.AcidLogging;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fluid behaviour for acidlogged blocks; replaces the water-only SimpleWaterloggedBlock defaults. */
@Mixin({CrossCollisionBlock.class, StairBlock.class, net.minecraft.world.level.block.SlabBlock.class})
public abstract class AcidLoggedFluidMixin extends Block implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
    private AcidLoggedFluidMixin(Properties properties) { super(properties); }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        super.animateTick(state,level,pos,random);
        if(AcidLogging.hot(state)&&level.getFluidState(pos.above()).isEmpty()&&random.nextInt(3)==0)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,pos.getX()+random.nextDouble(),pos.getY()+1.05,pos.getZ()+random.nextDouble(),0,.025,0);
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void gungang$acidFluid(BlockState state, CallbackInfoReturnable<FluidState> cir) {
        var fluid = AcidLogging.fluid(state);
        if (fluid != null) cir.setReturnValue(fluid);
    }

    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return AcidLogging.canPlace(state, fluid);
    }

    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return AcidLogging.place(level, pos, state, fluid);
    }

    public ItemStack pickupBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        return AcidLogging.pickup(level, pos, state);
    }

    public Optional<SoundEvent> getPickupSound(BlockState state) {
        return state.getFluidState().getType().getPickupSound();
    }

    @Override public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return AcidLogging.acid(state) ? 12 : super.getLightEmission(state, level, pos);
    }

    @SuppressWarnings("deprecation")
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        AcidLogging.entityInside(state, level, pos, entity);
    }
}
