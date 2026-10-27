package dev.gungang.acoustics.server;

/** First point heals 100 ticks after damage, then one point every 20 ticks. */
public final class RegenerationClock {
    private long next;
    private float lastHealth=Float.NaN;
    public RegenerationClock(long now) {damage(now);}
    public void damage(long now) {next=now+100;lastHealth=Float.NaN;}
    /** Also notice direct health reductions that bypass Forge's damage events. */
    public boolean tick(long now,float health,float maximum){
        if(Float.isFinite(lastHealth)&&health<lastHealth)damage(now);
        lastHealth=health;
        return tick(now,health>0&&health<maximum);
    }
    public boolean tick(long now,boolean injured) {
        if(!injured) {damage(now);return false;}
        if(now<next)return false;
        next=now+20;return true;
    }
}
