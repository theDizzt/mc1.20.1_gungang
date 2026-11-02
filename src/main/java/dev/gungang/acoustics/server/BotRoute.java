package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One bot's long route over {@link BotNavGraph}: short vanilla paths between waypoints a few blocks apart, pad
 * launches and gap jumps where the grid has them, and closed shutters opened with a lever or button beside them,
 * the way a player would. Short moves stay with plain vanilla pathing.
 */
final class BotRoute {
    /** Goals closer than this go straight to vanilla pathing. */
    static final double FAR=12;
    private static final int SPACING=12,BLOCK_TICKS=600,DOOR_WAIT=200,SWITCH_WALK=160;
    enum Result {OFF,WALK,HOLD,WAIT,NONE}
    private record Step(int kind,Vec3 pos,Vec3 landing,BlockPos cell) { }
    private final ArrayDeque<Step> steps=new ArrayDeque<>();
    private Vec3 target,point,leverStand;
    /** Shutters with no usable switch, and jump/pad landings that failed for real, skipped for a while. */
    private final Map<Long,Integer> blocked=new HashMap<>();
    private final Map<Long,Integer> failedSwitches=new HashMap<>();
    private BlockPos lever,usedLever,door;
    /** Last hop's takeoff: a second hop from there within 3 s means the first one fell back. */
    private Vec3 vaultFrom;
    private int vaultAt;
    private int leverSince,usedAt,doorSince,presses;
    private String state="없음";

    void clear(){steps.clear();target=null;point=null;lever=null;usedLever=null;door=null;presses=0;state="없음";}
    void reset(){clear();blocked.clear();failedSwitches.clear();}
    void avoidCurrent(int now){if(point!=null)blocked.put(BlockPos.containing(point).asLong(),now+200);clear();}
    boolean active(){return target!=null&&!steps.isEmpty();}
    Vec3 point(){return point;}
    String state(){return state;}

