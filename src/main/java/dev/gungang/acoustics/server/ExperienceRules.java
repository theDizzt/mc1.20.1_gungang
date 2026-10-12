package dev.gungang.acoustics.server;

public record ExperienceRules(Integer winBase,Integer winContributionMax,Integer lossBase,Integer lossContributionMax,
                              Double killWeight,Double assistWeight,Double damagePerPoint) {
    public ExperienceRules {
        winBase=winBase==null?32:winBase;winContributionMax=winContributionMax==null?24:winContributionMax;
        lossBase=lossBase==null?16:lossBase;lossContributionMax=lossContributionMax==null?16:lossContributionMax;
        killWeight=killWeight==null?1:killWeight;assistWeight=assistWeight==null?0.5:assistWeight;damagePerPoint=damagePerPoint==null?100:damagePerPoint;
        for(int n:new int[]{winBase,winContributionMax,lossBase,lossContributionMax})if(n<0||n>1000000)throw new IllegalArgumentException("Invalid XP reward");
        if(!Double.isFinite(killWeight)||killWeight<0||killWeight>1000||!Double.isFinite(assistWeight)||assistWeight<0||assistWeight>1000
                ||!Double.isFinite(damagePerPoint)||damagePerPoint<=0||damagePerPoint>1000000)throw new IllegalArgumentException("Invalid contribution weights");
    }
    public static ExperienceRules defaults() {return new ExperienceRules(null,null,null,null,null,null,null);}
    public double contribution(MatchSnapshot.Row row) {return row.kills()*killWeight+row.assists()*assistWeight+row.damage()/damagePerPoint;}
    public int reward(boolean win,double personal,double teamTotal) {
        double share=teamTotal<=0?0:Math.max(0,Math.min(1,personal/teamTotal));
        return (win?winBase:lossBase)+(int)Math.floor((win?winContributionMax:lossContributionMax)*share);
    }
}
