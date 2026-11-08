package dev.gungang.acoustics.server;

import com.google.gson.*;
import java.util.*;

/** Small CPU-only residual policy. No file access or training in the decision loop. */
final class BotPolicy {
    static final int SCHEMA=2;
    static final Set<String> MODES=Set.of("CONTROL","NORMANDY","ESCORT","BOMB","DEATHMATCH","FREE_FOR_ALL");
    record Head(Map<BotTactics.Action,Map<String,Double>> weights) { }
    final String version;
    final Map<String,Head> modes;
    record Control(Map<String,Map<String,Double>> weights,Set<String> loadouts) { }
    record Prediction(String action,double probability) { }
    final Map<String,Map<String,Control>> controls;
    static final Map<String,Set<String>> CONTROL_ACTIONS=Map.of("movement",Set.of("APPROACH","WITHDRAW","LATERAL","HOLD","TO_OBJECTIVE","OTHER"),
            "fire",Set.of("YES","NO"),"reload",Set.of("YES","NO"),"secondary",Set.of("YES","NO"));
    BotPolicy(String version,Map<String,Head> modes,Map<String,Map<String,Control>> controls){this.version=version;this.modes=Map.copyOf(modes);this.controls=Map.copyOf(controls);}

    static BotPolicy parse(String json) {
        var root=JsonParser.parseString(json).getAsJsonObject();
        int schema=root.get("schema").getAsInt();
        if(schema!=1&&schema!=SCHEMA)throw new IllegalArgumentException("Unsupported policy schema");
        String version=root.get("version").getAsString();
        if(version.isBlank()||version.length()>80)throw new IllegalArgumentException("Invalid version");
        Map<String,Head> heads=new HashMap<>();
        for(var mode:(root.has("modes")?root.getAsJsonObject("modes"):new JsonObject()).entrySet()) {
            if(!MODES.contains(mode.getKey()))throw new IllegalArgumentException("Unsupported mode: "+mode.getKey());
            Map<BotTactics.Action,Map<String,Double>> actions=new EnumMap<>(BotTactics.Action.class);
            for(var action:mode.getValue().getAsJsonObject().entrySet()) {
                var id=BotTactics.Action.valueOf(action.getKey());
                if(id==BotTactics.Action.EVADE)throw new IllegalArgumentException("EVADE is rule-owned");
                Map<String,Double> weights=new HashMap<>();
                for(var entry:action.getValue().getAsJsonObject().entrySet()) {
                    double value=entry.getValue().getAsDouble();
                    if(!feature(entry.getKey())||!Double.isFinite(value)||Math.abs(value)>100)throw new IllegalArgumentException("Invalid weight: "+entry.getKey());
                    weights.put(entry.getKey(),value);
                }
                if(weights.size()>256)throw new IllegalArgumentException("Too many features");
                actions.put(id,Map.copyOf(weights));
            }
            if(actions.isEmpty())throw new IllegalArgumentException("Empty mode: "+mode.getKey());
            heads.put(mode.getKey(),new Head(Map.copyOf(actions)));
        }
        Map<String,Map<String,Control>> controls=new HashMap<>();
        if(schema==2&&root.has("controls"))for(var mode:root.getAsJsonObject("controls").entrySet()){
            if(!MODES.contains(mode.getKey()))throw new IllegalArgumentException("Unsupported control mode");
            Map<String,Control> entries=new HashMap<>();
            for(var entry:mode.getValue().getAsJsonObject().entrySet()){
                var allowed=CONTROL_ACTIONS.get(entry.getKey());if(allowed==null)throw new IllegalArgumentException("Unknown control");
                var data=entry.getValue().getAsJsonObject();Map<String,Map<String,Double>> weights=new HashMap<>();
                for(var action:data.getAsJsonObject("weights").entrySet()){
                    if(!allowed.contains(action.getKey()))throw new IllegalArgumentException("Invalid control action");
                    Map<String,Double> values=new HashMap<>();
                    for(var value:action.getValue().getAsJsonObject().entrySet()){
                        double number=value.getValue().getAsDouble();
                        if(!feature(value.getKey())||!Double.isFinite(number)||Math.abs(number)>100)throw new IllegalArgumentException("Invalid control weight");
                        values.put(value.getKey(),number);
                    }
                    if(values.size()>256)throw new IllegalArgumentException("Too many control features");
                    weights.put(action.getKey(),Map.copyOf(values));
                }
                if(weights.size()<2)throw new IllegalArgumentException("Control needs both contrasting actions");
                Set<String> loadouts=new HashSet<>();for(var value:data.getAsJsonArray("loadouts")){
                    String pair=value.getAsString();if(!pair.matches("[A-Za-z0-9_.:/-]{1,100}\\|[A-Za-z0-9_.:/-]{1,100}"))throw new IllegalArgumentException("Invalid loadout");
                    loadouts.add(pair);
                }
                if(loadouts.isEmpty()||loadouts.size()>256)throw new IllegalArgumentException("Invalid loadout coverage");
                entries.put(entry.getKey(),new Control(Map.copyOf(weights),Set.copyOf(loadouts)));
            }
            if(entries.isEmpty())throw new IllegalArgumentException("Empty controls");controls.put(mode.getKey(),Map.copyOf(entries));
        }
        if(heads.isEmpty()&&controls.isEmpty())throw new IllegalArgumentException("Empty policy");
        return new BotPolicy(version,heads,controls);
    }
    private static final Set<String> NUMERIC=Set.of("bias","health","visible","distance","underFire","hazard","objective","inside","take","contest","hold","ammo","ammoKnown","reloading","reloadKnown","secondaryReady","usingSecondary");
    static boolean feature(String key){return NUMERIC.contains(key)||key.matches("(primary|secondary|held|team):[A-Za-z0-9_.:/-]{1,100}");}
    static Map<String,Double> features(float health,int visible,double distance,boolean underFire,boolean hazard,
                                       BotObjective mission,boolean inside,String primary,String secondary,String team) {
        Map<String,Double> f=new TreeMap<>();
        f.put("bias",1d);f.put("health",Math.max(0,Math.min(1,(double)health)));
        f.put("visible",Math.min(1,visible/4d));f.put("distance",Double.isFinite(distance)?Math.min(1,distance/64):1d);
        f.put("underFire",underFire?1d:0d);f.put("hazard",hazard?1d:0d);
        f.put("objective",mission!=null?1d:0d);f.put("inside",inside?1d:0d);
        for(var stance:BotObjective.Stance.values())f.put(stance.name().toLowerCase(Locale.ROOT),mission!=null&&mission.stance()==stance?1d:0d);
        for(String key:List.of("primary:"+primary,"secondary:"+secondary,"team:"+team))if(feature(key))f.put(key,1d);
        return Map.copyOf(f);
    }
    Map<BotTactics.Action,Float> offsets(String mode,Map<String,Double> features) {
        var head=modes.get(mode);if(head==null)return Map.of();
        Map<BotTactics.Action,Float> result=new EnumMap<>(BotTactics.Action.class);
        head.weights.forEach((action,weights)->{
            double sum=0;for(var f:features.entrySet())sum+=weights.getOrDefault(f.getKey(),0d)*f.getValue();
            result.put(action,(float)(12*Math.tanh(sum)));
        });
        return result;
    }
    Map<String,Prediction> predict(String mode,Map<String,Double> features,String loadout){
        Map<String,Prediction> result=new HashMap<>();
        for(var entry:controls.getOrDefault(mode,Map.of()).entrySet()){
            if(!entry.getValue().loadouts.contains(loadout))continue;
            if(entry.getKey().equals("fire")&&features.getOrDefault("ammoKnown",0d)<1)continue;
            if(entry.getKey().equals("reload")&&features.getOrDefault("reloadKnown",0d)<1)continue;
            Map<String,Double> scores=new TreeMap<>();
            entry.getValue().weights.forEach((action,weights)->{double sum=0;for(var f:features.entrySet())sum+=weights.getOrDefault(f.getKey(),0d)*f.getValue();scores.put(action,sum);});
            double max=Collections.max(scores.values()),total=0;String best="";double bestScore=-1,second=-1;
            for(var score:scores.entrySet()){
                double exp=Math.exp(score.getValue()-max);total+=exp;
                if(exp>bestScore){second=bestScore;bestScore=exp;best=score.getKey();}else second=Math.max(second,exp);
            }
            double confidence=bestScore/total;
            if(confidence>=.7&&(bestScore-second)/total>=.2)result.put(entry.getKey(),new Prediction(best,confidence));
        }
        return Map.copyOf(result);
    }
}
