package dev.gungang.acoustics.server;

/** Completed successful hits; the next hit advances one tier, capped at five. */
public record RapierCombo(int stacks,long expires) {
    public static final int DURATION=60;
    public RapierCombo {stacks=Math.max(0,Math.min(5,stacks));}
    public int active(long now){return now<expires&&expires-now<=DURATION?stacks:0;}
    public RapierCombo hit(long now){return new RapierCombo(Math.min(5,active(now)+1),now+DURATION);}
    public static int damage(int tier){return switch(tier){case 2->26;case 3->32;case 4->39;case 5->47;default->21;};}
}
