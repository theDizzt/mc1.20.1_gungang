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
    /** Defenders push the cart back at this fraction of the base forward speed. */
    public static final double PUSH_BACK=1/3d;
    /** Escorts at the cart for the top multiplier: from one escort (x1) it climbs evenly to rules.maxMultiplier at this many. */
    public static final int FULL_SPEED_ESCORTS=4;
    /** Lobby-adjustable: defenders' push-back speed (blocks/s), overtime length, and time added per checkpoint reached. */
    public double reverseSpeed;
    public int overtimeLimit=OVERTIME_TICKS;
    public final int[] bonusSeconds;
    public EscortGame(EscortRules rules,int seconds,double length,List<Double> checkpoints){
        this.rules=rules;this.length=length;this.checkpoints=List.copyOf(checkpoints);remainingTicks=seconds*20;
        reverseSpeed=rules.speed()*PUSH_BACK;bonusSeconds=new int[checkpoints.size()];java.util.Arrays.fill(bonusSeconds,rules.checkpointBonusSeconds());
        if(length<=0||checkpoints.size()<1||checkpoints.size()>3)throw new IllegalArgumentException("Escort requires 1..3 checkpoints");
        double last=0;for(double c:checkpoints){if(!Double.isFinite(c)||c<=last||c>=length)throw new IllegalArgumentException("Invalid checkpoint order");last=c;}
    }
    /** x1 for one escort, rising evenly to {@code max} at {@link #FULL_SPEED_ESCORTS} and staying there. */
    public static double multiplier(double max,int attackers){
        if(attackers<=0)return 0;
        return Math.min(max,1+(Math.min(attackers,FULL_SPEED_ESCORTS)-1)*(max-1)/(FULL_SPEED_ESCORTS-1));
    }
    /** Seconds added on reaching checkpoint {@code index} (0-based). */
    public int bonus(int index){return index>=0&&index<bonusSeconds.length?bonusSeconds[index]:rules.checkpointBonusSeconds();}
    public void tick(int attackers,boolean defenders){
        if(!winner.isEmpty())return;
        motion="STOP";multiplier=0;
        double floor=completed==0?0:checkpoints.get(completed-1);
        if(stopTicks>0){
            motion="CHECKPOINT";
            --stopTicks;
        } else if(!defenders&&attackers>0){
            idleTicks=0;multiplier=multiplier(rules.maxMultiplier(),attackers);
            motion="FORWARD";
            distance=Math.min(length,distance+rules.speed()*multiplier/20);
            if(completed<checkpoints.size()&&distance>=checkpoints.get(completed)){
                distance=checkpoints.get(completed++);stopTicks=rules.checkpointStopSeconds()*20;motion="CHECKPOINT";multiplier=0;
                remainingTicks+=bonusSeconds[completed-1]*20;
            }
            if(distance>=length){winner="ALPHA";return;}
        } else if(defenders&&attackers==0&&distance>floor){
            // Defenders push back (a third of base speed unless the lobby sets it), but cannot undo the most recently earned checkpoint.
            idleTicks++;motion=reverseSpeed>0?"REVERSE":"CONTESTED";
            distance=Math.max(floor,distance-reverseSpeed/20);
        } else {
            if(attackers>0)idleTicks=0;else idleTicks++;
            if(!defenders&&attackers==0&&idleTicks>=rules.rollbackDelaySeconds()*20&&rules.rollbackSections().contains(completed)&&distance>floor&&rules.rollbackSpeed()>0){
                distance=Math.max(floor,distance-rules.speed()/20);motion="REVERSE"; // reverse is always 1.0x forward speed
            }else if(defenders)motion="CONTESTED";
        }
        if(remainingTicks>0){remainingTicks--;if(remainingTicks==0)overtimeTicks=overtimeLimit;return;}
        // Overtime: delivering still wins for ALPHA; attackers at the cart (pushing or contested) reset it, an empty cart lets it run out.
        if(attackers>0)overtimeTicks=overtimeLimit;
        else if(--overtimeTicks<=0){overtimeTicks=0;winner="BRAVO";}
    }
}
