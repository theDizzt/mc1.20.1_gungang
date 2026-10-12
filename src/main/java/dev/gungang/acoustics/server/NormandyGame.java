package dev.gungang.acoustics.server;

/** Pure tick-based state machine, independent of world/player classes. */
public final class NormandyGame {
    private final NormandyRules rules;
    private int stage, progress, absent, decay;
    public NormandyGame(NormandyRules rules) { rules.validate(); this.rules=rules; }
    public boolean tick(boolean attacker,boolean defender) {
        if(complete())return false;
        if(attacker) { absent=0; decay=0; if(!defender)progress++; }
        else {
            absent=Math.min(rules.decayDelayTicks(),absent+1);
            if(absent>=rules.decayDelayTicks()&&++decay>=rules.decayStepTicks()) { progress=Math.max(0,progress-1); decay=0; }
        }
        if(progress<rules.captureTicks())return false;
        stage++; progress=0; absent=0; decay=0; return true;
    }
    public int stage() { return stage; }
    public boolean complete() { return stage>=rules.points().size(); }
    public float percent() { return complete()?100:100f*progress/rules.captureTicks(); }
    public NormandyRules.Point point() { return rules.points().get(Math.min(stage,rules.points().size()-1)); }
    public String winner(boolean expired) { return complete()?"BRAVO":expired?"ALPHA":""; }
}
