package dev.gungang.acoustics.server;

/**
 * Pure throw ballistics. The axe leaves {@code arcDegrees} above the look direction (0 by default: straight along the
 * crosshair) and drops under gravity; a throw while looking level lands {@code distance} blocks away on flat ground.
 */
public final class HandAxeRules {
    // Vanilla stores these as floats; keep their exact promoted values in the predictor.
    public static final double GRAVITY=(double).05f,DRAG=(double).99f,EYE_HEIGHT=1.52;
    /** Held ticks to throw distance, linear from min to max over the charge time. */
    public static double distance(int heldTicks,double min,double max,double chargeSeconds) {
        double charge=Math.max(0,Math.min(1,heldTicks/Math.max(1,chargeSeconds*20)));
        return min+(max-min)*charge;
    }
    /** Launch pitch in Minecraft degrees (negative is up): the look pitch raised by the arc, never past straight up. */
    public static float pitch(float lookPitch,double arcDegrees) {return (float)Math.max(-89,lookPitch-arcDegrees);}
    /** Launch speed in blocks per tick, matching ThrowableProjectile's per-tick move, drag and gravity. */
    public static double speed(double distance,double arcDegrees) {
        double low=0,high=10;
        for(int i=0;i<40;i++) {double mid=(low+high)/2;if(range(mid,arcDegrees)<distance)low=mid;else high=mid;}
        return (low+high)/2;
    }
    static double range(double speed,double arcDegrees) {
        double angle=Math.toRadians(arcDegrees);
        double x=0,y=0,vx=speed*Math.cos(angle),vy=speed*Math.sin(angle);
        for(int tick=0;tick<400;tick++) {
            double nx=x+vx,ny=y+vy;
            if(ny<=-EYE_HEIGHT&&vy<0)return x+(nx-x)*(y+EYE_HEIGHT)/(y-ny);
            x=nx;y=ny;vx*=DRAG;vy=vy*DRAG-GRAVITY;
        }
        return x;
    }
    /** Head region extends 15% of body height below the eyes. */
    public static boolean headshot(double hitY,double feetY,double height,double eyeY) {
        return hitY>=eyeY-height*.15&&hitY<=feetY+height;
    }
    public static double damage(double base,boolean headshot) {return base*(headshot?1.5:1);}
    private HandAxeRules() { }
}
