package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class GimmickEntity extends BlockEntity {
    private double health=-1;
    private long readyAt,generation=-1;
    public GimmickEntity(BlockPos p,BlockState s){super(GimmickBlocks.ENTITY.get(),p,s);}
    public static GimmickEntity at(Level level,BlockPos p){var s=level.getBlockState(p);if(s.getBlock() instanceof GimmickBlock&&s.getValue(GimmickBlock.TOP))p=p.below();return level.getBlockEntity(p) instanceof GimmickEntity e?e:null;}
    public static void hit(Level level,BlockPos p,float amount){var e=at(level,p);if(e!=null)e.damage(amount);}
    public double health(){return health;}
    private double maximum(){return GimmickConfig.walls.getOrDefault(((GimmickBlock)getBlockState().getBlock()).id,new GimmickConfig.Wall(1)).health();}
    private void active(boolean active){
        var s=getBlockState();level.setBlock(worldPosition,s.setValue(GimmickBlock.ACTIVE,active),3);
        if(((GimmickBlock)s.getBlock()).id.equals("gas_barrel")&&level.getBlockState(worldPosition.above()).is(s.getBlock()))level.setBlock(worldPosition.above(),s.setValue(GimmickBlock.TOP,true).setValue(GimmickBlock.ACTIVE,active),3);
        setChanged();
    }
    public void damage(float amount){
        if(!(level instanceof ServerLevel server)||!getBlockState().getValue(GimmickBlock.ACTIVE)||amount<=0)return;
        if(generation<0)generation=GimmickEpoch.get(server).generation;
        var block=(GimmickBlock)getBlockState().getBlock();if(block instanceof NozzleBlock)return;
        if(block.wall()){
            if(health<0)health=maximum();health-=amount;setChanged();
            if(health<=0){server.levelEvent(2001,worldPosition,net.minecraft.world.level.block.Block.getId(getBlockState()));active(false);}return;
        }
        var c=block.id.equals("uxo")?GimmickConfig.uxo:GimmickConfig.gas;
        active(false);readyAt=server.getGameTime()+ExtraGadgetConfig.ticks(c.respawnSeconds());setChanged();
        var center=Vec3.atCenterOf(worldPosition);
        for(var p:server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(center,center).inflate(c.radius()),BotEntity::participant))
            if(p.position().distanceToSqr(center)<=c.radius()*c.radius()&&!SpawnGateProtection.shields(p,center))OrdnanceDamage.hurt(p,server.damageSources().explosion(null,null),(float)c.damage());
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,center.x,center.y,center.z,1,0,0,0,0);
        server.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.BLOCKS,2,1);
    }
    public static void tick(Level world,BlockPos pos,BlockState state,GimmickEntity e){
        if(state.getValue(GimmickBlock.TOP)||!(world instanceof ServerLevel level))return;
        long epoch=GimmickEpoch.get(level).generation;
        if(e.generation<0)e.generation=epoch;
        if(e.generation!=epoch){e.generation=epoch;e.health=e.maximum();e.readyAt=0;e.active(true);}
        if(e.health<0){e.health=e.maximum();e.setChanged();}
        if(!e.getBlockState().getValue(GimmickBlock.ACTIVE)){if(e.readyAt>0&&level.getGameTime()>=e.readyAt){e.readyAt=0;e.health=e.maximum();e.active(true);}return;}
        var id=((GimmickBlock)state.getBlock()).id;
        if(id.equals("uxo")&&!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+.3,pos.getZ()+1),BotEntity::participant).isEmpty())e.damage(1);
        if(state.getBlock() instanceof NozzleBlock nozzle)nozzle.spray(level,pos);
    }
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);t.putDouble("Health",health);t.putLong("ReadyAt",readyAt);t.putLong("Generation",generation);}
    @Override public void load(CompoundTag t){super.load(t);health=t.contains("Health")?t.getDouble("Health"):-1;readyAt=t.getLong("ReadyAt");generation=t.contains("Generation")?t.getLong("Generation"):-1;}
}
