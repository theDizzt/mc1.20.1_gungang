package dev.gungang.acoustics.server;

import java.util.*;

/** Small deterministic respawn policy; no inference service or persistent training data. */
final class BotLoadoutChoice {
    record Option(String id,double health,double speed,boolean melee,double near,double far,
                  double damage,double rpm,int magazine,double reload,String role) {
        Option(String id,double health,double speed,boolean melee){this(id,health,speed,melee,0,0,0,0,0,0,"");}
        double combatScore(double distance) {
            double fit=far<=0?0:distance<near?-Math.min(6,(near-distance)*.35):distance>far?-Math.min(12,(distance-far)*.5):3;
            double shots=rpm/60,continuous=damage*shots;
            if(shots>0&&magazine>0)continuous=damage*magazine/(magazine/shots+Math.max(0,reload));
            return fit+Math.min(5,Math.max(0,continuous)/30);
        }
    }
    record Outcome(boolean combat,double damage,int aliveTicks,double distance) {
        boolean productive(){return damage>=50||damage>0&&aliveTicks>=600;}
    }
    static String choose(List<Option> options,String fixed,String previous,Outcome outcome,boolean secondary){
        return choose(options,fixed,previous,outcome,secondary,Map.of());
    }
    static String choose(List<Option> options,String fixed,String previous,Outcome outcome,boolean secondary,Map<String,List<Outcome>> history){
        if(fixed.equals("none")&&secondary)return null;
        if(!fixed.isEmpty())return options.stream().filter(o->o.id().equals(fixed)).findFirst().orElseThrow().id();
        if(options.isEmpty())return null;
        boolean previousExists=options.stream().anyMatch(o->o.id().equals(previous));
        if(previousExists&&(outcome==null||!outcome.combat()||outcome.productive()))return previous;
        double distance=outcome==null?12:outcome.distance();
        boolean fragile=outcome!=null&&outcome.aliveTicks()<200;
        return options.stream().max(Comparator.<Option>comparingDouble(o->{
            double score=0;
            if(secondary)score=o.melee()?(distance<=3?2:-20):1;
            else score=(fragile?2:1)*Math.min(o.health(),300)/100+Math.min(o.speed(),200)/100;
            score+=o.combatScore(distance);
            if(secondary)score+=switch(o.role()) {
                case "recovery_potion","shield","life_support"->fragile?4:1;
                case "molotov","use"->distance>=5&&distance<=20?3:0;
                case "orbital_strike"->distance>=28?4:-3;
                case "infrared_scanner"->distance>=16?3:1;
                case "jetpack"->distance>=24?2:0;
                case "landmine","bear_trap"->distance<=10?2:0;
                default->0;
            };
            var recent=history.getOrDefault(o.id(),List.of());
            if(!recent.isEmpty())score+=recent.stream().filter(Outcome::combat)
                    .mapToDouble(r->Math.min(150,r.damage())/30+(r.productive()?2:-2)).average().orElse(0);
            // An ineffective life is a reason to try an alternative, not an unconditional rotation on every death.
            if(previousExists&&options.size()>1&&o.id().equals(previous))score-=3;
            return score;
        }).thenComparing(Option::id,Comparator.reverseOrder())).orElseThrow().id();
    }
    private BotLoadoutChoice(){}
}
