package dev.gungang.acoustics.server;

/** Samples travel only: repeated returns without net progress indicate an oscillating route. */
final class BotTravelHistory {
    private final double[] x=new double[13],y=new double[13],z=new double[13];
    private int count,last=-1000;
    void reset(){count=0;last=-1000;}
    boolean sample(double px,double py,double pz,int now){
        if(now-last>30)count=0;
        if(now-last<10)return false;
        last=now;
        if(count==13){System.arraycopy(x,1,x,0,12);System.arraycopy(y,1,y,0,12);System.arraycopy(z,1,z,0,12);count=12;}
        x[count]=px;y[count]=py;z[count++]=pz;
        if(count<13)return false;
        double length=0;int returns=0;
        for(int i=1;i<count;i++)length+=Math.sqrt(sq(x[i]-x[i-1])+sq(y[i]-y[i-1])+sq(z[i]-z[i-1]));
        for(int i=0;i<count-3;i++)if(sq(px-x[i])+sq(py-y[i])+sq(pz-z[i])<2.25)returns++;
        return length>8&&returns>=3&&sq(px-x[0])+sq(py-y[0])+sq(pz-z[0])<9;
    }
    private static double sq(double v){return v*v;}
}
