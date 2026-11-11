package dev.gungang.acoustics.server;

public final class AirAssaultRules {
    /** Square zones use full side lengths. Exactly one, innermost zone supplies damage. */
    public static double damage(double dx,double dz,ExtraGadgetConfig.AirAssault c){
        double distance=Math.max(Math.abs(dx),Math.abs(dz));
        if(distance<=c.centerSize()/2)return c.centerDamage();
        if(distance<=c.outerSize()/2)return c.outerDamage();
        if(distance<=c.shockwaveSize()/2)return c.shockwaveDamage();
        return -1;
    }
    public static int remainingDiveTicks(double seconds,int elapsed){return Math.max(1,(int)Math.ceil(seconds*20)-elapsed+1);}
    private AirAssaultRules(){}
}
