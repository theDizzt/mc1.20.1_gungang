package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Bot locomotion. Travel: sampled waypoints between the spawns, kept until reached or stuck, so the bot does not
 * pace one line or re-path every half second. Combat: hold a distance band and strafe instead of standing still.
 * Work per call is bounded: each selection stage tries at most {@link #ATTEMPTS} paths within the shared tick budget.
 */
final class BotMovement {
    private static final int ATTEMPTS=3,STUCK_JUMP=40,STUCK_GIVE_UP=60;
    /** Gun fights happen inside this range; farther targets are approached along a path. */
    private BotCombatProfile profile=BotCombatProfile.RIFLE;
    void profile(BotCombatProfile value){profile=value;}
    private Vec3 goal,progressPos;
    private boolean chasing,strafing;
    private int progressAt,strafeDir=1,nextFlip;
    private Vec3 reposition;
    private int repositionUntil,nextReposition;

    Vec3 goal() {return goal;}
    private String failureReason="없음";
    private Vec3 failurePoint;
    private int failureAt;
    String failureReason(){return failureReason;}
    Vec3 failurePoint(){return failurePoint;}
    int failureAt(){return failureAt;}
    Vec3 diagnosticGoal(BotEntity bot){var path=bot.getNavigation().getPath();return path!=null&&!path.isDone()?Vec3.atBottomCenterOf(path.getTarget()):pendingMove||exit!=null?goal:null;}
    String navigationState(BotEntity bot){return pendingMove?"경로 예산 대기":leavingSpawn()?"스폰 출구 이동":bot.getNavigation().isDone()?"경로 종료/대기":"이동 중";}
    private void diagnose(Vec3 point,int now,String reason){failurePoint=point;failureAt=now;failureReason=reason;}
    private record FailedRoute(Vec3 from,Vec3 to,int until) { }
    private final java.util.ArrayDeque<FailedRoute> failedRoutes=new java.util.ArrayDeque<>();
    final BotRouteMemory routeMemory=new BotRouteMemory();
    void hit(BotEntity bot,float damage,int now) {
        routeMemory.hit(bot.getX(),bot.getY(),bot.getZ(),damage/Math.max(1,bot.getMaxHealth()),now);
        pendingMove=true; // Reconsider the current route on the next travel decision.
    }
    double routeRisk(Vec3 point,int now){return routeMemory.risk(point.x,point.y,point.z,now);}
    /** Retry after ten seconds, or sooner from a substantially different approach. */
    private boolean rejected(Vec3 from,Vec3 to,int now) {
        failedRoutes.removeIf(f->now>=f.until());
        return failedRoutes.stream().anyMatch(f->f.from().distanceToSqr(from)<64&&f.to().distanceToSqr(to)<9);
    }
    private void failed(Vec3 from,Vec3 to,int now) {
        failed(from,to,now,"목적지까지 도달 가능한 경로 없음");
    }
    private void failed(Vec3 from,Vec3 to,int now,String reason) {
        if(to==null)return;
        diagnose(to,now,reason);
        failedRoutes.removeIf(f->now>=f.until());
        if(failedRoutes.size()>=32)failedRoutes.removeFirst();
        failedRoutes.addLast(new FailedRoute(from,to,now+200));
    }
    /**
     * All bots together compute at most this many paths per server tick. Several bots thinking on one tick used to
     * stack a dozen searches into a single 60 ms spike; the rest now wait a tick instead of failing.
     */
    static final int PATHS_PER_TICK=8;
    private static int pathBudget=PATHS_PER_TICK;
    /** Stay inside the bot's loaded neighbourhood, even when the opposing spawn is hundreds of blocks away. */
    private static final double TRAVEL_STEP=24;
    private static Vec3 nearby(BotEntity bot,Vec3 target) {
        Vec3 delta=target.subtract(bot.position());double distance=delta.horizontalDistance();
        return distance<=TRAVEL_STEP?target:bot.position().add(delta.scale(TRAVEL_STEP/distance));
    }
    static void newTick() {pathBudget=PATHS_PER_TICK;}
    /** The budget ran out this tick: callers retry next tick rather than treating "no path" as a real failure. */
    static boolean spent() {return pathBudget<=0;}
    static int remainingPaths(){return pathBudget;}
    static net.minecraft.world.level.pathfinder.Path path(BotEntity bot,BlockPos target,int accuracy) {
        if(pathBudget<=0)return null;
        pathBudget--;return bot.getNavigation().createPath(target,accuracy);
    }
    private boolean pendingMove,findExit,departingSpawn;
    /** Standing spots just outside each team's own spawn gates, scanned once per map and team. */
    private static final java.util.Map<String,java.util.List<Vec3>> EXITS=new java.util.HashMap<>();
    static void forgetExits() {EXITS.clear();}
    private Vec3 exit;
    private int nextExitScan;
    void prepareSpawnExit() {findExit=true;departingSpawn=true;nextExitScan=0;}
    boolean leavingSpawn() {return exit!=null||departingSpawn;}
    private record ExitScan(java.util.List<Vec3> points,boolean complete) { }
    /** Resume the spawn route every tick, independently of the normal patrol path interval. */
    boolean exitSpawn(BotEntity bot,ArenaMap arena,String team,int now) {
        if(departingSpawn&&clearOfSpawn(arena,team,bot.position()))departingSpawn=false;
        if(findExit&&now>=nextExitScan)leaveSpawn(bot,arena,team);
        if(exit==null&&!departingSpawn)return false;
        // Keep advancing beyond the door. With no mapped gate, short local routes can still leave a winding room.
        travel(bot,arena,team,null,now,exit==null);
        return exit!=null||departingSpawn;
    }
    /** After a respawn, walk out through the nearest reachable own-team gate before patrolling. */
    void leaveSpawn(BotEntity bot,ArenaMap arena,String team) {
        int now=BotManager.server().getTickCount();
        if(findExit&&now<nextExitScan)return;
        nextExitScan=now+5;
        // GroundPathNavigation cannot build a route before the first physics tick establishes support.
        // Defer that case instead of permanently concluding that every exit is unreachable.
        if(!bot.onGround()&&!bot.isInWater()){findExit=true;return;}
        exit=null;findExit=false;
        String key=arena.id()+"/"+arena.dimension()+"/"+team;
        var exits=EXITS.get(key);
        if(exits==null) {
            var scan=scanExits(bot,arena,team);exits=scan.points();
            if(scan.complete()) {if(!exits.isEmpty())EXITS.put(key,exits);}
            else findExit=true;
        }
        for(var candidate:exits.stream().sorted(java.util.Comparator.comparingDouble(e->e.distanceToSqr(bot.position()))).limit(4).toList()) {
            var path=path(bot,BlockPos.containing(candidate),0);
            if(path!=null&&path.canReach()) {findExit=false;exit=candidate;goal=candidate;chasing=false;bot.getNavigation().moveTo(path,1);return;}
            if(spent()) {findExit=true;return;}
            diagnose(candidate,now,"스폰 출구 경로 탐색 실패");
        }
    }
    private static ExitScan scanExits(BotEntity bot,ArenaMap arena,String team) {
        var level=bot.level();var room=team.equals("ALPHA")?arena.alphaArea():arena.bravoArea();
        var spawn=team.equals("ALPHA")?arena.alphaSpawn():arena.bravoSpawn();
        Vec3 origin=new Vec3(spawn.x(),spawn.y(),spawn.z());
        // Old/programmatically relocated maps may retain an area around the previous spawn.
        // Such an area must not exclude the actual doorway from the search.
        if(room!=null&&!room.contains(origin.x,origin.y,origin.z))room=null;
        // The spawn room plus a two-block rim (gates sit in its walls); without a room, 24 blocks around the spawn. Capped either way.
        int x0,x1,y0,y1,z0,z1;
        if(room!=null) {x0=Mth.floor(room.minX())-2;x1=Mth.floor(room.maxX())+2;y0=Mth.floor(room.minY())-1;y1=Mth.floor(room.maxY())+1;z0=Mth.floor(room.minZ())-2;z1=Mth.floor(room.maxZ())+2;}
        else {x0=Mth.floor(origin.x)-24;x1=Mth.floor(origin.x)+24;y0=Mth.floor(origin.y)-4;y1=Mth.floor(origin.y)+6;z0=Mth.floor(origin.z)-24;z1=Mth.floor(origin.z)+24;}
        x0=Math.max(x0,Mth.floor(origin.x)-48);x1=Math.min(x1,Mth.floor(origin.x)+48);z0=Math.max(z0,Mth.floor(origin.z)-48);z1=Math.min(z1,Mth.floor(origin.z)+48);
        y0=Math.max(y0,Mth.floor(origin.y)-12);y1=Math.min(y1,Mth.floor(origin.y)+12);
        var exits=new java.util.ArrayList<Vec3>();var pos=new BlockPos.MutableBlockPos();
        // Only chunk sections whose palette may hold a gate are scanned block by block; a wide room no longer
        // means hundreds of thousands of block reads in one tick.
        var sections=new java.util.ArrayList<int[]>();
        boolean complete=true;
        for(int cx=x0>>4;cx<=x1>>4;cx++)for(int cz=z0>>4;cz<=z1>>4;cz++) {
            // A region ticket loads asynchronously. Never cache a partial scan as "no spawn gate".
            var chunk=((net.minecraft.server.level.ServerLevel)level).getChunkSource().getChunkNow(cx,cz);
            if(chunk==null){complete=false;continue;}
            for(int sy=y0>>4;sy<=y1>>4;sy++) {
                int index=chunk.getSectionIndexFromSectionY(sy);if(index<0||index>=chunk.getSections().length)continue;
                var section=chunk.getSection(index);
                if(!section.hasOnlyAir()&&section.maybeHas(state->state.getBlock() instanceof SpawnGateBlock))sections.add(new int[]{cx,sy,cz});
            }
        }
        for(var section:sections)
        for(int x=Math.max(x0,section[0]<<4);x<=Math.min(x1,(section[0]<<4)+15);x++)
        for(int z=Math.max(z0,section[2]<<4);z<=Math.min(z1,(section[2]<<4)+15);z++)
        for(int y=Math.max(y0,section[1]<<4);y<=Math.min(y1,(section[1]<<4)+15);y++) {
            pos.set(x,y,z);
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof SpawnGateBlock gate)||!gate.present(state)||!gate.allows(bot))continue;
            // Only the bottom block of a gate column is where feet pass.
            var below=level.getBlockState(pos.below());
            if(below.getBlock() instanceof SpawnGateBlock under&&under.present(below))continue;
            double inside=Vec3.atBottomCenterOf(pos).distanceToSqr(origin);
            for(var direction:Direction.Plane.HORIZONTAL) {
                var out=pos.relative(direction,2);Vec3 point=Vec3.atBottomCenterOf(out);
                boolean outward=room!=null?!room.contains(point.x,point.y+.1,point.z):point.distanceToSqr(origin)>inside;
                if(!outward)continue;
                var ground=ground(bot,arena,point);if(ground==null)continue;
                Vec3 spot=Vec3.atBottomCenterOf(ground);
                if(exits.stream().noneMatch(e->e.distanceToSqr(spot)<2.25))exits.add(spot);
            }
        }
        return new ExitScan(exits,complete);
    }
    private boolean searching;
    private float searchYaw;
    /** Stand and sweep the view ±90° over two seconds, the way a player checks where an enemy disappeared. */
    void search(BotEntity bot,int now,int since) {
        if(!searching||now==since) {searching=true;searchYaw=bot.getYRot();bot.getNavigation().stop();}
        stopStrafe(bot);progressPos=null;
        double angle=Math.toRadians(searchYaw+90*Math.sin((now-since)*Math.PI/20));
        Vec3 eye=bot.getEyePosition();
        bot.getLookControl().setLookAt(eye.x-Math.sin(angle)*8,eye.y,eye.z+Math.cos(angle)*8);
    }
    void reset() {failureReason="없음";failurePoint=null;failureAt=0;failedRoutes.clear();recent.clear();nextExitScan=0;searching=false;exit=null;pendingMove=false;findExit=false;departingSpawn=false;goal=null;progressPos=null;chasing=false;strafing=false;nextFlip=0;reposition=null;nextReposition=0;}
    /** Another system (health packs) is steering; do not count that time as being stuck. */
    void interrupt() {progressPos=null;strafing=false;}
    /** Any other behaviour taking over the legs ends a strafe cleanly. */
    void halt(BotEntity bot) {stopStrafe(bot);reposition=null;bot.getNavigation().stop();}

    /** Returns true when the chase point (last seen enemy) was reached or abandoned, so the caller can forget it. */
    boolean travel(BotEntity bot,ArenaMap arena,String team,Vec3 chase,int now) {
        return travel(bot,arena,team,chase,now,true);
    }
    /** Melee uses the shared path budget and failed-route memory, without picking a patrol on arrival. */
    void pursue(BotEntity bot,ArenaMap arena,String team,LivingEntity target,int now) {
        if(bot.distanceToSqr(target)<4) {
            halt(bot);goal=null;exit=null;chasing=false;pendingMove=false;progressPos=null;
            return;
        }
        travel(bot,arena,team,target.position(),now,false);
    }
    private boolean travel(BotEntity bot,ArenaMap arena,String team,Vec3 chase,int now,boolean patrolAfter) {
        stopStrafe(bot);searching=false;reposition=null;
        if(findExit)leaveSpawn(bot,arena,team);
        var nav=bot.getNavigation();boolean changed=false,doneChasing=false;
        if(chase!=null&&rejected(bot.position(),chase,now)) {
            nav.stop();goal=null;chasing=false;pendingMove=false;progressPos=null;return true;
        }
        if(goal!=null&&nav.isDone()&&bot.parkour.start(bot,arena,goal,now))return false;
        // Leaving the spawn room comes first; only a sighting outranks it.
        if(exit!=null&&chase==null) {
            if(flat(bot.position(),exit)<.36&&Math.abs(bot.getY()-exit.y)<1.5) {exit=null;goal=null;}
            else if(goal!=exit) {goal=exit;chasing=false;changed=true;}
        } else if(chase!=null)exit=null;
        if(chase!=null&&(!chasing||goal==null||goal.distanceToSqr(chase)>4)) {goal=chase;chasing=true;changed=true;}
        else if(chase==null&&chasing) {goal=null;chasing=false;}
        if(goal!=null&&goal!=exit&&flat(bot.position(),goal)<4&&Math.abs(bot.getY()-goal.y)<1.5) {doneChasing=chasing;goal=null;chasing=false;}
        if(progressPos==null||bot.position().distanceToSqr(progressPos)>1) {progressPos=bot.position();progressAt=now;}
        else if(now-progressAt>=STUCK_GIVE_UP&&!pendingMove) {failed(bot.position(),goal,now,"3초간 위치 변화 부족: 경로 포기");nav.stop();doneChasing|=chasing;goal=null;exit=null;chasing=false;progressAt=now;}
        else if(now-progressAt>=STUCK_JUMP&&bot.onGround())bot.getJumpControl().jump();
        // Arrival picks the next waypoint in the same call, so there is no idle gap.
        if(goal==null&&patrolAfter) {goal=pick(bot,arena,team,now);changed=true;}
        if(goal==null) {if(patrolAfter&&!spent())diagnose(bot.position(),now,"도달 가능한 순찰 후보 없음");nav.stop();pendingMove=false;progressPos=null;return doneChasing;}
        if(changed||nav.isDone()||pendingMove) {
            if(spent())pendingMove=true;
            else {
                if(pendingMove)nav.stop(); // Drop the cached path so newly learned danger changes the route.
                pathBudget--;pendingMove=false;
                var step=nearby(bot,goal);
                var ground=ground(bot,arena,step);
                boolean moved=false;
                if(ground!=null) {
                    if(exit!=null||chase==null) {var route=nav.createPath(ground,0);moved=route!=null&&route.canReach()&&nav.moveTo(route,1);}
                    else moved=nav.moveTo(ground.getX()+.5,ground.getY(),ground.getZ()+.5,1);
                }
                if(!moved) {
                    if(bot.parkour.start(bot,arena,goal,now))return false;
                    failed(bot.position(),goal,now,ground==null?"목적지 주변에 안전한 지면 없음":"경로가 막혔거나 목적지까지 이어지지 않음");nav.stop();doneChasing|=chasing;goal=null;exit=null;chasing=false;progressPos=null;
                }
            }
        }
        return doneChasing;
    }

    /**
     * MoveControl clears only the forward input when a strafe ends; the sideways input would linger and keep the
     * bot drifting in circles (e.g. after switching to a knife).
     */
    private void stopStrafe(BotEntity bot) {if(strafing) {bot.getMoveControl().strafe(0,0);bot.setXxa(0);bot.setZza(0);}strafing=false;}
    /** Strafe while the target is in gun range. Called every tick; returns false when path travel should drive instead. */
    boolean combat(BotEntity bot,ArenaMap arena,LivingEntity target,int now,boolean melee,boolean retreat) {
        double distance=bot.distanceTo(target);
        searching=false;
        if(melee||distance>profile.engage()) {stopStrafe(bot);reposition=null;return false;}
        if(reposition!=null) {
            if(now<repositionUntil&&bot.position().distanceToSqr(reposition)>.64&&!bot.getNavigation().isDone())return true;
            reposition=null;bot.getNavigation().stop();
        }
        progressPos=null;
        var random=bot.getRandom();
        if(!strafing) {strafing=true;bot.getNavigation().stop();strafeDir=random.nextBoolean()?1:-1;nextFlip=now+12+random.nextInt(20);}
        float yaw=(float)(Math.atan2(target.getZ()-bot.getZ(),target.getX()-bot.getX())*180/Math.PI)-90;
        bot.setYRot(yaw);bot.yBodyRot=yaw;
        // Behind a raised shield the bot backs off instead of holding its distance.
        float forward=retreat?-.5f:distance>profile.far()?.6f:distance<profile.near()?-.5f:0;
        float sideways=profile.strafe();
        if(now>=nextFlip||bot.horizontalCollision) {
            strafeDir=-strafeDir;nextFlip=now+12+random.nextInt(20);
        }
        boolean blocked=unsafe(bot,arena,yaw,forward,strafeDir*sideways);
        if(blocked) {
            strafeDir=-strafeDir;nextFlip=now+12+random.nextInt(20);
            blocked=unsafe(bot,arena,yaw,forward,strafeDir*sideways);
        }
        if(blocked) {
            stopStrafe(bot);
            bot.getNavigation().stop();
            if(now>=nextReposition) {
                nextReposition=now+20;
                reposition=repositionSpot(bot,arena,target.position());
                if(reposition!=null&&!spent()&&pathBudget-->0&&bot.getNavigation().moveTo(reposition.x,reposition.y,reposition.z,1))repositionUntil=now+50;
                else reposition=null;
            }
            return true;
        }
        // Players cannot sprint sideways, so strafing runs at walking pace. Strafe moves speedModifier×attribute×|input|
        // per tick while path walking moves attribute², so walking (sprint/1.3) needs this modifier.
        double attribute=bot.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double input=Math.max(.55,Math.sqrt(forward*forward+sideways*sideways));
        bot.getMoveControl().setWantedPosition(bot.getX(),bot.getY(),bot.getZ(),attribute/(1.3*input));
        bot.getMoveControl().strafe(forward,strafeDir*sideways);
        return true;
    }

    /** Allies' positions, refreshed by the bot each think: waypoints keep their distance, cover prefers their company. */
    private java.util.List<Vec3> allies=java.util.List.of();
    void allies(java.util.List<Vec3> positions) {allies=positions;}
    private final java.util.ArrayDeque<Vec3> recent=new java.util.ArrayDeque<>();

    /**
     * Cover: a reachable standing spot that every known threat's eye line to our head runs into a block, scored by
     * walking distance, not moving toward the threats, and being near an ally. Candidates are mapped COVER waypoints
     * within 16 blocks plus 12 samples around the bot. Bounded: at most 3 path searches.
     */
    record Cover(Vec3 spot,net.minecraft.world.level.pathfinder.Path path) { }
    /** Anticipate the opposing approach using map geometry, never hidden enemy positions. */
    Cover scoutCover(BotEntity bot,ArenaMap arena,String team) {
        var own=team.equals("ALPHA")?arena.alphaSpawn():arena.bravoSpawn();
        var enemy=team.equals("ALPHA")?arena.bravoSpawn():arena.alphaSpawn();
        var room=team.equals("ALPHA")?arena.alphaArea():arena.bravoArea();
        Vec3 origin=new Vec3(own.x(),own.y(),own.z());
        if(!clearOfSpawn(arena,team,bot.position())||spent())return null;
        Vec3 direction=new Vec3(enemy.x()-bot.getX(),0,enemy.z()-bot.getZ()).normalize();
        // Local approach probes avoid tracing across unloaded chunks on large maps.
        Vec3 front=bot.position().add(direction.scale(12)).add(0,1.6,0);
        Vec3 side=new Vec3(-direction.z,0,direction.x).scale(4);
        var cover=findCover(bot,arena,team,java.util.List.of(front,front.add(side),front.subtract(side)));
        if(cover==null||!clearOfSpawn(arena,team,cover.spot())||crowded(cover.spot())||knownDanger(bot,cover.spot())
                ||room!=null&&room.contains(cover.spot().x,cover.spot().y+.1,cover.spot().z))return null;
        return cover;
    }
    Cover findCover(BotEntity bot,ArenaMap arena,String team,java.util.List<Vec3> threats) {
        if(threats.isEmpty())return null;
        var level=bot.level();var random=bot.getRandom();
        var candidates=new java.util.ArrayList<Vec3>();
        candidates.add(bot.position());
        for(var point:BotWaypoints.of(arena.id(),BotWaypoints.Type.COVER,team))if(point.position().distanceToSqr(bot.position())<=256)candidates.add(point.position());
        for(int i=0;i<12;i++) {
            double angle=Math.PI*2*i/12+random.nextDouble()*.3,radius=3+random.nextDouble()*7;
            var ground=ground(bot,arena,bot.position().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
            if(ground!=null)candidates.add(Vec3.atBottomCenterOf(ground));
        }
        Vec3 centre=centre(threats);double current=bot.position().distanceTo(centre);
        record Scored(Vec3 spot,double cost) { }
        var hidden=new java.util.ArrayList<Scored>();
        for(var spot:candidates) {
            if(!hidden(bot,spot,threats)||!safeSegment(bot,arena,spot,spot))continue;
            double cost=Math.sqrt(spot.distanceToSqr(bot.position()));
            cost+=Math.max(0,current-spot.distanceTo(centre))*3;
            if(allies.stream().anyMatch(a->a.distanceToSqr(spot)<64))cost-=3;
            hidden.add(new Scored(spot,cost));
        }
        hidden.sort(java.util.Comparator.comparingDouble(Scored::cost));
        // Refine only six candidates: a firing edge and an escape lane matter more than a nearby dead end.
        var refined=new java.util.ArrayList<Scored>();
        int now=bot.getServer().getTickCount();
        for(var choice:hidden.stream().limit(6).toList()) {
            var quality=coverQuality(bot,arena,choice.spot(),threats);
            double cost=choice.cost()+(quality.exits()==0?16:-3*quality.exits())
                    +(quality.firingEdge()? -8:4)+routeRisk(choice.spot(),now)*6;
            refined.add(new Scored(choice.spot(),cost));
        }
        refined.sort(java.util.Comparator.comparingDouble(Scored::cost));
        for(var choice:refined.stream().limit(3).toList()) {
            if(choice.spot().distanceToSqr(bot.position())<.01)return new Cover(choice.spot(),null);
            var path=path(bot,BlockPos.containing(choice.spot()),0);
            if(path!=null&&path.canReach()&&path.getNodeCount()<=20)return new Cover(choice.spot(),path);
        }
        return null;
    }
    record CoverQuality(boolean firingEdge,int exits) { }
    CoverQuality coverQuality(BotEntity bot,ArenaMap arena,Vec3 spot,java.util.List<Vec3> threats) {
        if(threats.isEmpty())return new CoverQuality(false,0);
        Vec3 enemy=centre(threats),away=spot.subtract(enemy).multiply(1,0,1).normalize();
        Vec3 side=new Vec3(-away.z,0,away.x);int exits=0;
        for(int sign:new int[]{0,-1,1}) {
            Vec3 end=spot.add(away.scale(3)).add(side.scale(sign*2));
            if(safeSegment(bot,arena,spot,end)&&hidden(bot,end,threats))exits++;
        }
        return new CoverQuality(peekSpot(bot,arena,spot,enemy.add(0,-1.6,0))!=null,exits);
    }
    boolean hidden(BotEntity bot,Vec3 spot,java.util.List<Vec3> threats) {
        return !threats.isEmpty()&&threats.stream().allMatch(eye->
                blocked(bot.level(),eye,spot.add(0,1.6,0))&&blocked(bot.level(),eye,spot.add(0,.9,0)));
    }
    /** Only use a nearby firing edge with a safe, reversible route back to the same cover. */
    Vec3 peekSpot(BotEntity bot,ArenaMap arena,Vec3 cover,Vec3 enemy) {
        Vec3 facing=enemy.subtract(cover).multiply(1,0,1).normalize();
        Vec3 side=new Vec3(-facing.z,0,facing.x);
        for(double radius:new double[]{1.5,2.5,3.5})for(int sign:new int[]{1,-1}) {
            Vec3 spot=cover.add(side.scale(radius*sign));
            if(!safeSegment(bot,arena,cover,spot)||!safeSegment(bot,arena,spot,cover))continue;
            if(!blocked(bot.level(),spot.add(0,1.6,0),enemy.add(0,1.4,0)))return spot;
        }
        return null;
    }
    /** Fall back away from the threats, leaning toward the nearest ally; our spawn when nothing closer works. */
    Vec3 retreatSpot(BotEntity bot,ArenaMap arena,String team,java.util.List<Vec3> threats) {
        Vec3 away=threats.isEmpty()?Vec3.ZERO:bot.position().subtract(centre(threats));
        away=new Vec3(away.x,0,away.z);
        var ally=allies.stream().min(java.util.Comparator.comparingDouble(a->a.distanceToSqr(bot.position()))).orElse(null);
        if(ally!=null)away=away.normalize().add(new Vec3(ally.x-bot.getX(),0,ally.z-bot.getZ()).normalize().scale(.6));
        if(away.lengthSqr()<1e-4) {var spawn=team.equals("ALPHA")?arena.alphaSpawn():arena.bravoSpawn();away=new Vec3(spawn.x()-bot.getX(),0,spawn.z()-bot.getZ());}
        double base=Math.atan2(away.z,away.x);var random=bot.getRandom();
        for(int i=0;i<4;i++) {
            double angle=base+(random.nextDouble()*2-1)*.7,radius=8+random.nextDouble()*6;
            var ground=ground(bot,arena,bot.position().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));if(ground==null)continue;
            var path=path(bot,ground,1);
            if(path!=null&&path.canReach())return Vec3.atBottomCenterOf(ground);
        }
        var spawn=team.equals("ALPHA")?arena.alphaSpawn():arena.bravoSpawn();
        return new Vec3(spawn.x(),spawn.y(),spawn.z());
    }
    /** A spot beside an ally (not on top of it), on the side away from the danger, so the two cover different angles. */
    Vec3 besideAlly(BotEntity bot,ArenaMap arena,LivingEntity ally,Vec3 danger) {
        Vec3 facing=danger!=null?danger.subtract(ally.position()):ally.getLookAngle();
        facing=new Vec3(facing.x,0,facing.z);if(facing.lengthSqr()<1e-4)facing=new Vec3(1,0,0);
        facing=facing.normalize();Vec3 side=new Vec3(-facing.z,0,facing.x);
        int first=bot.getRandom().nextBoolean()?1:-1;
        for(int s:new int[]{first,-first}) {
            var ground=ground(bot,arena,ally.position().add(side.scale(4*s)).subtract(facing.scale(1.5)));if(ground==null)continue;
            var path=path(bot,ground,1);
            if(path!=null&&path.canReach())return Vec3.atBottomCenterOf(ground);
        }
        return null;
    }
    /**
     * Supporting a fighting ally from another angle: a reachable spot 10–16 blocks from the enemy, 60–80° off the
     * ally's line of fire, with a clear view of the enemy's position.
     */
    Vec3 flankSpot(BotEntity bot,ArenaMap arena,LivingEntity ally,Vec3 enemy) {
        Vec3 line=new Vec3(ally.getX()-enemy.x,0,ally.getZ()-enemy.z);if(line.lengthSqr()<1e-4)return null;
        double base=Math.atan2(line.z,line.x);var random=bot.getRandom();var level=bot.level();
        var options=new java.util.ArrayList<Vec3>();
        for(int s=-1;s<=1;s+=2)for(int i=0;i<2;i++) {
            double angle=base+s*Math.toRadians(60+random.nextDouble()*20),radius=10+random.nextDouble()*6;
            var ground=ground(bot,arena,enemy.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));if(ground==null)continue;
            Vec3 spot=Vec3.atBottomCenterOf(ground);
            if(!blocked(level,spot.add(0,1.6,0),enemy.add(0,1.4,0)))options.add(spot);
        }
        options.sort(java.util.Comparator.comparingDouble(o->o.distanceToSqr(bot.position())));
        for(var spot:options.stream().limit(2).toList()) {
            var path=path(bot,BlockPos.containing(spot),1);
            if(path!=null&&path.canReach())return spot;
        }
        return null;
    }
    /** Out of a danger radius: a reachable spot 3 blocks past its edge, heading away from the centre first. */
    Vec3 escape(BotEntity bot,ArenaMap arena,Vec3 centre,double radius) {
        Vec3 away=new Vec3(bot.getX()-centre.x,0,bot.getZ()-centre.z);
        double base=away.lengthSqr()<1e-4?bot.getRandom().nextDouble()*Math.PI*2:Math.atan2(away.z,away.x);
        for(double turn:new double[]{0,.6,-.6,1.2,-1.2,Math.PI}) {
            double angle=base+turn;
            var ground=ground(bot,arena,centre.add(Math.cos(angle)*(radius+3),bot.getY()-centre.y,Math.sin(angle)*(radius+3)));if(ground==null)continue;
            var path=path(bot,ground,1);
            if(path!=null&&path.canReach())return Vec3.atBottomCenterOf(ground);
        }
        return null;
    }
    private static boolean blocked(net.minecraft.world.level.Level level,Vec3 from,Vec3 to) {
        return level.clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,null))
                .getType()==net.minecraft.world.phys.HitResult.Type.BLOCK;
    }
    private static Vec3 centre(java.util.List<Vec3> points) {
        double x=0,y=0,z=0;for(var p:points){x+=p.x;y+=p.y;z+=p.z;}
        return new Vec3(x/points.size(),y/points.size(),z/points.size());
    }
    /** Same displacement MoveControl uses for strafing; refuse steps toward a drop or a death zone. */
    private static boolean unsafe(BotEntity bot,ArenaMap arena,float yaw,float forward,float right) {
        double sin=Math.sin(yaw*Math.PI/180),cos=Math.cos(yaw*Math.PI/180);
        double dx=right*cos-forward*sin,dz=forward*cos+right*sin,length=Math.max(1e-4,Math.sqrt(dx*dx+dz*dz));
        Vec3 ahead=bot.position().add(dx/length*1.5,0,dz/length*1.5);
        return !safeSegment(bot,arena,bot.position(),ahead);
    }

    /** Short, level moves: check the whole body and support along the route, not only its endpoint. */
    static boolean safeSegment(BotEntity bot,ArenaMap arena,Vec3 from,Vec3 to) {
        if(Math.abs(from.y-to.y)>.6)return false;
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.25));
        for(int i=1;i<=steps;i++) {
            Vec3 p=from.lerp(to,i/(double)steps);
            var box=bot.getDimensions(Pose.STANDING).makeBoundingBox(p);
            if(!bot.level().hasChunkAt(BlockPos.containing(p))||danger(arena,bot,p)
                    ||knownDanger(bot,p)
                    ||!bot.level().noCollision(bot,box)||bot.level().noCollision(bot,box.move(0,-.6,0)))return false;
        }
        return true;
    }
    static boolean knownDanger(BotEntity bot,Vec3 p) {
        var agent=BotManager.agent(bot.getUUID());
        if(agent==null)return false;
        double half=bot.getBbWidth()/2;
        for(double x:new double[]{p.x-half,p.x,p.x+half})for(double z:new double[]{p.z-half,p.z,p.z+half})
            if(agent.perception.avoid(x,p.y,z))return true;
        return false;
    }
    private Vec3 repositionSpot(BotEntity bot,ArenaMap arena,Vec3 enemy) {
        Vec3 away=bot.position().subtract(enemy).multiply(1,0,1).normalize();
        Vec3 side=new Vec3(-away.z,0,away.x);
        for(Vec3 offset:new Vec3[]{side.scale(3*strafeDir),side.scale(-3*strafeDir),away.scale(3)}) {
            var ground=ground(bot,arena,bot.position().add(offset));if(ground==null)continue;
            Vec3 spot=Vec3.atBottomCenterOf(ground);
            if(!safeSegment(bot,arena,bot.position(),spot))continue;
            var path=path(bot,ground,0);
            if(path!=null&&path.canReach())return spot;
        }
        return null;
    }

    private Vec3 pick(BotEntity bot,ArenaMap arena,String team,int now) {
        var mapped=pickWaypoint(bot,arena,team,now);if(mapped!=null)return mapped;
        var own=team.equals("ALPHA")?arena.alphaSpawn():arena.bravoSpawn();
        var enemy=team.equals("ALPHA")?arena.bravoSpawn():arena.alphaSpawn();
        Vec3 a=new Vec3(own.x(),own.y(),own.z()),b=new Vec3(enemy.x(),enemy.y(),enemy.z()),d=b.subtract(a);
        double length=Math.max(1,d.horizontalDistance());Vec3 side=new Vec3(-d.z/length,0,d.x/length);
        var random=bot.getRandom();
        for(int i=0;i<ATTEMPTS;i++) {
            // Anywhere from just past our spawn to the enemy's, spread across the map's width.
            double along=.2+random.nextDouble()*.8,across=(random.nextDouble()*2-1)*Math.max(6,length*.35);
            var ground=ground(bot,arena,nearby(bot,a.add(d.scale(along)).add(side.scale(across))));
            if(ground==null||flat(bot.position(),Vec3.atBottomCenterOf(ground))<36||crowded(Vec3.atBottomCenterOf(ground)))continue;
            var point=Vec3.atBottomCenterOf(ground);
            if(nearSpawn(arena,team,point))continue;
            if(rejected(bot.position(),point,now)||routeRisk(point,now)>.5||recent.stream().anyMatch(r->r.distanceToSqr(point)<64))continue;
            if(spent())return null;
            var path=path(bot,ground,1);
            if(path!=null&&path.canReach()) {remember(point);return point;}
            failed(bot.position(),point,now);
        }
        // A wall toward the enemy must lead to a local side route, not repeated partial paths into that wall.
        return localPatrol(bot,arena,team,now,a,b);
    }
    static boolean nearSpawn(ArenaMap arena,String team,Vec3 point) {
        var spawn=team.equals("ALPHA")?arena.alphaSpawn():arena.bravoSpawn();
        var room=team.equals("ALPHA")?arena.alphaArea():arena.bravoArea();
        return flat(point,new Vec3(spawn.x(),spawn.y(),spawn.z()))<64
                ||room!=null&&room.contains(spawn.x(),spawn.y(),spawn.z())&&room.contains(point.x,point.y,point.z);
    }
    static boolean clearOfSpawn(ArenaMap arena,String team,Vec3 point){
        var spawn=team.equals("ALPHA")?arena.alphaSpawn():arena.bravoSpawn();
        var enemy=team.equals("ALPHA")?arena.bravoSpawn():arena.alphaSpawn();
        Vec3 origin=new Vec3(spawn.x(),spawn.y(),spawn.z());
        double radius=Math.min(24,Math.max(8,Math.sqrt(flat(origin,new Vec3(enemy.x(),enemy.y(),enemy.z())))*.4));
        return !nearSpawn(arena,team,point)&&flat(point,origin)>=radius*radius;
    }
    /** Idle teammates must not pull each other back toward spawn or chase one another in circles. */
    static boolean forwardAlly(ArenaMap arena,String team,Vec3 bot,Vec3 ally){
        var enemy=team.equals("ALPHA")?arena.bravoSpawn():arena.alphaSpawn();
        Vec3 front=new Vec3(enemy.x(),enemy.y(),enemy.z());
        return !nearSpawn(arena,team,ally)&&Math.sqrt(flat(ally,front))+8<Math.sqrt(flat(bot,front));
    }
    Vec3 localPatrol(BotEntity bot,ArenaMap arena,String team,int now,Vec3 own,Vec3 enemy) {
        var candidates=new java.util.ArrayList<Vec3>();
        double heading=Math.atan2(enemy.z-bot.getZ(),enemy.x-bot.getX());
        boolean inside=nearSpawn(arena,team,bot.position());
        for(double radius:new double[]{3,6,10,16})for(int i=0;i<12;i++) {
            double angle=heading+i*Math.PI/6;
            var ground=ground(bot,arena,bot.position().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
            if(ground==null)continue;
            Vec3 point=Vec3.atBottomCenterOf(ground);
            // A route through a spawn corridor is necessary to reach its door; only forbid returning after leaving.
            if(!inside&&nearSpawn(arena,team,point)||rejected(bot.position(),point,now)||knownDanger(bot,point))continue;
            candidates.add(point);
        }
        candidates.sort(java.util.Comparator.comparingDouble(p->Math.sqrt(flat(p,enemy))*.15
                +Math.max(0,24-Math.sqrt(flat(p,own)))*2+routeRisk(p,now)*8
                +(crowded(p)?10:0)+(recent.stream().anyMatch(r->r.distanceToSqr(p)<64)?20:0)));
        for(var point:candidates.stream().limit(3).toList()) {
            if(spent())return null;
            var route=path(bot,BlockPos.containing(point),0);
            if(route!=null&&route.canReach()){remember(point);return point;}
            failed(bot.position(),point,now);
        }
        return null;
    }
    /** Teammates spread out: no patrol goal within 6 blocks of an ally, so the bot takes its own lane. */
    private boolean crowded(Vec3 spot) {return allies.stream().anyMatch(a->a.distanceToSqr(spot)<36);}
    private void remember(Vec3 point) {recent.addLast(point);while(recent.size()>8)recent.removeFirst();}
    /**
     * Mapped ROUTE / SNIPER / OBJECTIVE points (when the map has any), skipping the last eight selected, ones next to
     * allies and ones underfoot. One in five picks still samples freely so the bot does not become predictable.
     */
    private Vec3 pickWaypoint(BotEntity bot,ArenaMap arena,String team,int now) {
        var random=bot.getRandom();
        var points=new java.util.ArrayList<BotWaypoints.Point>();
        for(var type:new BotWaypoints.Type[]{BotWaypoints.Type.ROUTE,BotWaypoints.Type.SNIPER,BotWaypoints.Type.OBJECTIVE})
            points.addAll(BotWaypoints.of(arena.id(),type,team));
        if(points.isEmpty()||random.nextInt(5)==0)return null;
        java.util.Collections.shuffle(points,new java.util.Random(random.nextLong()));
        points.sort(java.util.Comparator.comparingDouble(p->profile.waypointCost(p.type().name())
                +Math.abs(p.y()-bot.getY())*.5+Math.sqrt(p.position().distanceToSqr(bot.position()))*.05+routeRisk(p.position(),now)*8));
        int tries=0;
        for(var mapped:points) {
            var point=mapped.position();
            if(nearSpawn(arena,team,point))continue;
            if(flat(bot.position(),point)<36||crowded(point)||recent.stream().anyMatch(r->r.distanceToSqr(point)<4))continue;
            if(rejected(bot.position(),point,now))continue;
            if(++tries>ATTEMPTS)break;
            if(spent())break;
            var path=path(bot,BlockPos.containing(point),1);
            if(path!=null&&path.canReach()) {remember(point);return point;}
            failed(bot.position(),point,now);
        }
        return null;
    }
    static BlockPos ground(BotEntity bot,ArenaMap arena,Vec3 point) {
        var level=bot.level();var origin=BlockPos.containing(point);
        // Prefer the requested floor. Roofs above a valid same-level floor must not steal its candidate.
        for(int dy:new int[]{0,-1,1,-2,2,-3,3,-4,4,-5,5,-6,6,-7,-8,-9}) {
            var pos=origin.offset(0,dy,0);
            if(!level.isLoaded(pos))continue;
            if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))continue;
            Vec3 feet=Vec3.atBottomCenterOf(pos);
            if(!level.noCollision(bot,bot.getDimensions(Pose.STANDING).makeBoundingBox(feet)))continue;
            if(!danger(arena,bot,feet))return pos;
        }
        return null;
    }
    static boolean danger(ArenaMap arena,BotEntity bot,Vec3 feet) {
        var box=bot.getDimensions(Pose.STANDING).makeBoundingBox(feet);
        return arena.deathZones()!=null&&arena.deathZones().stream().anyMatch(z->z.intersects(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
    }
    private static double flat(Vec3 a,Vec3 b) {double x=a.x-b.x,z=a.z-b.z;return x*x+z*z;}
}
