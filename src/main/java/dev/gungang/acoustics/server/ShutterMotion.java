package dev.gungang.acoustics.server;

/** One block per second; the same time-based position drives rendering and collision. */
public final class ShutterMotion {
    public static final double SPEED=1/20d;
    public static double lift(double start,double ticks,boolean open,int height){
        return Math.max(0,Math.min(height,start+Math.max(0,ticks)*SPEED*(open?1:-1)));
    }
    public static double bottom(int groupBottom,int tileY,double lift){return Math.max(0,Math.min(1,groupBottom+lift-tileY));}
    private ShutterMotion(){}
}
