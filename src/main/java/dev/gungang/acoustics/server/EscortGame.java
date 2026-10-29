package dev.gungang.acoustics.server;

import java.util.List;

/** Deterministic 20 Hz rules, independent of world/entities. */
public final class EscortGame {
    public final EscortRules rules;
    public final List<Double> checkpoints;
    public final double length;
    public double distance, multiplier;
    public int remainingTicks, stopTicks, completed, idleTicks, overtimeTicks;
    /** Overtime after the time limit: 5 s, refilled while attackers are at the cart, drained otherwise. */
    public static final int OVERTIME_TICKS=5*20;
    public boolean overtime(){return remainingTicks==0&&winner.isEmpty();}
    public String motion="STOP", winner="";
    public EscortGame(EscortRules rules,int seconds,double length,List<Double> checkpoints){
        this.rules=rules;this.length=length;this.checkpoints=List.copyOf(checkpoints);remainingTicks=seconds*20;
        if(length<=0||checkpoints.size()<1||checkpoints.size()>3)throw new IllegalArgumentException("Escort requires 1..3 checkpoints");
        double last=0;for(double c:checkpoints){if(!Double.isFinite(c)||c<=last||c>=length)throw new IllegalArgumentException("Invalid checkpoint order");last=c;}
    }
    public void tick(int attackers,boolean defenders){
        if(!winner.isEmpty())return;
        motion="STOP";multiplier=0;
        double floor=completed==0?0:checkpoints.get(completed-1);
        if(stopTicks>0){
            motion="CHECKPOINT";
            --stopTicks;
        } else if(!defenders&&attackers>0){
            idleTicks=0;multiplier=Math.min(rules.maxMultiplier(),1+(attackers-1)*rules.extraPlayerMultiplier());
            motion="FORWARD";
            distance=Math.min(length,distance+rules.speed()*multiplier/20);
            if(completed<checkpoints.size()&&distance>=checkpoints.get(completed)){
                distance=checkpoints.get(completed++);stopTicks=rules.checkpointStopSeconds()*20;motion="CHECKPOINT";multiplier=0;
                remainingTicks+=rules.checkpointBonusSeconds()*20;
            }
            if(distance>=length){winner="ALPHA";return;}
        } else if(defenders&&attackers==0&&distance>floor){
            // Defenders push at base speed, but cannot undo the most recently earned checkpoint.
            idleTicks++;motion="REVERSE";
            distance=Math.max(floor,distance-rules.speed()/20);
        } else {
            if(attackers>0)idleTicks=0;else idleTicks++;
            if(!defenders&&attackers==0&&idleTicks>=rules.rollbackDelaySeconds()*20&&rules.rollbackSections().contains(completed)&&distance>floor&&rules.rollbackSpeed()>0){
                distance=Math.max(floor,distance-rules.speed()/20);motion="REVERSE"; // reverse is always 1.0x forward speed
            }else if(defenders)motion="CONTESTED";
        }
        if(remainingTicks>0){remainingTicks--;if(remainingTicks==0)overtimeTicks=OVERTIME_TICKS;return;}
        // Overtime: delivering still wins for ALPHA; attackers at the cart (pushing or contested) reset it, an empty cart lets it run out.
        if(attackers>0)overtimeTicks=OVERTIME_TICKS;
        else if(--overtimeTicks<=0){overtimeTicks=0;winner="BRAVO";}
    }
}
