package dev.gungang.acoustics.server;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 95 slime block and 190 honey block disguises stick to whatever they touch: up walls and along ceilings, moving with
 * the movement keys (forward climbs a wall, the keys walk across a ceiling). Unlike the Q cling this is free
 * climbing, over the top of a wall included. E lets go until the disguise lands again; they cannot jump. The client
 * predicts the movement (players move client-side); the server only refuses their jumps.
 */
public final class PropSticky {
    public static final double REACH=.08,SPEED=.12,PRESS=.02;
    public static boolean sticky(String item){return item.equals("minecraft:slime_block")||item.equals("minecraft:honey_block");}
    private static boolean solid(Level level,AABB box){
        for(var shape:level.getBlockCollisions(null,box))if(!shape.isEmpty())return true;
        return false;
    }
    /** A wall touching the body's side: the faced one first, then any other. Null when none. */
    public static Direction wall(Level level,AABB body,float yaw){
        var facing=Direction.fromYRot(yaw);
        if(touches(level,body,facing))return facing;
        for(var side:Direction.Plane.HORIZONTAL)if(side!=facing&&touches(level,body,side))return side;
        return null;
    }
    private static boolean touches(Level level,AABB body,Direction side){
        var step=Vec3.atLowerCornerOf(side.getNormal()).scale(REACH);
        return solid(level,new AABB(body.minX,body.minY+.05,body.minZ,body.maxX,body.maxY-.05,body.maxZ).move(step));
    }
    public static boolean ceiling(Level level,AABB body){
        return solid(level,new AABB(body.minX+.02,body.maxY,body.minZ+.02,body.maxX-.02,body.maxY+REACH,body.maxZ-.02));
    }
    /** On a wall: forward/back climbs up/down, strafing slides along it; a slight press keeps contact. */
    public static Vec3 wallMotion(Direction wall,float forward,float strafe,float yaw){
        Vec3 normal=Vec3.atLowerCornerOf(wall.getNormal());
        double radians=Math.toRadians(yaw);
        Vec3 side=new Vec3(strafe*Math.cos(radians),0,strafe*Math.sin(radians));
        side=side.subtract(normal.scale(side.dot(normal)));
        side=side.lengthSqr()<1e-6?Vec3.ZERO:side.normalize().scale(SPEED*Math.min(1,Math.abs(strafe)));
        double up=Math.signum(forward)*SPEED*Math.min(1,Math.abs(forward));
        return new Vec3(side.x,up,side.z).add(normal.scale(PRESS));
    }
    /** Under a ceiling: the movement keys walk across it as on a floor; a slight press upward keeps contact. */
    public static Vec3 ceilingMotion(float forward,float strafe,float yaw){
        double radians=Math.toRadians(yaw),sin=Math.sin(radians),cos=Math.cos(radians);
        Vec3 move=new Vec3(strafe*cos-forward*sin,0,forward*cos+strafe*sin);
        move=move.lengthSqr()<1e-6?Vec3.ZERO:move.normalize().scale(SPEED*Math.min(1,move.length()));
        return move.add(0,PRESS,0);
    }
    private PropSticky(){}
}