    /**
     * Plan (when the goal moved) and advance the route. WALK: vanilla-path to {@link #point}. HOLD: stand still this
     * tick (a shutter is opening, a switch was used, a launch began). WAIT: out of search budget, retry next tick.
     * NONE: the map grid has no way there. OFF: no grid here, or the goal is near; the caller paths directly.
     */
    Result towards(BotEntity bot,ArenaMap arena,Vec3 goal,int now){
        var graph=BotNavGraph.of(arena,bot.level());
        if(graph==null||!graph.contains(bot.position())||!graph.contains(goal)){clear();return Result.OFF;}
        if(target==null||target.distanceToSqr(goal)>.25) {
            if(flat(bot.position(),goal)<FAR*FAR){clear();return Result.OFF;}
            if(BotNavGraph.searchSpent())return Result.WAIT;
            blocked.values().removeIf(until->now>=until);
            var nodes=graph.route(bot,goal,blocked.keySet());
            if(nodes==null){clear();return Result.NONE;}
            load(nodes);target=goal;
        }
        return follow(bot,arena,graph,now);
    }
    /** Waypoints every few nodes, and always just before a jump, a launch or a shutter. */
    private void load(List<BotNavGraph.Node> nodes){
        steps.clear();lever=null;usedLever=null;door=null;presses=0;
        int since=0;
        for(int i=1;i<nodes.size();i++){
            var node=nodes.get(i);var previous=nodes.get(i-1);since++;
            switch(node.kind()){
                case BotNavGraph.JUMP,BotNavGraph.LAUNCH,BotNavGraph.DROP,BotNavGraph.VAULT->{
                    steps.add(new Step(node.kind(),previous.pos(),node.pos(),null));since=0;
                }
                case BotNavGraph.DOOR->{
                    steps.add(new Step(BotNavGraph.WALK,previous.pos(),null,null));
                    steps.add(new Step(BotNavGraph.DOOR,node.pos(),null,node.cell()));since=0;
                }
                default->{if(since>=SPACING||i==nodes.size()-1){steps.add(new Step(BotNavGraph.WALK,node.pos(),null,null));since=0;}}
            }
        }
    }
    private Result follow(BotEntity bot,ArenaMap arena,BotNavGraph graph,int now){
        var level=bot.level();
        while(!steps.isEmpty()){
            var step=steps.peekFirst();
            switch(step.kind()){
                case BotNavGraph.WALK->{
                    if(flat(bot.position(),step.pos())<1.44&&Math.abs(bot.getY()-step.pos().y)<1.5){steps.removeFirst();continue;}
                    state="맵 경로 이동 (남은 "+steps.size()+"단계)";point=step.pos();return Result.WALK;
                }
                case BotNavGraph.JUMP,BotNavGraph.LAUNCH->{
                    boolean launch=step.kind()==BotNavGraph.LAUNCH;
                    // Already up there (a previous launch landed): continue.
                    if(flat(bot.position(),step.landing())<1&&Math.abs(bot.getY()-step.landing().y)<.6){steps.removeFirst();continue;}
                    double away=flat(bot.position(),step.pos());
                    if(away>.36||Math.abs(bot.getY()-step.pos().y)>.6){
                        if(away<2.25&&Math.abs(bot.getY()-step.pos().y)<.6){
                            // Vanilla path following stops half a block short; a pad has to be stood on.
                            bot.getNavigation().stop();bot.getMoveControl().setWantedPosition(step.pos().x,step.pos().y,step.pos().z,1);
                            state=launch?"점프대 위치 잡기":"점프 위치 잡기";return Result.HOLD;
                        }
                        state=launch?"점프대로 이동":"점프 지점으로 이동";point=step.pos();return Result.WALK;
                    }
                    if(!bot.onGround()){state="착지 대기";return Result.HOLD;}
                    boolean went=launch?bot.parkour.launch(bot,arena,step.landing()):bot.parkour.jump(bot,arena,step.landing());
                    steps.removeFirst();
                    if(!went){blocked.put(BlockPos.containing(step.landing()).asLong(),now+BLOCK_TICKS);clear();return Result.WAIT;}
                    state=launch?"점프대 비행":"틈 점프";return Result.HOLD;
                }
                case BotNavGraph.DROP,BotNavGraph.VAULT->{
                    Vec3 from=step.pos(),to=step.landing();boolean vault=step.kind()==BotNavGraph.VAULT;
                    // Down below the edge (or across the obstacle): carry on with the route.
                    if(bot.getY()<from.y-1.2||flat(bot.position(),to)<1&&Math.abs(bot.getY()-to.y)<1.5){steps.removeFirst();continue;}
                    if(!bot.onGround()&&!bot.isInWater()){state=vault?"장애물 넘는 중":"낙하 중";return Result.HOLD;}
                    double away=flat(bot.position(),from);
                    if(away>.36||Math.abs(bot.getY()-from.y)>.6){
                        if(away<2.25&&Math.abs(bot.getY()-from.y)<.6){
                            bot.getNavigation().stop();bot.getMoveControl().setWantedPosition(from.x,from.y,from.z,1);
                            state=vault?"넘을 위치 잡기":"뛰어내릴 위치 잡기";return Result.HOLD;
                        }
                        state=vault?"장애물로 이동":"뛰어내릴 곳으로 이동";point=from;return Result.WALK;
                    }
                    // Fall damage is disabled for bots; collision, hazards and death zones still forbid unsafe drops.
                    if(!safeDrop(bot,arena,from,to)){bot.getNavigation().stop();blocked.put(BlockPos.containing(to).asLong(),now+BLOCK_TICKS);clear();return Result.WAIT;}
                    if(!vault){
                        // Walk straight off the edge; vanilla path following refuses drops over three blocks.
                        bot.getNavigation().stop();bot.getMoveControl().setWantedPosition(to.x,from.y,to.z,1);
                        state="뛰어내리기";return Result.HOLD;
                    }
                    if(from.equals(vaultFrom)&&now-vaultAt<60||!bot.parkour.vault(bot,to)){
                        blocked.put(BlockPos.containing(to).asLong(),now+BLOCK_TICKS);clear();return Result.WAIT;
                    }
                    vaultFrom=from;vaultAt=now;state="장애물 넘기";return Result.HOLD;
                }
                case BotNavGraph.DOOR->{
                    var cell=step.cell();
                    if(!(level.getBlockEntity(cell) instanceof ShutterEntity shutter)||BotNavGraph.shutterOpen(level,cell)&&BotNavGraph.shutterOpen(level,cell.above())){
                        steps.removeFirst();door=null;lever=null;usedLever=null;presses=0;continue;
                    }
                    if(!cell.equals(door)){door=cell;doorSince=now;presses=0;}
                    if(usedLever!=null&&now-usedAt>=6){
                        // The switch did nothing for this shutter: flip a lever back and remember not to try it again.
                        if(!shutter.powered()){
                            if(level.getBlockState(usedLever).getBlock() instanceof LeverBlock)use(bot,usedLever);
                            failedSwitches.put(usedLever.asLong(),now+BLOCK_TICKS);
                        }
                        usedLever=null;
                    }
                    if(shutter.powered()||usedLever!=null){
                        if(now-doorSince>DOOR_WAIT)return give(bot,cell,now);
                        state="셔터 열림 대기";return Result.HOLD;
                    }
                    return operate(bot,graph,cell,now);
                }
                default->steps.removeFirst();
            }
        }
        state="맵 경로 도착";target=null;return Result.OFF;
    }
    /** Walk to a switch near the shutter and use it; no reachable switch marks the shutter shut for this bot. */
    private Result operate(BotEntity bot,BotNavGraph graph,BlockPos door,int now){
        failedSwitches.values().removeIf(until->now>=until);
        if(presses>=3)return give(bot,door,now);
        if(lever!=null&&(now-leverSince>SWITCH_WALK||!(bot.level().getBlockState(lever).getBlock() instanceof LeverBlock||bot.level().getBlockState(lever).getBlock() instanceof ButtonBlock))) {
            failedSwitches.put(lever.asLong(),now+BLOCK_TICKS);lever=null;leverStand=null;
        }
        if(lever==null) {
            Vec3 stand=null;
            for(var candidate:graph.switchesNear(door)) {
                if(failedSwitches.containsKey(candidate.asLong()))continue;
                var state=bot.level().getBlockState(candidate);
                // A lever already on is holding something else; pulling it would only switch that off.
                if(state.getBlock() instanceof LeverBlock&&state.getValue(LeverBlock.POWERED)||!(state.getBlock() instanceof LeverBlock||state.getBlock() instanceof ButtonBlock))continue;
                if(reach(bot,candidate)){lever=candidate;break;}
                for(var spot:graph.standsNear(candidate).stream().limit(2).toList()) {
                    if(BotMovement.spent())return Result.WAIT;
                    var path=BotMovement.path(bot,spot,0);
                    if(path!=null&&path.canReach()){lever=candidate;stand=Vec3.atBottomCenterOf(spot);break;}
                }
                if(lever!=null)break;
            }
            if(lever==null)return give(bot,door,now);
            leverSince=now;leverStand=stand;
        }
        if(reach(bot,lever)) {
            bot.getNavigation().stop();
            use(bot,lever);usedLever=lever;usedAt=now;lever=null;leverStand=null;presses++;
            state="셔터 스위치 조작";return Result.HOLD;
        }
        state="셔터 스위치로 이동";
        if(leverStand==null){var spots=graph.standsNear(lever);if(spots.isEmpty())return give(bot,door,now);leverStand=Vec3.atBottomCenterOf(spots.get(0));}
        point=leverStand;return Result.WALK;
    }
    private Result give(BotEntity bot,BlockPos door,int now){
        // The whole shutter, so the next route does not just try the tile beside this one.
        for(var pos:BlockPos.betweenClosed(door.offset(-4,-4,-4),door.offset(4,4,4)))
            if(bot.level().getBlockState(pos).getBlock() instanceof ShutterBlock)blocked.put(pos.asLong(),now+BLOCK_TICKS);
        clear();state="셔터를 열 수 없음: 우회";return Result.WAIT;
    }
    /** Recheck live hazards in the entire landing column before leaving the ledge. */
    static boolean safeDrop(BotEntity bot,ArenaMap arena,Vec3 from,Vec3 to){
        for(double y=to.y;y<=Math.max(from.y+1,to.y);y+=.25) {
            Vec3 p=new Vec3(to.x,y,to.z);
            if(BotMovement.danger(arena,bot,p)||BotMovement.knownDanger(bot,p))return false;
        }
        return bot.level().noCollision(bot,bot.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(to));
    }
    private static boolean reach(BotEntity bot,BlockPos pos){
        return bot.getEyePosition().distanceTo(Vec3.atCenterOf(pos))<=3.5;
    }
    /** Right-click with an empty hand, through a stand-in player at the bot's eyes, so sounds and redstone match a player's use. */
    private static void use(BotEntity bot,BlockPos pos){
        if(!(bot.level() instanceof ServerLevel level))return;
        var state=level.getBlockState(pos);var centre=Vec3.atCenterOf(pos);
        bot.getLookControl().setLookAt(centre.x,centre.y,centre.z);
        var hand=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        hand.moveTo(bot.getX(),bot.getY(),bot.getZ(),bot.getYRot(),bot.getXRot());
        hand.setItemInHand(InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        Direction face=Direction.getNearest(bot.getEyePosition().x-centre.x,bot.getEyePosition().y-centre.y,bot.getEyePosition().z-centre.z);
        state.use(level,hand,InteractionHand.MAIN_HAND,new BlockHitResult(centre,face,pos,false));
        bot.swing(InteractionHand.MAIN_HAND);
    }
    private static double flat(Vec3 a,Vec3 b){double x=a.x-b.x,z=a.z-b.z;return x*x+z*z;}
}
