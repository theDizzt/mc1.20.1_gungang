package dev.gungang.acoustics.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Fixed-step dash (six steps for the rapier): input, gravity, friction and jump impulses cannot extend it. */
public final class RapierDash {
    /** Per-tick step for the configured dash distance (item config rapier.dashDistance, default 10 blocks). */
    public static double speed(){return speed(ExtraGadgetConfig.rapier.dashDistance());}
    static double speed(double distance){return distance/RapierItem.DASH_TICKS;}
    private static final String LEFT="RapierDashSteps",X="RapierDashX",Z="RapierDashZ";
    public static void start(Player p,float yaw){start(p,yaw,ExtraGadgetConfig.rapier.dashDistance());}
    /** {@code distance}: blocks covered over the whole dash (the client receives the server's value in the packet). */
    public static void start(Player p,float yaw,double distance){start(p,yaw,distance,RapierItem.DASH_TICKS);}
    /** The same fixed-step dash over {@code ticks} steps (74 Hermes' Boots sets its own duration). */
    public static void start(Player p,float yaw,double distance,int ticks){
        ticks=Math.max(1,ticks);
        var tag=p.getPersistentData();double angle=Math.toRadians(yaw),speed=distance/ticks;
        tag.putInt(LEFT,ticks);tag.putDouble(X,-Math.sin(angle)*speed);tag.putDouble(Z,Math.cos(angle)*speed);
        tag.putLong("RapierMotionEnd",p.level().getGameTime()+ticks);
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
