package dev.gungang.acoustics.server;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
public final class AcidBlock extends LiquidBlock {
    public AcidBlock(){super(GimmickBlocks.ACID_SOURCE,Properties.copy(Blocks.WATER).lightLevel(s->12));}
    @Override public void entityInside(BlockState s,Level level,BlockPos pos,Entity entity){
        super.entityInside(s,level,pos,entity);
        if(!level.isClientSide&&entity instanceof net.minecraft.world.entity.player.Player p&&!p.isSpectator())p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,ExtraGadgetConfig.ticks(GimmickConfig.acid.poisonSeconds()),GimmickConfig.acid.amplifier()));
    }
}
