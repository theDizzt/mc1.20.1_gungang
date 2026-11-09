package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** One causal window: observation first, then actual actions over the next ten ticks. */
final class BotDemonstration {
    final Map<String,Object> start;
    final Vec3 origin,enemy,objective;
    final int tick;
    final String context;
    private boolean fire,reload,secondary,movementKnown=true,fireKnown,reloadKnown=true;
    BotDemonstration(Map<String,Object> start,Vec3 origin,Vec3 enemy,Vec3 objective,int tick,String context,boolean fireKnown){
        this.start=start;this.origin=origin;this.enemy=enemy;this.objective=objective;this.tick=tick;this.context=context;this.fireKnown=fireKnown;
    }
    void action(String action){switch(action){case "fire"->fire=true;case "reload"->reload=true;case "secondary"->secondary=true;default->{}}}
    void observe(boolean voluntaryMovement,boolean knownReload,boolean knownFire){movementKnown&=voluntaryMovement;reloadKnown&=knownReload;fireKnown&=knownFire;}
    Map<String,String> targets(Vec3 end){
        Map<String,String> result=new TreeMap<>();
        if(movementKnown)result.put("movement",movement(origin,end,enemy,objective));
        // Unknown integration is not a negative example. Explicitly observed positives remain useful.
        if(fireKnown||fire)result.put("fire",fire?"YES":"NO");
        if(reloadKnown||reload)result.put("reload",reload?"YES":"NO");
        // Secondary request coverage is supplied by the caller, rather than assuming all mods are observable.
        if(secondary)result.put("secondary","YES");
        return result;
    }
    static String movement(Vec3 from,Vec3 to,Vec3 enemy,Vec3 objective){
        Vec3 travel=new Vec3(to.x-from.x,0,to.z-from.z);double length=travel.length();
        if(length<.25)return "HOLD";
        if(enemy!=null){
            Vec3 direction=new Vec3(enemy.x-from.x,0,enemy.z-from.z).normalize();
            double along=travel.dot(direction)/length;
            if(along>.65)return "APPROACH";
            if(along<-.65)return "WITHDRAW";
            return "LATERAL";
        }
        if(objective!=null&&travel.dot(new Vec3(objective.x-from.x,0,objective.z-from.z).normalize())/length>.65)return "TO_OBJECTIVE";
        return "OTHER";
    }
}
