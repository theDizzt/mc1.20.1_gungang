package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/** Bounded local gap jumps and jump pad launches. Uses normal collision/gravity, never teleports the bot. */
final class BotParkour {
    private static final double JUMP=.42,MAX_SPEED=.30,DASH_SPEED=.45,PAD_SPEED=.28,VAULT_SPEED=.4;
    // Three empty blocks require four blocks of travel between the platform centres.
    /** {@code rise}: a pad launch climbs straight up until it clears the landing's height, then drifts over. */
    record Plan(Vec3 landing,int ticks,double speedLimit,boolean rise) {}
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
        return new Plan(landing,ticks,speedLimit,false);
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
            begin(bot,candidate,JUMP);
            return true;
        }
        return false;
    }
    private boolean ready(BotEntity bot){
        return !active()&&bot.onGround()&&!bot.isInWater()&&!bot.isInLava()&&!bot.isPassenger()&&!bot.isNoGravity()
                &&!bot.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)&&!bot.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING);
    }
    private void begin(BotEntity bot,Plan plan,double velocity){
        flight=plan;elapsed=0;
        wasSprinting=bot.isSprinting();
        if(plan.speedLimit()>MAX_SPEED)bot.setSprinting(true);
        bot.getNavigation().stop();bot.getMoveControl().strafe(0,0);
        bot.setXxa(0);bot.setZza(0);bot.setSpeed(0);
        bot.setDeltaMovement(0,velocity,0);bot.setOnGround(false);
    }
    /** A mapped gap jump onto a known landing; false when the real terrain disagrees with the map grid. */
    boolean jump(BotEntity bot,ArenaMap arena,Vec3 landing){
        if(!ready(bot))return false;
        var candidate=plan(bot,arena,landing);if(candidate==null)return false;
        begin(bot,candidate,JUMP);return true;
    }
    /**
     * Hop a low obstacle (a railing of iron bars, a one-block ledge) toward the column beyond it: jump, cross once
     * clear of the obstacle's top, then fall straight down there, however far. Players hop the same railings.
     */
    boolean vault(BotEntity bot,Vec3 beyond){
        if(!ready(bot))return false;
        begin(bot,new Plan(new Vec3(beyond.x,bot.getY()+.9,beyond.z),10,VAULT_SPEED,true),JUMP);
        return true;
    }
    /** The pad under the bot's feet, as the pad's own jump handler finds it. */
    static JumpPadBlock padUnder(BotEntity bot){
        var pos=BlockPos.containing(bot.getX(),bot.getY()-.04,bot.getZ());
        return bot.level().getBlockState(pos).getBlock() instanceof JumpPadBlock pad&&Math.abs(bot.getY()-(pos.getY()+.125))<=.08?pad:null;
    }
    /** Ride the pad under the bot to a ledge; false when not on a pad or the flight would hit something. */
    boolean launch(BotEntity bot,ArenaMap arena,Vec3 landing){
        var pad=padUnder(bot);if(pad==null||!ready(bot))return false;
        double velocity=MotionRules.jumpVelocity(pad.height);
        int ticks=padFlight(bot.position(),velocity,landing,p->clear(bot,arena,p));
        if(ticks<=0)return false;
        begin(bot,new Plan(landing,ticks,PAD_SPEED,true),velocity);
        JumpPadBlock.launched(bot);return true;
    }
    /**
     * Ticks a pad flight from {@code from} takes to land on {@code landing}, or -1: the same climb-then-drift steering
     * and vanilla gravity/drag as {@link #steer}, the body swept between ticks.
     */
    static int padFlight(Vec3 from,double velocity,Vec3 landing,java.util.function.Predicate<Vec3> clear){
        double x=from.x,y=from.y,z=from.z,vy=velocity;
        for(int t=1;t<=120;t++){
            double rx=landing.x-x,rz=landing.z-z,rest=Math.sqrt(rx*rx+rz*rz),mx=0,mz=0;
            if(y>=landing.y+.1&&rest>1e-6){double scale=Math.min(PAD_SPEED,rest)/rest;mx=rx*scale;mz=rz*scale;}
            double nx=x+mx,ny=y+vy,nz=z+mz;
            if(vy<0&&ny<=landing.y){
                double ax=landing.x-nx,az=landing.z-nz;
                return ax*ax+az*az<=.0225&&clear.test(new Vec3(nx,landing.y,nz))?t:-1;
            }
            for(int sub=1;sub<=3;sub++)if(!clear.test(new Vec3(x+(nx-x)*sub/3,y+(ny-y)*sub/3,z+(nz-z)*sub/3)))return -1;
            x=nx;y=ny;z=nz;vy=(vy-.08)*.98;
        }
        return -1;
    }
    /** Called by the body's travel method, exactly once per physics tick. */
    Vec3 steer(BotEntity bot) {
        if(!active())return null;
        if((elapsed>0&&bot.onGround())||elapsed>=Math.max(24,flight.ticks()+10)||bot.isInWater()||bot.isInLava()) {
            flight=null;bot.setSprinting(wasSprinting);bot.setDeltaMovement(0,bot.getDeltaMovement().y,0);return Vec3.ZERO;
        }
        if(flight.rise()) {
            Vec3 rest=flight.landing().subtract(bot.position()).multiply(1,0,1);
            Vec3 drift=bot.getY()<flight.landing().y+.1||rest.lengthSqr()<1e-12?Vec3.ZERO:rest.length()>flight.speedLimit()?rest.normalize().scale(flight.speedLimit()):rest;
            bot.setDeltaMovement(drift.x,bot.getDeltaMovement().y,drift.z);
            elapsed++;return Vec3.ZERO;
        }
        int remaining=flight.ticks()-elapsed;
        Vec3 delta=remaining>0?flight.landing().subtract(bot.position()).multiply(1,0,1).scale(1.0/remaining):Vec3.ZERO;
        if(delta.length()>flight.speedLimit())delta=delta.normalize().scale(flight.speedLimit());
        bot.setDeltaMovement(delta.x,bot.getDeltaMovement().y,delta.z);
        elapsed++;return Vec3.ZERO;
    }
}
