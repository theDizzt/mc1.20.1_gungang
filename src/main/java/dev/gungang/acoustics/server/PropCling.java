package dev.gungang.acoustics.server;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 숨바꼭질 wall cling (Q), shared by the predicting client and the validating server. A clinging disguise hangs on a
 * wall with no gravity and slides over its face with the movement keys, only where the wall is behind both its feet
 * and a band {@link #HEADROOM} above its head. That keeps the top of the body well below the wall's top edge, so
 * letting go never puts it beside a ledge it could step onto: clinging is for hiding, not climbing (no parkour).
 */
public final class PropCling {
    /** How far toward the wall the support probes reach; the server allows more for movement lag. */
    public static final double REACH=.08,SERVER_REACH=.3;
    public static final double HEADROOM=.6,SPEED=.1;
    private static boolean solid(Level level,AABB box){
        for(var shape:level.getBlockCollisions(null,box))if(!shape.isEmpty())return true;
        return false;
    }
    /** Wall behind the feet and above the head; blocks only, so the owner's own disguise never counts as a wall. */
    public static boolean attached(Level level,AABB body,Direction wall,double reach){
        var step=Vec3.atLowerCornerOf(wall.getNormal()).scale(reach);
        var feet=new AABB(body.minX,body.minY+.02,body.minZ,body.maxX,body.minY+.12,body.maxZ).move(step);
        var above=new AABB(body.minX,body.maxY+HEADROOM-.1,body.minZ,body.maxX,body.maxY+HEADROOM,body.maxZ).move(step);
        return solid(level,feet)&&solid(level,above);
    }
    /** The wall to cling to: the one faced first, then any other side; null when none qualifies. */
    public static Direction find(Level level,AABB body,float yaw){
        var facing=Direction.fromYRot(yaw);
        if(attached(level,body,facing,REACH))return facing;
        for(var side:Direction.Plane.HORIZONTAL)if(side!=facing&&attached(level,body,side,REACH))return side;
        return null;
    }
    /**
     * This tick's motion: forward/back climbs up/down the wall, strafing slides along it (relative to the view),
     * each axis only when the body stays free and supported there. A slight press keeps it against the wall.
     */
    public static Vec3 motion(Level level,AABB body,Direction wall,float forward,float strafe,float yaw){
        Vec3 normal=Vec3.atLowerCornerOf(wall.getNormal());
        double radians=Math.toRadians(yaw);
        Vec3 side=new Vec3(strafe*Math.cos(radians),0,strafe*Math.sin(radians));
        side=side.subtract(normal.scale(side.dot(normal)));
        side=side.lengthSqr()<1e-6?Vec3.ZERO:side.normalize().scale(SPEED*Math.min(1,Math.abs(strafe)));
        double up=Math.signum(forward)*SPEED*Math.min(1,Math.abs(forward));
        double dy=up!=0&&free(level,body.move(0,up,0))&&attached(level,body.move(0,up,0),wall,REACH)?up:0;
        var lifted=body.move(0,dy,0);
        boolean slide=side!=Vec3.ZERO&&free(level,lifted.move(side))&&attached(level,lifted.move(side),wall,REACH);
        return new Vec3(slide?side.x:0,dy,slide?side.z:0).add(normal.scale(.02));
    }
    private static boolean free(Level level,AABB box){return !solid(level,box.deflate(1e-4));}
    private PropCling(){}
}
