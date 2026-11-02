package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Local social reactions only; sending a bot reply never generates another reply event. */
final class BotPropReactions {
    private final Map<UUID,Integer> nearby=new HashMap<>();
    private UUID sender;
    private int slot,due,nextAllowed,nextScan;
    void reset(){nearby.clear();sender=null;due=nextAllowed=nextScan=0;}
    static int response(int incoming,boolean friendly,boolean danger){
        if(danger||!friendly)return 3; // The block's surprised face, even if the seeker waves.
        return switch(incoming){case 1->1;case 2,3->2;case 4->3;case 5->5;case 6->6;case 7->5;default->2;};
    }
    private static boolean visible(BotEntity bot,PropHuntMatch hunt,LivingEntity other){
        if(other==bot||!other.isAlive()||other.isSpectator()||other.level()!=bot.level()||!hunt.participant(other.getUUID()))return false;
        var own=bot.disguise();if(own==null||own.isRemoved())return false;
        var disguise=hunt.prop(other.getUUID());
        var target=disguise!=null&&!disguise.isRemoved()?disguise.getBoundingBox().getCenter():other.getEyePosition();
        var from=own.getBoundingBox().getCenter();if(from.distanceToSqr(target)>64)return false;
        return bot.level().clip(new ClipContext(from,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bot)).getType()==HitResult.Type.MISS;
    }
    void observe(BotEntity bot,PropHuntMatch hunt,LivingEntity other,int incoming,int now){
        if(sender!=null&&slot==0&&incoming>0&&visible(bot,hunt,other)){
            sender=other.getUUID();slot=incoming;return;
        }
        if(now<nextAllowed||!visible(bot,hunt,other)||!hunt.hider(bot.getUUID())||!hunt.hiding()&&!hunt.hunting())return;
        sender=other.getUUID();slot=incoming;due=now+8+bot.getRandom().nextInt(13);
        // Reserve the reply immediately so emote spam cannot keep postponing it.
        nextAllowed=now+120;
    }
    void tick(BotEntity bot,PropHuntMatch hunt,int now,boolean danger){
        if(!hunt.hiding()&&!hunt.hunting()){reset();return;}
        if(sender!=null&&now>=due){
            var other=hunt.bodies().stream().filter(b->b.getUUID().equals(sender)).findFirst().orElse(null);
            if(other!=null&&visible(bot,hunt,other))Emotes.bot(bot,response(slot,hunt.hider(other.getUUID()),danger));
            sender=null;
        }
        if(now<nextScan)return;nextScan=now+10;
        nearby.values().removeIf(at->now-at>200);
        for(var other:hunt.bodies())if(visible(bot,hunt,other)){
            var previous=nearby.put(other.getUUID(),now);
            if((previous==null||now-previous>60)&&sender==null)observe(bot,hunt,other,0,now);
        }
    }
}
