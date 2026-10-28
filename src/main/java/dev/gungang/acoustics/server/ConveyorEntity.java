package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.*;

public final class ConveyorEntity extends BlockEntity {
    int speed=10;
    boolean initialized;
    public ConveyorEntity(BlockPos p,BlockState s){super(MotionBlocks.CONVEYOR_ENTITY.get(),p,s);}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putInt("Speed",speed);tag.putBoolean("Initialized",initialized);}
    @Override public void load(CompoundTag tag){super.load(tag);speed=tag.contains("Speed")?MotionRules.speedStep(tag.getInt("Speed")):10;initialized=tag.getBoolean("Initialized");}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    void sync(){if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    public static void tick(Level level,BlockPos pos,BlockState state,ConveyorEntity belt){
        double top=pos.getY()+(state.getValue(ConveyorBlock.HALF)==Half.TOP?1:.5);
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(pos.getX(),top-.02,pos.getZ(),pos.getX()+1,top+.15,pos.getZ()+1))){
            if(entity.isSpectator()||entity.isPassenger()||!entity.onGround()||Math.abs(entity.getY()-top)>.08)continue;
            // Players are predicted by their owning client; server moves items and mobs once.
            if(level.isClientSide?!(entity instanceof Player p&&p.isLocalPlayer()):entity instanceof Player)continue;
            if(entity instanceof Player p&&p.getAbilities().flying)continue;
            if(net.minecraft.util.Mth.floor(entity.getX())!=pos.getX()||net.minecraft.util.Mth.floor(entity.getZ())!=pos.getZ())continue;
            var data=entity.getPersistentData();
            if(data.contains("GunGangBeltTick")&&data.getLong("GunGangBeltTick")==level.getGameTime())continue;
            data.putLong("GunGangBeltTick",level.getGameTime());
            var ports=ConveyorBlock.ports(state.getValue(ConveyorBlock.SHAPE));
            var out=ConveyorBlock.output(state);var in=ports[0]==out?ports[1]:ports[0];
            double x=entity.getX()-pos.getX()-.5,z=entity.getZ()-pos.getZ()-.5;
            double nearest=0,best=Double.MAX_VALUE;
            for(int i=0;i<=32;i++){double t=i/32d;var p=MotionRules.point(in.getStepX(),in.getStepZ(),out.getStepX(),out.getStepZ(),t);double d=(x-p[0])*(x-p[0])+(z-p[1])*(z-p[1]);if(d<best){best=d;nearest=t;}}
            double travel=.1*belt.speed/10d;
            // Advance along the tangent and gently center the rider, including on corners.
            double dx=-(1-nearest)*in.getStepX()+nearest*out.getStepX(),dz=-(1-nearest)*in.getStepZ()+nearest*out.getStepZ();
            double length=Math.sqrt(dx*dx+dz*dz);dx/=length;dz/=length;
            var center=MotionRules.point(in.getStepX(),in.getStepZ(),out.getStepX(),out.getStepZ(),nearest);
            double lateral=net.minecraft.util.Mth.clamp(-(center[0]-x)*dz+(center[1]-z)*dx,-travel*.5,travel*.5);
            entity.move(MoverType.SELF,new Vec3(dx*travel-dz*lateral,0,dz*travel+dx*lateral));
            entity.setOnGround(true);
        }
    }
}
