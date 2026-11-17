package dev.gungang.acoustics.server;

/** Covers every vanilla bot path search, including navigation's own recomputations. Server thread only. */
final class BotPathBudget {
    private static int tick=Integer.MIN_VALUE,calls;
    private static long used,started,deadline=Long.MAX_VALUE;
    private static boolean limited;
    private static void refresh(){
        var server=BotManager.server();int now=server==null?0:server.getTickCount();
        if(tick!=now){tick=now;calls=0;used=0;limited=false;}
    }
    static boolean spent(){
        if(MatchManager.realPropHunt()==null)return false;
        refresh();return calls>=4||used>=6_000_000;
    }
    static boolean begin(){
        refresh();limited=false;
        if(spent()){limited=true;return false;}
        calls++;started=System.nanoTime();deadline=started+Math.min(2_000_000,6_000_000-used);return true;
    }
    static boolean expired(){
        if(deadline==Long.MAX_VALUE||System.nanoTime()<deadline)return false;
        limited=true;return true;
    }
    static void end(){used+=System.nanoTime()-started;deadline=Long.MAX_VALUE;}
    static boolean limited(){return MatchManager.realPropHunt()!=null&&limited;}
    private BotPathBudget(){}
}
