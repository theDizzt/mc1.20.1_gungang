package dev.gungang.acoustics.server;

/** Physics shared by movement blocks and their regression checks. */
public final class MotionRules {
    public static int speedStep(int step) {return Math.max(1,Math.min(30,step));}
    public static double jumpHeight(double velocity) {
        double height=0;
        while(velocity>0){height+=velocity;velocity=(velocity-.08)*.98;}
        return height;
    }
    public static double jumpVelocity(double height) {
        double low=0,high=4;
        for(int i=0;i<60;i++){double middle=(low+high)/2;if(jumpHeight(middle)<height)low=middle;else high=middle;}
        return (low+high)/2;
    }
    /** Quadratic centerline joins the two edge midpoints without a sharp corner. */
    public static double[] point(double ix,double iz,double ox,double oz,double t) {
        return new double[]{.5*(1-t)*(1-t)*ix+.5*t*t*ox,.5*(1-t)*(1-t)*iz+.5*t*t*oz};
    }
    private MotionRules(){}
}
