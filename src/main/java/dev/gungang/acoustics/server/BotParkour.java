package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/** Bounded local gap jumps. Uses normal collision/gravity, never teleports the bot. */
final class BotParkour {
    private static final double JUMP=.42,MAX_SPEED=.30,DASH_SPEED=.45;
    // Three empty blocks require four blocks of travel between the platform centres.
    record Plan(Vec3 landing,int ticks,double speedLimit) {}
    private Plan flight;
    private boolean wasSprinting;
    private int elapsed,nextTry;
    boolean active(){return flight!=null;}

    static Plan plan(BotEntity bot,ArenaMap arena,Vec3 landing) {
        Vec3 from=bot.position();double dy=landing.y-from.y,distance=landing.subtract(from).horizontalDistance();
        if(distance<1||distance>4.3||dy>1.01||dy<-.01||!supported(bot,landing))return null;
        double y=0,vy=JUMP;int ticks=0;
        for(int t=1;t<=16;t++) {
            y+=vy;vy=(vy-.08)*.98;
            if(vy<0&&y<=dy){ticks=t;break;}
        }
        if(ticks==0||distance/ticks>DASH_SPEED)return null;
        double speedLimit=distance/ticks>MAX_SPEED?DASH_SPEED:MAX_SPEED;
        y=0;vy=JUMP;
        Vec3 previous=from;
        for(int t=1;t<=ticks;t++) {
            y+=vy;vy=(vy-.08)*.98;
            Vec3 point=from.lerp(landing,t/(double)ticks);
            point=new Vec3(point.x,from.y+(t==ticks?dy:y),point.z);
            // Sweep the full body, including head clearance and map hazards, between physics ticks.
            for(int sub=1;sub<=4;sub++)if(!clear(bot,arena,previous.lerp(point,sub/4.0)))return null;
            previous=point;
        }
        return new Plan(landing,ticks,speedLimit);
    }
    private static boolean supported(BotEntity bot,Vec3 p) {
        var pos=BlockPos.containing(p).below();
        return bot.level().hasChunkAt(pos)&&bot.level().getBlockState(pos).isFaceSturdy(bot.level(),pos,Direction.UP);
    }
    private static boolean clear(BotEntity bot,ArenaMap arena,Vec3 p) {
        var box=bot.getDimensions(Pose.STANDING).makeBoundingBox(p);
        for(int x=net.minecraft.util.Mth.floor(box.minX);x<=net.minecraft.util.Mth.floor(box.maxX);x++)
        for(int z=net.minecraft.util.Mth.floor(box.minZ);z<=net.minecraft.util.Mth.floor(box.maxZ);z++)
            if(!bot.level().hasChunkAt(new BlockPos(x,(int)p.y,z)))return false;
        var terrain=WalkNodeEvaluator.getBlockPathTypeStatic(bot.level(),BlockPos.containing(p).mutable());
        return (terrain==BlockPathTypes.OPEN||terrain==BlockPathTypes.WALKABLE)
                &&bot.level().getWorldBorder().isWithinBounds(box)&&bot.level().noCollision(bot,box)
                &&!BotMovement.danger(arena,bot,p)&&!BotMovement.knownDanger(bot,p)
                &&bot.level().getFluidState(BlockPos.containing(p)).isEmpty();
    }
    boolean start(BotEntity bot,ArenaMap arena,Vec3 goal,int now) {
        if(active())return true;
        if(now<nextTry||!bot.onGround()||bot.isInWater()||bot.isInLava()||bot.isPassenger()||bot.isNoGravity()
                ||bot.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                ||bot.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING))return false;
        nextTry=now+10;
        Vec3 facing=goal.subtract(bot.position()).multiply(1,0,1).normalize();
        if(facing.lengthSqr()<.5)return false;
        for(double length:new double[]{3,2,1,4})for(double angle:new double[]{0,.35,-.35})for(int height:new int[]{0,1}) {
            Vec3 offset=facing.yRot((float)angle).scale(length);
            Vec3 landing=Vec3.atBottomCenterOf(BlockPos.containing(bot.position().add(offset).add(0,height,0)));
            if(landing.distanceToSqr(goal)>=bot.position().distanceToSqr(goal)-.5)continue;
            if(BotMovement.safeSegment(bot,arena,bot.position(),landing))continue;
            var candidate=plan(bot,arena,landing);
            if(candidate==null)continue;
            flight=candidate;elapsed=0;
            wasSprinting=bot.isSprinting();
            if(candidate.speedLimit()>MAX_SPEED)bot.setSprinting(true);
            bot.getNavigation().stop();bot.getMoveControl().strafe(0,0);
            bot.setXxa(0);bot.setZza(0);bot.setSpeed(0);
            bot.setDeltaMovement(0,JUMP,0);bot.setOnGround(false);
            return true;
        }
        return false;
    }
    /** Called by the body's travel method, exactly once per physics tick. */
    Vec3 steer(BotEntity bot) {
        if(!active())return null;
        if((elapsed>0&&bot.onGround())||elapsed>=24||bot.isInWater()||bot.isInLava()) {
            flight=null;bot.setSprinting(wasSprinting);bot.setDeltaMovement(0,bot.getDeltaMovement().y,0);return Vec3.ZERO;
        }
        int remaining=flight.ticks()-elapsed;
        Vec3 delta=remaining>0?flight.landing().subtract(bot.position()).multiply(1,0,1).scale(1.0/remaining):Vec3.ZERO;
        if(delta.length()>flight.speedLimit())delta=delta.normalize().scale(flight.speedLimit());
        bot.setDeltaMovement(delta.x,bot.getDeltaMovement().y,delta.z);
        elapsed++;return Vec3.ZERO;
    }
}
