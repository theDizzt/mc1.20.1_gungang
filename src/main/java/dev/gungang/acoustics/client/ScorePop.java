package dev.gungang.acoustics.client;

/** Scale pop for team scores: quick swell on an increase, then a damped settle back to 1. */
final class ScorePop {
    private static final float DURATION=.5f,RISE=.18f,PEAK=.45f;
    private static final int[] LAST={Integer.MIN_VALUE,Integer.MIN_VALUE};
    private static final long[] STARTED=new long[2];
    /** Team 0 is ALPHA, 1 is BRAVO. Only increases pop, so a new match resetting to 0 stays quiet. */
    static float scale(int team,int score) {
        long now=System.nanoTime();
        if(LAST[team]!=Integer.MIN_VALUE&&score>LAST[team])STARTED[team]=now;
        LAST[team]=score;
        if(STARTED[team]==0)return 1;
        float t=(now-STARTED[team])/1e9f/DURATION;
        if(t>=1){STARTED[team]=0;return 1;}
        if(t<RISE)return 1+PEAK*(float)Math.sin(t/RISE*Math.PI/2);
        float u=(t-RISE)/(1-RISE);
        return 1+PEAK*(1-u)*(1-u)*(float)Math.cos(u*Math.PI*1.2);
    }
    static boolean popping(int team) { return STARTED[team]!=0; }
    static void reset() { LAST[0]=LAST[1]=Integer.MIN_VALUE;STARTED[0]=STARTED[1]=0; }
    private ScorePop() { }
}
