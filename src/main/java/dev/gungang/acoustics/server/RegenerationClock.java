package dev.gungang.acoustics.server;

/** First point heals 100 ticks after damage, then one point every 20 ticks. */
public final class RegenerationClock {
    private long next;
    public RegenerationClock(long now) {damage(now);}
    public void damage(long now) {next=now+100;}
    public boolean tick(long now,boolean injured) {
        if(!injured) {damage(now);return false;}
        if(now<next)return false;
        next=now+20;return true;
    }
}
