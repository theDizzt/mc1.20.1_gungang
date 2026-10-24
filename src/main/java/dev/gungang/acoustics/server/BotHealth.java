package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Local loaded-chunk search only. At most four path searches every two seconds. */
final class BotHealth {
    private BlockPos goal;
    private int nextSearch,expires,nextPath;
    private final Map<BlockPos,Integer> retryAfter=new HashMap<>();
    private final List<StationEntity> cached=new ArrayList<>();
    private int nextAvailability,checkedRange;
    private BlockPos checkedFrom;
    void reset(){goal=null;nextSearch=expires=nextPath=nextAvailability=0;checkedFrom=null;cached.clear();retryAfter.clear();}
    boolean seeking(){return goal!=null;}
    /** Cheap cached availability, without starting navigation or spending a path search. */
    boolean available(BotEntity bot,int now,int range) {
        if(goal!=null)return true;
        if(now<nextSearch)return false;
        return !candidates(bot,now,range).isEmpty();
    }
    boolean navigate(BotEntity bot,int now,int range){
        var level=(ServerLevel)bot.level();
        if(bot.getHealth()>=bot.getMaxHealth()*.9f){goal=null;return false;}
        if(goal!=null){
            if(now>=expires||!(level.getBlockEntity(goal) instanceof StationEntity station)||!station.availableHealthPack()){
                if(now>=expires)retryAfter.put(goal,now+200);
                goal=null;bot.getNavigation().stop();
            }else{
                if(bot.getNavigation().isDone()&&now>=nextPath) {
                    if(BotMovement.spent())return true;
                    var path=BotMovement.path(bot,BlockPos.containing(goal.getX()+.5,standingY(level,goal),goal.getZ()+.5),0);
                    nextPath=now+10;
                    if(path==null||!path.canReach()||!bot.getNavigation().moveTo(path,1)) {
                        retryAfter.put(goal,now+200);goal=null;return false;
                    }
                }
                return true;
            }
        }
        if(bot.getHealth()>bot.getMaxHealth()*.6f||now<nextSearch)return false;
        nextSearch=now+40;
        retryAfter.values().removeIf(t->t<=now);
        var candidates=candidates(bot,now,range);
        for(var station:candidates.stream().limit(4).toList()){
            var pos=station.getBlockPos();
            if(BotMovement.spent()){nextSearch=now+1;return true;}
            var path=BotMovement.path(bot,BlockPos.containing(pos.getX()+.5,standingY(level,pos),pos.getZ()+.5),0);
            if(path!=null&&path.canReach()){
                goal=pos.immutable();expires=now+200;nextPath=now+10;bot.getNavigation().moveTo(path,1);return true;
            }
            retryAfter.put(pos.immutable(),now+200);
        }
        return false;
    }
    private List<StationEntity> candidates(BotEntity bot,int now,int range) {
        var level=(ServerLevel)bot.level();
        int radius=Math.min(range,32),cx=bot.blockPosition().getX()>>4,cz=bot.blockPosition().getZ()>>4;
        if(checkedFrom==null||now>=nextAvailability||checkedRange!=radius||checkedFrom.distToCenterSqr(bot.position())>=64) {
            cached.clear();checkedFrom=bot.blockPosition();checkedRange=radius;nextAvailability=now+40;int visited=0;
            search: for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++){
                var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
                for(var entity:chunk.getBlockEntities().values()){
                    if(++visited>2048)break search;
                    if(entity instanceof StationEntity station
                            &&Math.abs(entity.getBlockPos().getY()-bot.getY())<=8
                            &&entity.getBlockPos().distToCenterSqr(bot.position())<=radius*radius)cached.add(station);
                }
            }
        }
        return cached.stream().filter(s->!s.isRemoved()&&s.availableHealthPack()&&now>=retryAfter.getOrDefault(s.getBlockPos(),0)
                &&Math.abs(s.getBlockPos().getY()-bot.getY())<=8&&s.getBlockPos().distToCenterSqr(bot.position())<=radius*radius)
                .sorted(Comparator.comparingDouble(s->s.getBlockPos().distToCenterSqr(bot.position()))).toList();
    }
    private static double standingY(ServerLevel level,BlockPos pos){
        var shape=level.getBlockState(pos).getCollisionShape(level,pos);
        return pos.getY()+(shape.isEmpty()?1:shape.max(net.minecraft.core.Direction.Axis.Y));
    }
}
