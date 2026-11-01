package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public final class HotSpringBlock extends LiquidBlock {
    public HotSpringBlock(){super(HotSpringContent.SOURCE,Properties.copy(Blocks.WATER));}
    @Override public void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){super.entityInside(state,level,pos,entity);if(entity instanceof LivingEntity living)HotSpringEffect.touch(living);}
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random){
        if(level.getFluidState(pos.above()).isEmpty()&&random.nextInt(3)==0)
            level.addParticle(ParticleTypes.CLOUD,pos.getX()+random.nextDouble(),pos.getY()+state.getFluidState().getHeight(level,pos)+.05,pos.getZ()+random.nextDouble(),0,.025,0);
    }
}
