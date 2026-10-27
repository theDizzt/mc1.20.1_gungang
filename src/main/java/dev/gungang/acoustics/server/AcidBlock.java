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
        // Disguised prop-hunt hiders are immune.
        if(!level.isClientSide&&BotEntity.participant(entity)&&entity instanceof net.minecraft.world.entity.LivingEntity p
                &&!(p instanceof net.minecraft.world.entity.player.Player player&&PropDimensions.prop(player)!=null)){
            int amplifier=GimmickConfig.acid.amplifier();
            int interval=AcidPoisonEffect.interval(amplifier);
            int duration=Math.max(interval+1,ExtraGadgetConfig.ticks(GimmickConfig.acid.poisonSeconds()));
            var current=p.getEffect(GimmickBlocks.ACID_POISON.get());
            // Preserve the poison tick phase when refreshing while submerged.
            if(current!=null&&current.getAmplifier()==amplifier)
                duration-=Math.floorMod(duration-current.getDuration(),interval);
            // Multiple overlapping acid cells often contact the same entity in one tick.
            if(current!=null&&current.getAmplifier()==amplifier&&current.getDuration()>=duration)return;
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(GimmickBlocks.ACID_POISON.get(),duration,amplifier));
        }
    }
}
