package dev.gungang.acoustics.server;

/** Recovery starts {@code delay} ticks (default 100) after damage, then fires every {@code period} ticks (default 20). */
public final class RegenerationClock {
    private long next;
    private int delay=100,period=20;
    private float lastHealth=Float.NaN;
    public RegenerationClock(long now) {damage(now);}
    public RegenerationClock(long now,int delay) {this.delay=Math.max(0,delay);damage(now);}
    /** Applies a changed delay setting from the next damage on. */
    public void delay(int ticks) {delay=Math.max(0,ticks);}
    /** Ticks between recoveries once it has started (default 20). */
    public void period(int ticks) {period=Math.max(1,ticks);}
    public void damage(long now) {next=now+delay;lastHealth=Float.NaN;}
    /** Also notice direct health reductions that bypass Forge's damage events. */
    public boolean tick(long now,float health,float maximum){
        if(Float.isFinite(lastHealth)&&health<lastHealth)damage(now);
        lastHealth=health;
        return tick(now,health>0&&health<maximum);
    }
    public boolean tick(long now,boolean injured) {
        if(!injured) {damage(now);return false;}
        if(now<next)return false;
        next=now+period;return true;
    }
}
