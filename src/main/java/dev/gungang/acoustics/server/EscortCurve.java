package dev.gungang.acoustics.server;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Centripetal Catmull-Rom expressed as Hermite segments, with continuous knot tangents. */
public final class EscortCurve {
    private final List<Vec3> points;
    public EscortCurve(List<Vec3> points){
        this.points=List.copyOf(points);if(points.size()<2)throw new IllegalArgumentException("At least two curve points are required");
        for(int i=1;i<points.size();i++)if(points.get(i).distanceToSqr(points.get(i-1))<1e-8)throw new IllegalArgumentException("Duplicate curve point");
    }
    private Vec3 point(int index){
        if(index<0)return points.get(0).scale(2).subtract(points.get(1));
        if(index>=points.size())return points.get(points.size()-1).scale(2).subtract(points.get(points.size()-2));
        return points.get(index);
    }
    private double interval(int index){return Math.sqrt(point(index).distanceTo(point(index+1)));}
    private Vec3 tangent(int i){
        var a=point(i-1);var b=point(i);var c=point(i+1);double before=interval(i-1),after=interval(i);
        return b.subtract(a).scale(1/before).subtract(c.subtract(a).scale(1/(before+after))).add(c.subtract(b).scale(1/after));
    }
    public Vec3 position(int segment,double t){
        double t2=t*t,t3=t2*t,dt=interval(segment);
        return point(segment).scale(2*t3-3*t2+1).add(tangent(segment).scale((t3-2*t2+t)*dt))
                .add(point(segment+1).scale(-2*t3+3*t2)).add(tangent(segment+1).scale((t3-t2)*dt));
    }
    public Vec3 direction(int segment,double t){
        double t2=t*t,dt=interval(segment);
        return point(segment).scale(6*t2-6*t).add(tangent(segment).scale((3*t2-4*t+1)*dt))
                .add(point(segment+1).scale(-6*t2+6*t)).add(tangent(segment+1).scale((3*t2-2*t)*dt)).normalize();
    }
}
