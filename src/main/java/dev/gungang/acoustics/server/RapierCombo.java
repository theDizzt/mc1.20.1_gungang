package dev.gungang.acoustics.server;

/** Completed successful hits; the next hit advances one tier, capped at five. */
public record RapierCombo(int stacks,long expires) {
    public static final int DURATION=60;
    public RapierCombo {stacks=Math.max(0,Math.min(5,stacks));}
    public int active(long now){return now<expires&&expires-now<=DURATION?stacks:0;}
    public RapierCombo hit(long now){return new RapierCombo(Math.min(5,active(now)+1),now+DURATION);}
    /** Thrust damage at a combo tier (item setting rapier tierNDamage: 27 · 33 · 40 · 48 · 57). */
    public static int damage(int tier){return (int)Math.round(ExtraGadgetConfig.rapier.damage(tier));}
}
