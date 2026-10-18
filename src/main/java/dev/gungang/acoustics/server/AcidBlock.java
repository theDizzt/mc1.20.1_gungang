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
        poison(level,entity);
    }
    public static void poison(Level level,Entity entity){
        if(!level.isClientSide&&entity instanceof net.minecraft.world.entity.player.Player p&&!p.isSpectator()&&PropDimensions.prop(p)==null){ // disguised hiders are immune
            int amplifier=GimmickConfig.acid.amplifier();
            int interval=Math.max(1,25>>amplifier);
            int duration=Math.max(interval+1,ExtraGadgetConfig.ticks(GimmickConfig.acid.poisonSeconds()));
            var current=p.getEffect(net.minecraft.world.effect.MobEffects.POISON);
            // Preserve the poison tick phase when refreshing while submerged.
            if(current!=null&&current.getAmplifier()==amplifier)
                duration-=Math.floorMod(duration-current.getDuration(),interval);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,duration,amplifier));
        }
    }
}
