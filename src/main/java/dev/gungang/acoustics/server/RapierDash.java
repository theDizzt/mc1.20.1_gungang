package dev.gungang.acoustics.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Fixed six-step dash: input, gravity, friction and jump impulses cannot extend it. */
public final class RapierDash {
    public static final double SPEED=.65;
    private static final String LEFT="RapierDashSteps",X="RapierDashX",Z="RapierDashZ";
    public static void start(Player p,float yaw){
        var tag=p.getPersistentData();double angle=Math.toRadians(yaw);
        tag.putInt(LEFT,RapierItem.DASH_TICKS);tag.putDouble(X,-Math.sin(angle)*SPEED);tag.putDouble(Z,Math.cos(angle)*SPEED);
        tag.putLong("RapierMotionEnd",p.level().getGameTime()+RapierItem.DASH_TICKS);
        p.setDeltaMovement(Vec3.ZERO);
    }
    public static boolean active(Player p){return p.getPersistentData().getInt(LEFT)>0
            &&(p.isControlledByLocalInstance()||p.level().getGameTime()<p.getPersistentData().getLong("RapierMotionEnd"))
            &&p.isAlive()&&!p.isSpectator()&&!p.isPassenger();}
    public static boolean travel(Player p){
        // Remote players are interpolated by vanilla; only the locally controlled body travels here.
        if(!p.isControlledByLocalInstance())return false;
        return step(p);
    }
    static boolean step(Player p){
        var tag=p.getPersistentData();int left=tag.getInt(LEFT);
        if(left<=0)return false;
        if(!active(p)){tag.remove(LEFT);p.setDeltaMovement(Vec3.ZERO);return false;}
        p.setDeltaMovement(Vec3.ZERO);
        p.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(tag.getDouble(X),0,tag.getDouble(Z)));
        tag.putInt(LEFT,p.horizontalCollision?0:left-1);
        p.setDeltaMovement(Vec3.ZERO);
        return true;
    }
    private RapierDash(){}
}
