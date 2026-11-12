package dev.gungang.acoustics.server;

import java.util.*;

/**
 * Small respawn loadout policy; no inference service or persistent training data. Scores fit, firepower and recent
 * results, then spreads bots over the arsenal: weapons other bots carry score lower and the pick is a weighted draw
 * among the near-best options instead of always the single best.
 */
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
        return choose(options,fixed,previous,outcome,secondary,history,Map.of(),null);
    }
    /** Score lost per other bot already carrying the weapon, so a team spreads over the arsenal. */
    static final double USAGE_PENALTY=2.5;
    /** Weighted pick: options more than this far below the best are never drawn; within it, weight exp(Δ/TEMPERATURE). */
    static final double WINDOW=6,TEMPERATURE=2;
    /**
     * {@code usage}: how many other bots carry each id now. {@code random} null picks the best score (deterministic);
     * otherwise a weighted draw among the near-best options, so equal bots do not all take the same weapon.
     */
    static String choose(List<Option> options,String fixed,String previous,Outcome outcome,boolean secondary,
                         Map<String,List<Outcome>> history,Map<String,Integer> usage,Random random){
        if(fixed.equals("none")&&secondary)return null;
        if(!fixed.isEmpty())return options.stream().filter(o->o.id().equals(fixed)).findFirst().orElseThrow().id();
        if(options.isEmpty())return null;
        boolean previousExists=options.stream().anyMatch(o->o.id().equals(previous));
        if(previousExists&&(outcome==null||!outcome.combat()||outcome.productive()))return previous;
        // First spawn: no fight to learn from yet, so the static scores (which always crown the same gun) are skipped
        // and the pick is uniform among the weapons the fewest other bots carry.
        if(random!=null&&outcome==null&&!previousExists){
            int least=options.stream().mapToInt(o->usage.getOrDefault(o.id(),0)).min().orElse(0);
            var pool=options.stream().filter(o->usage.getOrDefault(o.id(),0)==least).toList();
            return pool.get(random.nextInt(pool.size())).id();
        }
        double distance=outcome==null?12:outcome.distance();
        boolean fragile=outcome!=null&&outcome.aliveTicks()<200;
        var scores=new LinkedHashMap<String,Double>();
        for(var o:options){
            double score=score(o,secondary,distance,fragile,history);
            // An ineffective life is a reason to try an alternative, not an unconditional rotation on every death.
            if(previousExists&&options.size()>1&&o.id().equals(previous))score-=3;
            score-=USAGE_PENALTY*usage.getOrDefault(o.id(),0);
            scores.put(o.id(),score);
        }
        var best=scores.entrySet().stream().max(Map.Entry.<String,Double>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder()))).orElseThrow();
        if(random==null||scores.size()==1)return best.getKey();
        double top=best.getValue(),total=0;
        var weights=new ArrayList<Map.Entry<String,Double>>();
        for(var e:scores.entrySet())if(e.getValue()>=top-WINDOW){
            double w=Math.exp((e.getValue()-top)/TEMPERATURE);total+=w;weights.add(Map.entry(e.getKey(),w));
        }
        double roll=random.nextDouble()*total;
        for(var e:weights){roll-=e.getValue();if(roll<0)return e.getKey();}
        return best.getKey();
    }
    private static double score(Option o,boolean secondary,double distance,boolean fragile,Map<String,List<Outcome>> history){
        double score;
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
        return score;
    }
    private BotLoadoutChoice(){}
}
