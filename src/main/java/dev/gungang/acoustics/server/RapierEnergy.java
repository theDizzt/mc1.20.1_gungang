package dev.gungang.acoustics.server;

/** Server ticks: wait three seconds after spending, then recover one point every four ticks. */
public record RapierEnergy(int amount,int waitTicks) {
    public RapierEnergy {amount=Math.max(0,Math.min(100,amount));waitTicks=Math.max(0,Math.min(60,waitTicks));}
    public static final int COST=20;
    public boolean canStep(){return amount>=COST;}
    public RapierEnergy spend(){return canStep()?new RapierEnergy(amount-COST,60):this;}
    public RapierEnergy tick(){
        if(amount==100)return new RapierEnergy(100,0);
        if(waitTicks>1)return new RapierEnergy(amount,waitTicks-1);
        return new RapierEnergy(amount+1,amount+1==100?0:4);
    }
}
