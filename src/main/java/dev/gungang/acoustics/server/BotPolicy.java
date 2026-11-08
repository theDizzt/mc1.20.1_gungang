package dev.gungang.acoustics.server;

import com.google.gson.*;
import java.util.*;

/** Small CPU-only residual policy. No file access or training in the decision loop. */
final class BotPolicy {
    static final int SCHEMA=1;
    static final Set<String> MODES=Set.of("CONTROL","NORMANDY","ESCORT","BOMB","DEATHMATCH","FREE_FOR_ALL");
    record Head(Map<BotTactics.Action,Map<String,Double>> weights) { }
    final String version;
    final Map<String,Head> modes;
    BotPolicy(String version,Map<String,Head> modes){this.version=version;this.modes=Map.copyOf(modes);}

    static BotPolicy parse(String json) {
        var root=JsonParser.parseString(json).getAsJsonObject();
        if(root.get("schema").getAsInt()!=SCHEMA)throw new IllegalArgumentException("Unsupported policy schema");
        String version=root.get("version").getAsString();
        if(version.isBlank()||version.length()>80)throw new IllegalArgumentException("Invalid version");
        Map<String,Head> heads=new HashMap<>();
        for(var mode:root.getAsJsonObject("modes").entrySet()) {
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
        if(heads.isEmpty())throw new IllegalArgumentException("Empty policy");
        return new BotPolicy(version,heads);
    }
    private static final Set<String> NUMERIC=Set.of("bias","health","visible","distance","underFire","hazard","objective","inside","take","contest","hold");
    static boolean feature(String key){return NUMERIC.contains(key)||key.matches("(primary|secondary|team):[A-Za-z0-9_.:/-]{1,100}");}
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
}
