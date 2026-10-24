package dev.gungang.acoustics.server;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
public final class PuddleBlock extends Block {
    public final boolean acid;
    public PuddleBlock(boolean acid){super(Properties.of().strength(.1f).noCollission().noOcclusion().sound(net.minecraft.world.level.block.SoundType.WET_GRASS).lightLevel(s->acid?6:0));this.acid=acid;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(0,0,0,16,1,16);}
    @Override public void entityInside(BlockState s,Level l,BlockPos p,Entity e){splash(l,e,acid);}
    public static void splash(Level level,Entity entity){splash(level,entity,false);}
    /** Water puddles put out fire; acid puddles poison like acid fluid. */
    public static void splash(Level level,Entity entity,boolean acid){
        if(acid)AcidBlock.poison(level,entity);else if(!level.isClientSide)entity.clearFire();
        // Footsteps in water are a sound cue, for the bot's steps as much as a player's.
        if(level.isClientSide||!BotEntity.participant(entity))return;var p=entity;
        long now=level.getGameTime();var tag=p.getPersistentData();
        double dx=p.getX()-tag.getDouble("GunGangPuddleX"),dz=p.getZ()-tag.getDouble("GunGangPuddleZ");
        boolean moving=!tag.contains("GunGangPuddleSeen")||now-tag.getLong("GunGangPuddleSeen")>2||dx*dx+dz*dz>.0001;
        tag.putDouble("GunGangPuddleX",p.getX());tag.putDouble("GunGangPuddleZ",p.getZ());tag.putLong("GunGangPuddleSeen",now);
        if(!moving||(tag.contains("GunGangPuddleStep")&&now-tag.getLong("GunGangPuddleStep")<GimmickConfig.puddle.soundIntervalTicks()))return;
        tag.putLong("GunGangPuddleStep",now);if(p.isSilent())return;level.playSound(null,p.getX(),p.getY(),p.getZ(),GimmickBlocks.PUDDLE_STEP.get(),net.minecraft.sounds.SoundSource.PLAYERS,(float)GimmickConfig.puddle.soundVolume(),.9f+level.random.nextFloat()*.2f);
        ((net.minecraft.server.level.ServerLevel)level).sendParticles(acid?NozzleBlock.ACID_SPRAY:net.minecraft.core.particles.ParticleTypes.SPLASH,p.getX(),p.getY()+.04,p.getZ(),4,.15,.01,.15,.02);
    }
}
