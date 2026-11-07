package dev.gungang.acoustics.server;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * 252 Sanga's doll rides her skateboard up walls instead of sticking to them: skating into a wall carves up it with
 * the speed she brought, she rides along it under lighter gravity steered by where she looks, and the ride ends when
 * the speed runs out, she lets go of forward or leaves the wall. Jump kicks off the wall (onto another one, as in a
 * half-pipe). One ride per wall until she lands or kicks off. The client predicts the ride (players move client-side).
 */
public final class PropSkate {
    public static final String ITEM="gungang:sanga_doll";
    /** Slowest entry that still rides; share of the entry speed carved upward when facing the wall head-on. */
    static final double MIN_SPEED=.12,CARVE=.65,GRAVITY=.032,DRAG=.985,PRESS=.02,KICK_OUT=.38,KICK_UP=.42,END_SPEED=.06,SLIDE=.25;
    public static final int MAX_TICKS=60;
    public static boolean skater(String item){return ITEM.equals(item);}

    /** A ride in progress: the wall's side, the speed along it and upward, the ticks ridden. */
    public static final class Ride {
        public final Direction wall;double along,up;Vec3 heading;public int ticks;
        Ride(Direction wall,double along,double up,Vec3 heading){this.wall=wall;this.along=along;this.up=up;this.heading=heading;}
    }
    /** The look direction flattened onto the wall's face, or null when looking straight into it. */
    private static Vec3 tangent(Direction wall,float yaw){
        Vec3 normal=Vec3.atLowerCornerOf(wall.getNormal());double r=Math.toRadians(yaw);
        Vec3 look=new Vec3(-Math.sin(r),0,Math.cos(r));
        Vec3 t=look.subtract(normal.scale(look.dot(normal)));
        return t.lengthSqr()<1e-4?null:t.normalize();
    }
    /**
     * Starts a ride on {@code wall} at {@code speed} (blocks per tick): head-on turns most of it into height, at a
     * glancing angle most of it carries along the wall. Null when too slow.
     */
    public static Ride start(Direction wall,double speed,float yaw){
        if(speed<MIN_SPEED)return null;
        double r=Math.toRadians(yaw);Vec3 look=new Vec3(-Math.sin(r),0,Math.cos(r));
        double into=Math.max(0,look.dot(Vec3.atLowerCornerOf(wall.getNormal())));
        var heading=tangent(wall,yaw);if(heading==null)heading=Vec3.atLowerCornerOf(wall.getClockWise().getNormal());
        return new Ride(wall,speed*(1-.6*into),speed*CARVE*(.45+.55*into),heading);
    }
    /** This tick's motion, or null when the ride is over (forward released, too slow or too long). */
    public static Vec3 step(Ride ride,float forward,float yaw){
        if(forward<=0||++ride.ticks>MAX_TICKS)return null;
        var heading=tangent(ride.wall,yaw);if(heading!=null)ride.heading=heading;
        // Past the top of the carve the board slides back down the wall, never faster than SLIDE.
        ride.along*=DRAG;ride.up=Math.max(-SLIDE,ride.up-GRAVITY);
        if(ride.along<END_SPEED&&ride.up<0)return null;
        return ride.heading.scale(ride.along).add(0,ride.up,0).add(Vec3.atLowerCornerOf(ride.wall.getNormal()).scale(PRESS));
    }
    /** Jump off the wall: away from it, upward, keeping the speed along it. */
    public static Vec3 kick(Ride ride){
        return Vec3.atLowerCornerOf(ride.wall.getNormal()).scale(-KICK_OUT).add(ride.heading.scale(ride.along)).add(0,KICK_UP,0);
    }
    private PropSkate(){}
}
