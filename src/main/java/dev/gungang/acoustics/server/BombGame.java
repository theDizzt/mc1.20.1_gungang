package dev.gungang.acoustics.server;

/** Deterministic round state machine, independent of the Minecraft runtime. */
public final class BombGame {
    public enum Phase { PREPARING, RUNNING, ROUND_END, FINISHED }
    private final BombRules rules;
    private final int roundTicks;
    private final String firstAttack;
    private Phase phase=Phase.PREPARING;
    private int round=1,alpha,bravo,remaining,fuse;
    private boolean planted;
    private String roundWinner="",reason="";
    public BombGame(BombRules rules,int seconds,boolean alphaAttacks){
        rules.validate();if(seconds<1||seconds>86400)throw new IllegalArgumentException("Invalid bomb round time");
        this.rules=rules;roundTicks=seconds*20;firstAttack=alphaAttacks?"ALPHA":"BRAVO";remaining=rules.preparationSeconds()*20;
    }
    public Phase phase(){return phase;}
    public int round(){return round;}
    public int alphaScore(){return alpha;}
    public int bravoScore(){return bravo;}
    public boolean planted(){return planted;}
    public String attacker(){return ((round-1)/rules.swapEveryRounds())%2==0?firstAttack:opposite(firstAttack);}
    public String defender(){return opposite(attacker());}
    public static String opposite(String team){return team.equals("ALPHA")?"BRAVO":"ALPHA";}
    public int remainingTicks(){return planted&&phase==Phase.RUNNING?fuse:remaining;}
    public int fuseTicks(){return Math.max(0,fuse);}
    public String roundWinner(){return roundWinner;}
    public String reason(){return reason;}
    public String winner(){return alpha>=rules.winsRequired()?"ALPHA":bravo>=rules.winsRequired()?"BRAVO":"";}
    public boolean plant(int fuseTicks){
        if(phase!=Phase.RUNNING||planted||fuseTicks<1)return false;
        planted=true;fuse=fuseTicks;return true;
    }
    public boolean defuse(){
        if(phase!=Phase.RUNNING||!planted||fuse<=0)return false;
        win(defender(),"DEFUSED");return true;
    }
    public void tick(int attackersAlive,int defendersAlive){
        switch(phase){
            case PREPARING->{if(--remaining<=0){phase=Phase.RUNNING;remaining=roundTicks;}}
            case RUNNING->{
                if(planted){if(--fuse<=0)win(attacker(),"EXPLODED");}
                else if(attackersAlive==0)win(defender(),"ELIMINATED");
                else if(defendersAlive==0)win(attacker(),"ELIMINATED");
                else if(--remaining<=0)win(defender(),"TIMEOUT");
            }
            case ROUND_END->{if(--remaining<=0){
                if(!winner().isEmpty())phase=Phase.FINISHED;
                else {round++;planted=false;fuse=0;roundWinner="";reason="";phase=Phase.PREPARING;remaining=rules.preparationSeconds()*20;}
            }}
            case FINISHED->{}
        }
    }
    private void win(String team,String why){
        if(phase!=Phase.RUNNING)return;
        if(team.equals("ALPHA"))alpha++;else bravo++;
        roundWinner=team;reason=why;phase=Phase.ROUND_END;remaining=60;
    }
}
