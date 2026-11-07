package dev.gungang.acoustics.server;

/** Bomb decisions use public timers and the bot's own perception, never hidden enemies. */
final class BotBombDecision {
    static boolean defuse(int fuse,int total,int progress,boolean safe){
        int remaining=Math.max(0,total-Math.max(0,progress));
        return fuse>remaining&&(safe||progress>=total*.6||fuse<=remaining+60);
    }
    static boolean commit(boolean defuse,int timer,int duration,int progress,boolean safe){
        return defuse?defuse(timer,duration,progress,safe):progress>=0&&safe||timer<=duration+100;
    }
    private BotBombDecision(){}
}
