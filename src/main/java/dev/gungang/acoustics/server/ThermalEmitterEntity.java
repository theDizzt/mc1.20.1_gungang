package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class ThermalEmitterEntity extends BlockEntity {
    private long eruptUntil,nextEruption;
    public ThermalEmitterEntity(BlockPos p,BlockState s){super(HotSpringContent.ENTITY.get(),p,s);}
    public static AABB plume(BlockPos pos){return new AABB(pos.getX()-1,pos.getY(),pos.getZ()-1,pos.getX()+2,pos.getY()+8,pos.getZ()+2);}
    public static void tick(Level world,BlockPos pos,BlockState state,ThermalEmitterEntity be){
        if(!(world instanceof ServerLevel level))return;
        long now=level.getGameTime();if(now%2!=0)return;
        var nearby=nearby(level,new AABB(pos).inflate(1,8,1));
        if(nearby.isEmpty())return;
        var block=(ThermalEmitterBlock)state.getBlock();
        if(block.geyser()){
            var bounds=plume(pos);
            var trigger=bounds.inflate(3,0,3);
            if(now>=be.nextEruption&&nearby.stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&p.getBoundingBox().intersects(trigger))){
                be.eruptUntil=now+60;be.nextEruption=now+140;
                level.playSound(null,pos,net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,1.2f,.65f);
            }
            if(now<be.eruptUntil){
                geyser(level,new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5),p->true,nearby);
            }else if(now%10==0)level.sendParticles(ParticleTypes.CLOUD,pos.getX()+.5,pos.getY()+.3,pos.getZ()+.5,2,.4,.1,.4,.02);
        }else{
            var direction=state.getValue(ThermalEmitterBlock.FACING);var forward=Vec3.atLowerCornerOf(direction.getNormal());
            var start=Vec3.atBottomCenterOf(pos).add(0,block.kind.equals("bath_toad")?.42:.65,0).add(forward.scale(.48));
            spray(level,start,start.add(forward.scale(3)).add(0,-.35,0),false,p->true,nearby);
        }
    }
    static void spray(ServerLevel level,Vec3 start,Vec3 end,boolean scald){spray(level,start,end,scald,p->true);}
    private static java.util.List<ServerPlayer> nearby(ServerLevel level,AABB streams){
        // Include spectators as particle viewers; damage/healing still uses the original eligibility checks.
        return level.getEntitiesOfClass(ServerPlayer.class,streams.inflate(48),p->true);
    }
    static void geyser(ServerLevel level,Vec3 base,java.util.function.Predicate<net.minecraft.world.entity.player.Player> affected){
        geyser(level,base,affected,nearby(level,new AABB(base.add(-1,0,-1),base.add(1,8,1))));
    }
    private static void geyser(ServerLevel level,Vec3 base,java.util.function.Predicate<net.minecraft.world.entity.player.Player> affected,java.util.List<ServerPlayer> nearby){
        if(nearby.isEmpty())return;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var start=base.add(x,.3,z);
            spray(level,start,start.add(0,7.7,0),true,affected,nearby);
        }
    }
    /** Only players accepted by the filter are scalded or healed; the stream is drawn for everyone nearby. */
    static void spray(ServerLevel level,Vec3 start,Vec3 end,boolean scald,java.util.function.Predicate<net.minecraft.world.entity.player.Player> affected){
        spray(level,start,end,scald,affected,nearby(level,new AABB(start,end)));
    }
    private static void spray(ServerLevel level,Vec3 start,Vec3 end,boolean scald,java.util.function.Predicate<net.minecraft.world.entity.player.Player> affected,java.util.List<ServerPlayer> nearby){
        if(nearby.isEmpty())return;
        var hit=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null));
        if(hit.getType()==HitResult.Type.BLOCK)end=hit.getLocation();
        var delta=end.subtract(start);double length=delta.length();if(length<.05)return;
        if(level.getGameTime()%4==0){
            var packet=new GungangNetwork.ThermalBurst(start,end,scald);
            for(var viewer:nearby)if(viewer.position().distanceToSqr(start)<48*48)GungangNetwork.send(viewer,packet);
        }
        var area=new AABB(start,end).inflate(.45);
        for(var player:nearby){
            if(!player.isAlive()||player.isSpectator()||!affected.test(player)||!player.getBoundingBox().intersects(area))continue;
            var box=player.getBoundingBox().inflate(.35);
            if(!box.contains(start)&&box.clip(start,end).isEmpty())continue;
            if(scald){player.setSecondsOnFire(10);player.removeEffect(HotSpringContent.HEALING.get());}
            else HotSpringEffect.touch(player);
        }
    }
}
