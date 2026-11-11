package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

/**
 * 숨바꼭질 for one bot.
 *
 * <p><b>Seeker.</b> The bot is never told which blocks are players. While it walks the map it glances at what is in
 * front of it: real blocks along random lines of sight and, only when it happens to notice one, a disguise. Every
 * candidate gets the same "does this look out of place?" score from what anyone could see (off the block grid,
 * floating, alone among unlike blocks, a decoration in the open, seen moving, near a noise or something that hurt it),
 * plus a guess. A candidate over its (slowly falling) threshold is checked with one shot after a short hesitation.
 * Only a hit — what a player learns from the hit marker — turns the block into a known hider it chases; a miss marks
 * the spot as checked for a while. So it does shoot innocent lanterns and walk past well-placed hiders.
 *
 * <p><b>Hider.</b> During the hiding minute it scores reachable spots for its block (same or similar blocks around,
 * walls and corners, a roof, darkness, few open sight lines, away from the seekers' room and teammates), walks there,
 * settles exactly on the block grid and turns its front to the open side. Quiet hiders periodically relocate across
 * the mapped arena. On seeing a seeker or being shot, it runs: away from the seekers it
 * has seen, toward ground they cannot see, leaping with charged jumps, then hides again somewhere new. It uses its
 * block: TNT and gas barrels blow up seekers in range, dispensers shoot those in line, end crystals and spawners hunt
 * them, health packs sink into the floor, and it taunts — horn, music, bells, shrieks, rude signs — when a seeker
 * nearby is looking the other way or has just shot the wrong block.
 */
final class BotPropHunt {
    private final Random random=new Random();
    private String mode="준비";
    String describe(){return mode;}

    // ---- seeker state ----
    private record Suspect(Vec3 point,PropEntity prop,BlockPos cell,double score){}
    private record Hotspot(Vec3 at,double radius,int until){}
    private final Map<Long,Integer> cleared=new HashMap<>();
    private final Map<Integer,Vec3> seenAt=new HashMap<>();
    private final Map<Integer,Integer> seenTick=new HashMap<>();
    private final List<Hotspot> hotspots=new ArrayList<>();
    private Suspect aiming;
    private int aimSince,hesitation,shotAt;
    private boolean shotPending;
    private PropEntity quarry;
    private Vec3 quarryLast;
    private int quarrySeen;
    private Vec3 investigate;
    private int investigateUntil,nextScan,lookUntil,lookStart,nextLookAround;
    private float lookYaw,aimYaw,aimPitch,errorYaw,errorPitch;
    /** Per-bot temperament: how readily a seeker shoots doubtful blocks, how boldly a hider taunts. */
    private double nerve=.5,boldness=.5;

    // ---- hider state ----
    private enum Hide{SEARCH,TRAVEL,HIDDEN,FLEE,RAM}
    private Hide state=Hide.SEARCH;
    private BlockPos spot;
    private Vec3 searchCentre;
    private double searchRadius=32;
    private boolean avoidSeen;
    private int travelSince,nextSearch,fleeUntil,calmSince,nextTaunt,nextAbility,nextPerceive,struckAt=-1000,missAt=-1000,toggledAt=-1;
    private String lastItem="";
    private boolean reconsider;
    private double nervous;
    private Vec3 fleeTo;
    /** Where it holds still once hidden (on a wall: up on the wall). */
    private Vec3 holdPos;
    /** Q wall cling: the wall of the chosen spot, the height to climb to, and whether it now hangs there. */
    private Direction clingWall;
    private double clingHeight;
    private boolean clinging;
    private LivingEntity ramTarget;
    private int ramUntil,nextFidget,revertAt=-1;
    /** 184: firing the beam (kept until the gauge runs out, restarted only once it is well refilled). */
    private boolean beaming;
    /**
     * Roaming: while the hunt runs and no seeker has been seen for a while, a hider now and then slips off to another
     * spot instead of sitting where it first hid. Bolder bots roam more often; spots it already used are avoided.
     */
    private int lastThreatAt=-10000,nextRoam;
    private boolean roaming;
    private Vec3 sweepTo;
    private int sweepSince;
    private final ArrayDeque<BlockPos> recentSpots=new ArrayDeque<>();
    final BotPropReactions reactions=new BotPropReactions();
    private final Map<BlockPos,Integer> exposed=new HashMap<>();
    private void expose(int now){if(spot!=null)exposed.put(spot,now+600);}
    private boolean exposed(BlockPos feet,int now){exposed.values().removeIf(until->now>=until);return exposed.keySet().stream().anyMatch(p->p.distSqr(feet)<64);}
    private int exits(BotEntity bot,BlockPos feet){
        int count=0;
        for(var d:Direction.Plane.HORIZONTAL){
            boolean open=true;
            for(int n=1;n<=3;n++){
                var next=BotMovement.ground(bot,BotManager.arena(),Vec3.atBottomCenterOf(feet.relative(d,n)));
                if(next==null||Math.abs(next.getY()-feet.getY())>1){open=false;break;}
            }
            if(open)count++;
        }
        return count;
    }
    private record Threat(LivingEntity seeker,double distance,boolean looking){}
    private List<Threat> threats=List.of();
    private final Map<UUID,Vec3> remembered=new HashMap<>();
    private final Map<UUID,Integer> rememberedAt=new HashMap<>();

    void reset(BotAgent a,PropHuntMatch hunt){
        reactions.reset();
        var server=BotManager.server();int now=server==null?0:server.getTickCount();
        aiming=null;shotPending=false;quarry=null;investigate=null;seenAt.clear();seenTick.clear();
        nextScan=now+random.nextInt(8);nextLookAround=now+60+random.nextInt(80);lookUntil=0;
        state=Hide.SEARCH;spot=null;searchCentre=null;searchRadius=32;avoidSeen=false;nextSearch=now+random.nextInt(10);
        threats=List.of();nervous=0;fleeTo=null;reconsider=false;lastItem="";toggledAt=-1;
        holdPos=null;clingWall=null;clinging=false;ramTarget=null;revertAt=-1;nextFidget=now+600+random.nextInt(600);
        lastThreatAt=-10000;roaming=false;sweepTo=null;recentSpots.clear();exposed.clear();nextRoam=now+roamDelay();
        nextTaunt=now+400+random.nextInt(400);nextAbility=now;nextPerceive=now;
        nerve=.35+random.nextDouble()*.3;boldness=.3+random.nextDouble()*.6;
        if(a.bot!=null){aimYaw=a.bot.getYRot();aimPitch=a.bot.getXRot();}
        if(!a.team.equals("ALPHA"))a.movement.prepareSpawnExit();
        mode=a.team.equals("ALPHA")?"숨을 곳 찾기":"대기실";
    }
    void tick(BotAgent a,PropHuntMatch hunt,int now)throws Exception{
        if(!hunt.hunting()&&!hunt.hiding()){a.bot.getNavigation().stop();mode="종료";return;}
        if(a.team.equals("ALPHA"))hide(a,hunt,now);else seek(a,hunt,now);
    }

    // =====================================================================================================
    // Seeker
    // =====================================================================================================
    private void seek(BotAgent a,PropHuntMatch hunt,int now)throws Exception{
        var bot=a.bot;
        if(hunt.waiting(a.id)||!hunt.hunting()){mode=hunt.hiding()?"대기실 (숨는 시간)":"부활 대기";a.aim.idle(bot);return;}
        hotspots.removeIf(h->now>h.until());
        if(now%100==0)cleared.values().removeIf(until->now>until);
        var disguises=hunt.disguises();
        // A known hider: keep it in sight and keep shooting; when it gets away, search where it was last seen.
        if(quarry!=null&&(quarry.isRemoved()||quarry.marker()||!disguises.contains(quarry)))quarry=null;
        if(quarry!=null){
            var centre=quarry.getBoundingBox().getCenter();
            boolean visible=!quarry.isInvisible()&&sees(bot,centre);
            if(visible){quarryLast=centre;quarrySeen=now;}
            if(now-quarrySeen>100){
                hotspots.add(new Hotspot(quarryLast,6,now+600));investigate=quarryLast;investigateUntil=now+300;quarry=null;
            }else{
                mode=visible?"발견한 블록 추격·사격":"놓친 블록 추적";
                double distance=bot.position().distanceTo(quarryLast);
                if(!visible||distance>4.5)go(a,quarryLast,now);else bot.getNavigation().stop();
                if(visible&&aim(a,quarryLast,now,true)&&now>=a.nextShot)fire(a,now);
                else if(!visible)bot.getLookControl().setLookAt(quarryLast.x,quarryLast.y,quarryLast.z,30,30);
                return;
            }
        }
        // One shot at a doubtful block: a hit makes it the quarry (see confirmed), otherwise it counts as checked.
        if(shotPending&&now-shotAt>=8){
            shotPending=false;
            if(aiming!=null&&(aiming.prop()==null||bot.getEyePosition().distanceTo(aiming.prop().getBoundingBox().getCenter())<=8)){
                cleared.put(aiming.cell().asLong(),now+(aiming.prop()==null?1200:500)+random.nextInt(400));aiming=null;
            }
        }
        if(aiming==null&&now>=nextScan){nextScan=now+8;scan(a,hunt,disguises,now);}
        if(aiming!=null){
            var prop=aiming.prop();
            if(prop!=null&&(prop.isRemoved()||prop.marker()))aiming=null;
            else{
                var point=prop!=null?prop.getBoundingBox().getCenter():aiming.point();
                double distance=bot.getEyePosition().distanceTo(point);
                if(now-aimSince>240&&!shotPending){cleared.put(aiming.cell().asLong(),now+400);aiming=null;return;}
                boolean visible=sees(bot,point);
                if(distance>4.5||!visible)go(a,point,now);else bot.getNavigation().stop();
                if(!visible){mode="수상한 블록 추적";bot.getLookControl().setLookAt(point.x,point.y,point.z,30,30);return;}
                mode=distance>4.5?"수상한 블록 접근·사격":String.format(Locale.ROOT,"긴가민가 %.0f%% · 조준",Math.min(99,aiming.score()*100));
                if(aim(a,point,now,false)&&!shotPending&&now-aimSince>=hesitation&&now>=a.nextShot){fire(a,now);shotPending=true;shotAt=now;}
                return;
            }
        }
        // A noise (horn, music, explosion) or a lost quarry: go there and look around.
        if(investigate!=null&&now<investigateUntil){
            mode="소리 난 곳 확인";
            if(bot.position().distanceTo(investigate)<3){investigate=null;lookUntil=now+50;lookStart=now;lookYaw=bot.getYHeadRot();}
            else{go(a,investigate,now);return;}
        }
        if(now<lookUntil){
            mode="둘러보기";bot.getNavigation().stop();
            float yaw=lookYaw+(float)Math.sin((now-lookStart)/50.0*Math.PI*2)*100;
            var look=bot.getEyePosition().add(Vec3.directionFromRotation(10+(float)Math.sin((now-lookStart)/17.0)*15,yaw).scale(6));
            bot.getLookControl().setLookAt(look.x,look.y,look.z,20,20);return;
        }
        if(now>=nextLookAround){lookUntil=now+40+random.nextInt(30);lookStart=now;lookYaw=bot.getYHeadRot();nextLookAround=now+140+random.nextInt(160);return;}
        mode="수색";
        var graph=BotNavGraph.of(BotManager.arena(),bot.level());
        if(graph!=null){
            if(sweepTo!=null&&(bot.position().distanceToSqr(sweepTo)<9||now-sweepSince>600))sweepTo=null;
            if(sweepTo==null){
                for(int i=0;i<8;i++){
                    var candidate=graph.roamSpot(bot.position(),random);if(candidate==null)continue;
                    if(candidate.distToCenterSqr(bot.position())<144||recentSpots.stream().anyMatch(p->p.distSqr(candidate)<144))continue;
                    // Spread seekers across rooms instead of repeatedly searching behind another seeker.
                    var point=Vec3.atBottomCenterOf(candidate);
                    if(BotManager.entities().stream().filter(other->other!=bot&&BotManager.side(other).equals(a.team))
                            .anyMatch(other->{var agent=BotManager.agent(other);return agent!=null&&agent.propHunt.sweepTo!=null&&agent.propHunt.sweepTo.distanceToSqr(point)<144;}))continue;
                    sweepTo=Vec3.atBottomCenterOf(candidate);sweepSince=now;recentSpots.addLast(candidate);
                    while(recentSpots.size()>8)recentSpots.removeFirst();break;
                }
            }
            if(sweepTo!=null){
                if(now>=a.nextPath||bot.getNavigation().isDone()){
                    a.nextPath=now+10;
                    if(a.movement.travel(bot,BotManager.arena(),a.team,sweepTo,now,false))sweepTo=null;
                }
                return;
            }
        }
        if(now>=a.nextPath||bot.getNavigation().isDone()){a.nextPath=now+BotManager.settings().pathTicks();a.movement.travel(bot,BotManager.arena(),a.team,null,now,true);}
    }
    /** What this seeker notices in front of it this glance, scored alike for real blocks and disguises. */
    private void scan(BotAgent a,PropHuntMatch hunt,List<PropEntity> disguises,int now){
        var bot=a.bot;var level=bot.level();var eye=bot.getEyePosition();float head=bot.getYHeadRot();
        double fov=BotManager.settings().fov();
        Suspect best=null;
        for(var prop:disguises){
            if(prop.level()!=level||prop.isInvisible())continue;
            var centre=prop.getBoundingBox().getCenter();double distance=eye.distanceTo(centre);
            if(distance>22||!inView(head,eye,centre,fov)||!sees(bot,centre))continue;
            int id=prop.getId();var before=seenAt.get(id);Integer when=seenTick.get(id);
            seenAt.put(id,prop.position());seenTick.put(id,now);
            // Seen moving between two glances: nothing in the map moves like that.
            boolean moved=before!=null&&when!=null&&now-when<=24&&before.distanceToSqr(prop.position())>.04;
            // Small, far or peripheral blocks are easy to overlook; staring a while catches them.
            if(!moved&&random.nextDouble()>(distance<6?.6:.3))continue;
            var cell=BlockPos.containing(centre);
            if(!moved&&cleared.getOrDefault(cell.asLong(),0)>now)continue;
            double score=awkward(level,prop.blockPosition(),prop.state(),prop.position())+(moved?1.5:0)+hot(centre)+guess()-distance*.01;
            if(best==null||score>best.score())best=new Suspect(centre,prop,cell,score);
        }
        for(int i=0;i<6;i++){
            float yaw=head+(float)((random.nextDouble()-.5)*fov*.8),pitch=(float)(-10+random.nextDouble()*55);
            var end=eye.add(Vec3.directionFromRotation(pitch,yaw).scale(18));
            var hit=level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,bot));
            if(hit.getType()!=HitResult.Type.BLOCK)continue;
            var pos=hit.getBlockPos();var state=level.getBlockState(pos);
            if(state.isAir()||cleared.getOrDefault(pos.asLong(),0)>now)continue;
            var point=hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.15));
            double score=awkward(level,pos,state,Vec3.atBottomCenterOf(pos))+hot(point)+guess()-eye.distanceTo(point)*.01;
            if(best==null||score>best.score())best=new Suspect(point,null,pos,score);
        }
        if(best==null)return;
        // Ever more trigger-happy as time runs out, and right where something gave a hider away.
        double elapsed=1-Math.min(1,hunt.huntRemaining()/(double)hunt.game.huntTicks());
        double threshold=.62-elapsed*.18-(hunt.finalMinute()?.1:0)+(nerve-.5)*.2;
        if(best.score()<threshold)return;
        aiming=best;aimSince=now;
        // The closer the call, the longer it hesitates.
        double margin=Math.min(1,(best.score()-threshold)/.5);
        hesitation=BotManager.settings().reactionTicks()+(int)((1-margin)*30)+random.nextInt(6);
        newError();
    }
    /** How out of place a block (or a disguise standing at {@code position}) looks; about 0 for walls, about 1 for a misfit. */
    private static double awkward(Level level,BlockPos pos,BlockState state,Vec3 position){
        double score=.2;
        // Off the block grid, or hovering at a fractional height.
        double offX=Math.abs(position.x-Math.floor(position.x)-.5),offZ=Math.abs(position.z-Math.floor(position.z)-.5);
        double off=Math.max(offX,offZ);if(off>.08)score+=Math.min(.35,off*.9);
        double up=position.y-Math.floor(position.y);if(up>.05&&up<.45)score+=.12;
        // Nothing underneath.
        var below=BlockPos.containing(position.x,position.y-.05,position.z);
        if(level.getBlockState(below).getCollisionShape(level,below).isEmpty()&&level.getFluidState(below).isEmpty())score+=.3;
        // Alone among unlike blocks, or one of many alike.
        int same=0;
        for(var q:BlockPos.betweenClosed(pos.offset(-3,-2,-3),pos.offset(3,2,3)))if(!q.equals(pos)&&level.getBlockState(q).getBlock()==state.getBlock())same++;
        score+=same==0?.2:same>=6?-.3:-.04*same;
        boolean full=state.isCollisionShapeFullBlock(level,pos);
        if(!full)score+=.1;
        int walls=0;for(var d:Direction.Plane.HORIZONTAL)if(!level.getBlockState(pos.relative(d)).getCollisionShape(level,pos.relative(d)).isEmpty())walls++;
        if(walls==0)score+=.1;
        return score;
    }
    private double hot(Vec3 point){
        double bonus=0;for(var h:hotspots){double d=h.at().distanceTo(point);if(d<h.radius())bonus=Math.max(bonus,.45*(1-d/h.radius()));}
        return bonus;
    }
    private double guess(){return (random.nextDouble()-.5)*.3;}
    private void newError(){
        double error=BotManager.settings().aimError()*.4;
        errorYaw=(float)(random.nextGaussian()*error);errorPitch=(float)(random.nextGaussian()*error*.6);
    }
    /** Turns the view toward a point at the bot's turn rate; true once on it. */
    private boolean aim(BotAgent a,Vec3 point,int now,boolean tracking){
        var bot=a.bot;var eye=bot.getEyePosition();var d=point.subtract(eye);
        if(tracking&&now%10==0)newError();
        float wantYaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90+errorYaw;
        float wantPitch=(float)(-(Mth.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))*180/Math.PI))+errorPitch;
        float rate=(float)(double)BotManager.settings().turnRate();
        float dy=Mth.wrapDegrees(wantYaw-aimYaw),dp=wantPitch-aimPitch;
        aimYaw+=Mth.clamp(dy,-rate,rate);aimPitch=Mth.clamp(aimPitch+Mth.clamp(dp,-rate,rate),-90,90);
        bot.setYRot(aimYaw);bot.setYHeadRot(aimYaw);bot.yBodyRot=aimYaw;bot.setXRot(aimPitch);
        bot.getLookControl().setLookAt(point.x,point.y,point.z,rate,rate);
        return Math.abs(Mth.wrapDegrees(wantYaw-aimYaw))<1.5&&Math.abs(wantPitch-aimPitch)<1.5;
    }
    private void fire(BotAgent a,int now)throws ReflectiveOperationException{
        if(a.gun==null)return;
        var bot=a.bot;bot.setYRot(aimYaw);bot.setYHeadRot(aimYaw);bot.setXRot(aimPitch);
        String result;
        try{result=a.gun.fire(aimPitch,aimYaw);}catch(IllegalStateException notReady){a.nextShot=now+10;return;}
        BotManager.heard(bot);
        a.nextShot=now+(result.equals("SUCCESS")?14:4);
    }
    private void go(BotAgent a,Vec3 point,int now){
        if(now<a.nextPath&&!a.bot.getNavigation().isDone())return;
        a.nextPath=now+10;
        var bot=a.bot;var arena=BotManager.arena();
        // Aim at the block's centre, but walk to reachable feet beside/below it, including airborne disguises.
        var feet=BotMovement.ground(bot,arena,point);
        if(feet==null)feet=BotMovement.ground(bot,arena,new Vec3(point.x,bot.getY(),point.z));
        var destination=feet==null?point:Vec3.atBottomCenterOf(feet);
        if(!a.movement.travel(bot,arena,a.team,destination,now,false))return;
        for(int i=0;i<8&&!BotMovement.spent();i++){
            double angle=i*Math.PI/4;
            var side=BotMovement.ground(bot,arena,new Vec3(point.x+Math.cos(angle)*3,bot.getY(),point.z+Math.sin(angle)*3));
            if(side!=null&&!a.movement.travel(bot,arena,a.team,Vec3.atBottomCenterOf(side),now,false))break;
        }
    }
    /** A pellet of this seeker landed on a disguise: from now on it is a hider. */
    void confirmed(PropEntity prop,boolean found){
        int now=BotManager.server().getTickCount();
        aiming=null;shotPending=false;
        if(found){quarry=null;mode="발견!";lookUntil=now+30;lookStart=now;return;}
        quarry=prop;quarryLast=prop.getBoundingBox().getCenter();quarrySeen=now;
    }
    void respawned(BotAgent a){aiming=null;shotPending=false;quarry=null;investigate=null;a.movement.reset();a.movement.prepareSpawnExit();}
    /** A loud spot: seekers go and look, roughly where they heard it; hiders ignore it. */
    void noise(BotAgent a,Vec3 at){
        if(a.team.equals("ALPHA")||a.bot==null)return;
        double distance=a.bot.position().distanceTo(at);if(distance>40)return;
        int now=BotManager.server().getTickCount();double spread=distance*.12;
        var guess=at.add((random.nextDouble()-.5)*2*spread,0,(random.nextDouble()-.5)*2*spread);
        hotspots.add(new Hotspot(at,3+distance*.1,now+600));
        if(quarry==null&&aiming==null){investigate=guess;investigateUntil=now+400;}
    }
    /** Hurt with nobody to blame (cactus, magma, a blast, a dispenser arrow): something out of place is near. */
    void hurt(BotAgent a,DamageSource source){
        var bot=a.bot;int now=BotManager.server().getTickCount();
        if(a.team.equals("ALPHA")){if(source.getEntity()!=null&&source.getEntity()!=bot)nervous+=1;return;}
        var direct=source.getDirectEntity();
        if(direct!=null&&PropHuntMatch.propArrow(source)){
            var back=direct.getDeltaMovement().normalize().scale(-1);
            for(int k=2;k<=12;k+=4)hotspots.add(new Hotspot(direct.position().add(back.scale(k)),3,now+500));
            return;
        }
        if(source.getEntity()==null)hotspots.add(new Hotspot(bot.position(),2.5,now+500));
    }

    // =====================================================================================================
    // Hider
    // =====================================================================================================
    private void hide(BotAgent a,PropHuntMatch hunt,int now)throws Exception{
        // 255 drives itself (PropAbilities.auto): the bot keeps still and lets it.
        var own=hunt.prop(a.bot.getUUID());if(own!=null&&own.item().equals(PartyBlocks.AUTO)){a.bot.getNavigation().stop();a.movement.halt(a.bot);mode="자율 전투";return;}
        var bot=a.bot;var prop=bot.disguise();if(prop==null)return;
        if(!prop.item().equals(lastItem)){boolean first=lastItem.isEmpty();lastItem=prop.item();toggledAt=-1;if(!first)reconsider=true;}
        if(now>=nextPerceive){nextPerceive=now+5;perceive(a,hunt,prop,now);}
        // Seeing a seeker is enough to run; never freeze in its line of fire waiting to be hit.
        if(hunt.hunting()&&!threats.isEmpty()&&state!=Hide.FLEE&&state!=Hide.RAM)flee(a,now);
        reactions.tick(bot,hunt,now,!threats.isEmpty()||state==Hide.FLEE||state==Hide.RAM);
        // Explosive blocks answer any seeker in range, whatever else is going on.
        if(hunt.hunting()&&PropHuntMatch.explosive(prop.item())&&hunt.botCooldown(a.id)==0){
            double radius=prop.item().equals("minecraft:tnt")?PropHuntMatch.TNT_RADIUS:GimmickConfig.gas.radius();
            long caught=threats.stream().filter(t->t.seeker().distanceTo(prop)<=radius-.3).count();
            if(caught>0&&hunt.botUse(bot)){mode="폭발!";if(threats.size()>caught)flee(a,now);return;}
        }
        // A beacon's beam gives its block away from afar, so it shines only for a hider already found out:
        // then every hider runs 20% faster.
        if(prop.item().equals("minecraft:beacon")&&prop.open()!=(state==Hide.FLEE||state==Hide.RAM)&&hunt.hunting())hunt.botUse(bot);
        // A door, lid or flame toggled as a taunt goes back as it was.
        if(revertAt>=0&&now>=revertAt){revertAt=-1;if(threats.stream().noneMatch(Threat::looking))hunt.botUse(bot);}
        if(state!=Hide.HIDDEN&&clinging)release(bot);
        dolls(a,hunt,prop);
        switch(state){
            case SEARCH->search(a,hunt,prop,now);
            case TRAVEL->travel(a,hunt,prop,now);
            case HIDDEN->hidden(a,hunt,prop,now);
            case FLEE->fleeing(a,hunt,prop,now);
            case RAM->ram(a,hunt,prop,now);
        }
    }
    /** Seekers this hider can see from its block, and which of them look its way. */
    private void perceive(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        var bot=a.bot;var centre=prop.getBoundingBox().getCenter();var list=new ArrayList<Threat>();
        for(var seeker:hunt.seekers()){
            if(seeker.level()!=bot.level())continue;
            double distance=seeker.distanceTo(prop);if(distance>32)continue;
            var eye=seeker.getEyePosition();
            if(!clear(bot.level(),centre,eye,bot))continue;
            var toMe=centre.subtract(eye).normalize();
            boolean looking=seeker.getLookAngle().dot(toMe)>Math.cos(Math.toRadians(distance<6?35:20));
            list.add(new Threat(seeker,distance,looking));
            remembered.put(seeker.getUUID(),seeker.position());rememberedAt.put(seeker.getUUID(),now);
        }
        threats=list;if(!list.isEmpty())lastThreatAt=now;
        rememberedAt.entrySet().removeIf(e->{if(now-e.getValue()>400){remembered.remove(e.getKey());return true;}return false;});
    }
    private void search(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        var bot=a.bot;mode=roaming?"배회 · 다음 은신처 찾기":"숨을 곳 찾는 중";
        if(now<nextSearch)return;nextSearch=now+10;
        var arena=BotManager.arena();
        if(searchCentre==null){var s=arena.alphaSpawn();searchCentre=hunt.hiding()?new Vec3(s.x(),s.y(),s.z()):bot.position();}
        var level=bot.level();
        record Option(BlockPos feet,double score){}
        var options=new ArrayList<Option>();
        var graph=BotNavGraph.of(arena,level);
        // Every candidate gets the cheap part of the score (blocks around, walls, light, distances); only the best few
        // also get the raycasts and ground probes (sight lines, exits, seekers' view), which cost far more per spot.
        var others=hunt.disguises();
        if(graph!=null&&!avoidSeen)for(int i=0;i<16;i++){
            var feet=graph.roamSpot(bot.position(),random);if(feet==null)continue;
            double distance=bot.position().distanceTo(Vec3.atBottomCenterOf(feet));
            if(distance<16||recentSpots.stream().anyMatch(p->p.distSqr(feet)<144))continue;
            if(exposed(feet,now))continue;
            double quality=level.hasChunkAt(feet)?quick(a,prop,feet,others):0;
            options.add(new Option(feet,quality+Math.min(4,distance/16)));
        }
        for(int i=0;i<40;i++){
            double angle=random.nextDouble()*Math.PI*2,r=3+random.nextDouble()*searchRadius;
            var sample=searchCentre.add(Math.cos(angle)*r,random.nextInt(9)-4,Math.sin(angle)*r);
            var feet=BotMovement.ground(bot,arena,sample);if(feet==null)continue;
            if(!exposed(feet,now))options.add(new Option(feet,quick(a,prop,feet,others)));
        }
        if(options.size()>THOROUGH){options.sort(Comparator.comparingDouble(Option::score).reversed());options.subList(THOROUGH,options.size()).clear();}
        options.replaceAll(o->level.hasChunkAt(o.feet())?new Option(o.feet(),o.score()+thorough(a,o.feet())):o);
        // Blocks that belong on walls (paintings, signs, torches...) also try hanging there (Q), well above the floor.
        Direction wallChoice=null;double heightChoice=0;
        if(wallBlock(prop)&&!options.isEmpty()){
            var top=options.stream().max(Comparator.comparingDouble(Option::score)).get();
            for(var option:new ArrayList<>(options)){
                if(!level.hasChunkAt(option.feet()))continue;
                var wall=hangWall(level,option.feet(),bot);if(wall==null)continue;
                double h=1+random.nextInt(3)*.5,bonus=prop.item().equals("minecraft:painting")?1.8:.9;
                if(option.score()+bonus>top.score()){top=new Option(option.feet(),option.score()+bonus);wallChoice=wall;heightChoice=h;}
            }
            if(wallChoice!=null){options.remove(top);options.add(0,top);}
        }
        if(wallChoice==null)options.sort(Comparator.comparingDouble(Option::score).reversed());
        else{var first=options.remove(0);options.sort(Comparator.comparingDouble(Option::score).reversed());options.add(0,first);}
        int tries=0;
        for(var option:options){
            if(tries++>=3||BotMovement.spent())break;
            var destination=Vec3.atBottomCenterOf(option.feet());
            if(graph!=null&&bot.position().distanceToSqr(destination)>BotRoute.FAR*BotRoute.FAR&&graph.reachable(bot.position(),destination)){
                if(a.movement.travel(bot,arena,a.team,destination,now,false))continue;
                spot=option.feet();state=Hide.TRAVEL;travelSince=now;clingWall=null;clingHeight=0;return;
            }
            var path=BotMovement.path(bot,option.feet(),0);
            if(path!=null&&path.canReach()&&bot.getNavigation().moveTo(path,avoidSeen?.85:1)){
                spot=option.feet();state=Hide.TRAVEL;travelSince=now;
                boolean hang=tries==1&&wallChoice!=null&&hunt.clingEnabled();clingWall=hang?wallChoice:null;clingHeight=hang?heightChoice:0;
                return;
            }
        }
        // Nothing reachable: widen the search, and in the end settle right here.
        searchRadius=Math.max(8,searchRadius*.8);
        if(now-calmSince>200&&options.isEmpty()){spot=bot.blockPosition();settle(a,hunt,prop,now);}
    }
    /** Candidate spots per search that get the full (raycast) score. */
    private static final int THOROUGH=12;
    /** How good a hiding spot this cell is for this block. */
    private double score(BotAgent a,PropHuntMatch hunt,PropEntity prop,BlockPos feet){return quick(a,prop,feet,hunt.disguises())+thorough(a,feet);}
    /** The score's block lookups: what is around, walls, roof, light, spawns, other hiders, recent spots (plus noise). */
    private double quick(BotAgent a,PropEntity prop,BlockPos feet,List<PropEntity> others){
        var bot=a.bot;var level=bot.level();var arena=BotManager.arena();var mine=prop.state();var point=Vec3.atBottomCenterOf(feet);
        double s=0;int same=0,similar=0;
        for(var q:BlockPos.betweenClosed(feet.offset(-3,-1,-3),feet.offset(3,2,3))){
            var state=level.getBlockState(q);if(state.isAir())continue;
            if(state.getBlock()==mine.getBlock())same++;else if(state.getSoundType()==mine.getSoundType())similar++;
        }
        s+=Math.min(3,same)+Math.min(6,similar)*.2;
        int walls=0;Direction wall=null;
        for(var d:Direction.Plane.HORIZONTAL){var side=feet.relative(d);if(!level.getBlockState(side).getCollisionShape(level,side).isEmpty()){walls++;wall=d;}}
        boolean small=!mine.isCollisionShapeFullBlock(level,feet)||prop.item().equals("minecraft:painting");
        s+=walls*(small?.8:.5)+(walls>=2?.3:0);
        if(prop.item().equals("minecraft:painting")&&wall==null)s-=3;
        for(int up=2;up<=5;up++){var roof=feet.above(up);if(!level.getBlockState(roof).getCollisionShape(level,roof).isEmpty()){s+=.4;break;}}
        s+=(15-level.getMaxLocalRawBrightness(feet))/15.0*.8;
        var seekers=arena.bravoSpawn();double far=point.distanceTo(new Vec3(seekers.x(),seekers.y(),seekers.z()));
        s+=Math.min(1.2,far/30);
        if(BotMovement.nearSpawn(arena,"BRAVO",point))s-=4;
        if(BotMovement.nearSpawn(arena,"ALPHA",point))s-=.8;
        for(var other:others)if(other!=prop&&other.position().distanceToSqr(point)<25)s-=1.5;
        if(!level.getFluidState(feet).isEmpty()&&!(mine.getBlock() instanceof NozzleBlock)&&!prop.item().equals("minecraft:water"))s-=1;
        // Roaming goes somewhere new, not back to a spot used a moment ago.
        for(var used:recentSpots)if(used.distSqr(feet)<25)s-=2;
        return s+random.nextDouble()*.3;
    }
    /** The score's sight lines and ground probes: open directions, ways out, and whether a seeker seen lately could see it. */
    private double thorough(BotAgent a,BlockPos feet){
        var bot=a.bot;var level=bot.level();var point=Vec3.atBottomCenterOf(feet);double s=0;
        int open=0;
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;var to=point.add(Math.cos(angle)*7,.5,Math.sin(angle)*7);
            if(!blocked(level,point.add(0,.5,0),to,bot))open++;
        }
        s-=open*.15;
        int ways=exits(bot,feet);s+=ways>=2?1.5:ways==1?-2:-5;
        if(avoidSeen)for(var seen:remembered.values())if(seen.distanceTo(point)<20&&clear(level,seen.add(0,1.5,0),point.add(0,.5,0),bot))s-=2.5;
        return s;
    }
    private void travel(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        var bot=a.bot;mode=hunt.hiding()?"숨을 곳으로 이동":roaming?"배회 중":"새 은신처로 이동";
        if(hunt.hunting()&&!threats.isEmpty()){flee(a,now);return;}
        var target=Vec3.atBottomCenterOf(spot);
        double flat=Math.pow(bot.getX()-target.x,2)+Math.pow(bot.getZ()-target.z,2);
        if(flat<.5&&Math.abs(bot.getY()-target.y)<1.2){settle(a,hunt,prop,now);return;}
        if(bot.getNavigation().isDone()){
            // A spot a ledge up and close by: a charged leap, as a hiding player would.
            // 95 / 190 cannot jump: they climb the wall up to it instead.
            // 252 rides the wall up on her board, faster than 95 / 190 climb.
            boolean skate=PropSkate.skater(prop.item());
            if((PropSticky.sticky(prop.item())||skate)&&flat<9&&target.y-bot.getY()>=.9&&target.y-bot.getY()<=(skate?4:6)){
                var dir=new Vec3(target.x-bot.getX(),0,target.z-bot.getZ()).normalize().scale(skate?.3:PropSticky.SPEED);
                bot.setDeltaMovement(dir.x,bot.horizontalCollision||target.y>bot.getY()?.2:0,dir.z);bot.fallDistance=0;bot.hurtMarked=true;return;
            }
            if(bot.onGround()&&flat<9&&target.y-bot.getY()>=.9&&target.y-bot.getY()<=3.2){
                var dir=new Vec3(target.x-bot.getX(),0,target.z-bot.getZ()).normalize().scale(.25);
                bot.setDeltaMovement(dir.x,PropHuntGame.jumpVelocity(bot,(int)Math.min(20,(target.y-bot.getY())*7)),dir.z);bot.hurtMarked=true;return;
            }
            if(BotMovement.spent())return;
            if(a.movement.travel(bot,BotManager.arena(),a.team,target,now,false)){state=Hide.SEARCH;nextSearch=now;}
        }
        else if(now>=a.nextPath){a.nextPath=now+10;a.movement.travel(bot,BotManager.arena(),a.team,target,now,false);}
        // Out of hiding time (or stuck): take the nearest decent cell rather than be caught walking.
        if(now-travelSince>(hunt.hiding()?900:400)){spot=bot.blockPosition();settle(a,hunt,prop,now);}
    }
    /** On the spot: exactly on the block grid, front to the open side, a health pack sunk into the floor. */
    private void settle(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        var bot=a.bot;var level=bot.level();
        bot.getNavigation().stop();bot.setDeltaMovement(Vec3.ZERO);
        var centre=Vec3.atBottomCenterOf(spot);
        if(level.noCollision(bot,bot.getBoundingBox().move(centre.subtract(bot.position()))))bot.setPos(centre);
        Direction wall=null,open=null;int bestOpen=-1;
        for(var d:Direction.Plane.HORIZONTAL){
            var side=spot.relative(d);
            if(!level.getBlockState(side).getCollisionShape(level,side).isEmpty()){wall=d;continue;}
            int free=0;for(int k=1;k<=6;k++){var c=spot.relative(d,k);if(!level.getBlockState(c).getCollisionShape(level,c).isEmpty())break;free++;}
            if(free>bestOpen){bestOpen=free;open=d;}
        }
        if(open!=null){
            bot.setYRot(open.toYRot());bot.yBodyRot=open.toYRot();bot.setYHeadRot(open.toYRot());
            for(int i=0;i<6;i++){
                var state=prop.state();
                var facing=state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)?state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                        :state.hasProperty(BlockStateProperties.FACING)?state.getValue(BlockStateProperties.FACING):null;
                if(facing==null||facing==open)break;
                hunt.botRotate(bot);
            }
        }
        // A hanging spot: up against the chosen wall.
        if(clingWall!=null){
            for(int i=0;i<4&&prop.item().equals("minecraft:painting")&&(prop.rotation()%2==0)!=(clingWall.getAxis()==Direction.Axis.Z);i++)hunt.botRotate(bot);
            var flush=bot.position().add(Vec3.atLowerCornerOf(clingWall.getNormal()).scale(.5-bot.getBbWidth()/2-.01));
            if(level.noCollision(bot,bot.getBoundingBox().move(flush.subtract(bot.position()))))bot.setPos(flush);
        }
        // A painting hangs flat on its wall.
        else if(prop.item().equals("minecraft:painting")&&wall!=null){
            boolean alongX=wall.getAxis()==Direction.Axis.Z;
            for(int i=0;i<4&&(prop.rotation()%2==0)!=alongX;i++)hunt.botRotate(bot);
            var flush=bot.position().add(Vec3.atLowerCornerOf(wall.getNormal()).scale(.5-bot.getBbWidth()/2-.01));
            if(level.noCollision(bot,bot.getBoundingBox().move(flush.subtract(bot.position()))))bot.setPos(flush);
        }
        if(prop.sinkable()&&!prop.sunk())hunt.botUse(bot);
        // A lectern gets a line in its book for whoever reads it.
        if(prop.item().equals("minecraft:lectern")&&prop.text().isEmpty())hunt.botUse(bot);
        // Hanging spot: face away from the wall and hold on (the climb happens over the next ticks).
        if(clingWall!=null&&PropCling.attached(level,bot.getBoundingBox(),clingWall,PropCling.REACH)){
            clinging=true;bot.setNoGravity(true);bot.setDeltaMovement(Vec3.ZERO);
            float away=clingWall.getOpposite().toYRot();bot.setYRot(away);bot.yBodyRot=away;bot.setYHeadRot(away);
        }else clingWall=null;
        holdPos=bot.position();
        state=Hide.HIDDEN;calmSince=now;nervous=0;reconsider=false;avoidSeen=false;roaming=false;
        recentSpots.addLast(spot);while(recentSpots.size()>5)recentSpots.removeFirst();
        nextRoam=now+roamDelay();
    }
    private void hidden(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        var bot=a.bot;mode="숨어 있음";
        bot.getNavigation().stop();
        if(clinging){
            // Climb the wall like a player holding forward, until high enough or the wall ends; then hang still.
            var start=Vec3.atBottomCenterOf(spot);
            if(bot.getY()-start.y<clingHeight&&threats.stream().noneMatch(Threat::looking)){
                var step=PropCling.motion(bot.level(),bot.getBoundingBox(),clingWall,1,0,bot.getYRot());
                if(step.y>0){bot.setPos(bot.position().add(0,step.y,0));holdPos=bot.position();}
            }
            if(!PropCling.attached(bot.level(),bot.getBoundingBox(),clingWall,PropCling.SERVER_REACH)){release(bot);holdPos=bot.position();}
            else{bot.setNoGravity(true);bot.setDeltaMovement(Vec3.ZERO);bot.fallDistance=0;if(holdPos!=null&&bot.position().distanceToSqr(holdPos)>1e-4)bot.setPos(holdPos);}
        }else if(holdPos!=null&&bot.position().distanceToSqr(holdPos)>.36&&threats.isEmpty()&&bot.onGround())settle(a,hunt,prop,now);
        if(!hunt.hunting())return;
        // Nervousness: seekers looking this way from close by add up; it ebbs while nobody does.
        double pressure=0;
        for(var t:threats)if(t.looking())pressure+=Math.max(0,(10-t.distance())/10)*(t.distance()<4?.12:.05);
        nervous=pressure>0?nervous+pressure:nervous*.985;
        var nearest=threats.stream().min(Comparator.comparingDouble(Threat::distance)).orElse(null);
        if(nearest!=null&&(nearest.distance()<1.8||nearest.looking()&&nearest.distance()<3&&nervous>.6)||nervous>1.4+boldness){flee(a,now);return;}
        abilities(a,hunt,prop,now);
        if(state!=Hide.HIDDEN)return;
        taunt(a,hunt,prop,now);
        fidget(a,hunt,prop,now);
        // Roam: quiet for a while (no seeker in sight for 10 s, none remembered close by), time to slip elsewhere.
        if(state==Hide.HIDDEN&&now>=nextRoam&&now-lastThreatAt>100
                &&remembered.values().stream().noneMatch(seen->seen.distanceTo(prop.position())<16)){
            if(prop.sinkable()&&prop.sunk())hunt.botUse(bot);
            roaming=true;state=Hide.SEARCH;searchCentre=bot.position();searchRadius=24+random.nextDouble()*24;avoidSeen=false;nextSearch=now;
            mode="자리 옮기는 중";return;
        }
        // A new block that fits this place badly moves on, but only while nobody watches.
        if(reconsider&&threats.isEmpty()&&now>=nextSearch){
            reconsider=false;
            double here=score(a,hunt,prop,spot==null?bot.blockPosition():spot);
            if(here<1.2){state=Hide.SEARCH;searchCentre=bot.position();searchRadius=16;avoidSeen=true;nextSearch=now;}
        }
        // A sculk toggled on as a taunt falls quiet again after a few seconds.
        if(toggledAt>=0&&now-toggledAt>70&&prop.open()){if(hunt.botUse(bot))toggledAt=-1;}
    }
    /** Offensive blocks used against seekers that cannot see it coming. */
    private void abilities(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        if(now<nextAbility||hunt.botCooldown(a.id)>0)return;
        var bot=a.bot;String item=prop.item();
        var unaware=threats.stream().filter(t->!t.looking()).min(Comparator.comparingDouble(Threat::distance)).orElse(null);
        switch(item){
            case "minecraft:dispenser","minecraft:dropper"->{
                var facing=prop.state().getValue(BlockStateProperties.FACING);var line=Vec3.atLowerCornerOf(facing.getNormal());
                for(var t:threats){
                    var to=t.seeker().getBoundingBox().getCenter().subtract(prop.getBoundingBox().getCenter());
                    if(t.distance()<16&&to.normalize().dot(line)>Math.cos(Math.toRadians(8))){hunt.botUse(bot);nextAbility=now+20;mode="화살 발사";return;}
                }
                // Turn toward an unaware seeker; turning in front of one would give it away.
                if(unaware!=null&&unaware.distance()<14&&threats.stream().noneMatch(Threat::looking)){
                    var to=unaware.seeker().position().subtract(prop.position());
                    var want=Direction.getNearest(to.x,0,to.z);
                    if(facing!=want&&Math.abs(to.y)<2){hunt.botRotate(bot);nextAbility=now+6;}
                }
            }
            case "minecraft:end_crystal"->{if(unaware!=null&&unaware.distance()<22&&random.nextDouble()<.5+boldness*.5){hunt.botUse(bot);nextAbility=now+40;mode="수정 번개";}}
            // 199: call the lightning storm once seekers come within its 24 blocks.
            case "minecraft:conduit"->{if(threats.stream().anyMatch(t->t.distance()<20)&&random.nextDouble()<.4+boldness*.5){hunt.botUse(bot);nextAbility=now+40;mode="번개 폭풍";}}
            // 39: a burst when a seeker steps into the stream.
            case "gungang:nozzle"->{if(threats.stream().anyMatch(t->t.distance()<2.5)){hunt.botUse(bot);nextAbility=now+40;mode="대량 분사";}}
            case "minecraft:spawner"->{if(!threats.isEmpty()||random.nextDouble()<.02){hunt.botUse(bot);nextAbility=now+40;mode="거미 소환";}}
            case "minecraft:brewing_stand"->{if(threats.stream().allMatch(t->t.distance()>16)){hunt.botUse(bot);nextAbility=now+40;mode="팀 물약";}}
            case "minecraft:bell"->{if(!threats.isEmpty()&&threats.stream().allMatch(t->t.distance()>12)){hunt.botUse(bot);nextAbility=now+40;mode="종 울리기";}}
            // A seeker stepping into the portal is thrown somewhere random on the map.
            case "minecraft:nether_portal"->{
                var box=prop.getBoundingBox().inflate(.15);
                if(threats.stream().anyMatch(t->t.seeker().getBoundingBox().intersects(box))){hunt.botUse(bot);nextAbility=now+20;mode="차원문 날려 보내기";}
            }
            // A teammate with a seeker on top of it gets pulled away to this lodestone.
            case "minecraft:lodestone"->{
                if(threats.stream().anyMatch(Threat::looking))break;
                for(var other:hunt.disguises()){
                    if(other==prop||other.distanceTo(prop)>24)continue;
                    if(threats.stream().anyMatch(t->t.seeker().distanceTo(other)<5)){hunt.botUse(bot);nextAbility=now+100;mode="자석석으로 팀원 구출";return;}
                }
            }
            // 189: a lit TNT left at the feet of a seeker close by, then away before it blows.
            case "minecraft:tnt_minecart"->{if(threats.stream().anyMatch(t->t.distance()<4)){hunt.botUse(bot);nextAbility=now+40;mode="TNT 설치 후 도주";flee(a,now);return;}}
            // A cart rams a seeker that wanders close without looking (50 damage and a shove), then makes off.
            case "minecraft:minecart"->{
                var close=threats.stream().filter(t->t.distance()<4.5&&(!t.looking()||t.distance()<2.5)).min(Comparator.comparingDouble(Threat::distance)).orElse(null);
                if(close!=null&&random.nextDouble()<.4+boldness*.5){ramTarget=close.seeker();ramUntil=now+30;state=Hide.RAM;mode="광산 수레 돌진";return;}
            }
            // 250: money rain once a seeker is within its 8 blocks; 247: a pull of the slot machine now and then.
            case "gungang:embezzlement_statue"->{if(hunt.botCooldown(a.id)==0&&threats.stream().anyMatch(t->t.distance()<7)){hunt.botUse(bot);nextAbility=now+100;mode="돈벼락";return;}}
            case "gungang:slot_machine"->{if(hunt.botCooldown(a.id)==0&&random.nextDouble()<.3){hunt.botUse(bot);nextAbility=now+100;mode="슬롯 돌리기";return;}}
            default->{}
        }
        if(nextAbility<now)nextAbility=now+10;
    }
    /**
     * 조롱: a seeker in sight but looking elsewhere, none right on top of it — a horn blast, a tune, a shriek or a rude
     * sign. More often in the final minute and right after a seeker wasted a shot nearby.
     */
    private void taunt(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        if(now<nextTaunt||now%20!=0)return;
        var bot=a.bot;
        if(threats.isEmpty()||threats.stream().anyMatch(t->t.distance()<5||t.looking()&&t.distance()<12))return;
        if(threats.stream().noneMatch(t->t.distance()<=26))return;
        double chance=boldness*.08+(hunt.finalMinute()?.15:0)+(now-missAt<100?.3:0);
        if(random.nextDouble()>chance)return;
        String item=prop.item();
        boolean done=switch(item){
            case "minecraft:note_block","minecraft:jukebox"->hunt.botCooldown(a.id)==0&&hunt.botUse(bot);
            case "minecraft:sculk_sensor","minecraft:sculk_shrieker"->{if(!prop.open()&&hunt.botUse(bot)){toggledAt=now;yield true;}yield false;}
            case "minecraft:oak_sign","minecraft:oak_hanging_sign","gungang:shutter","minecraft:bubble_column","gungang:billiard_ball","gungang:christmas_tree","gungang:wishing_well"->hunt.botUse(bot);
            case "gungang:dice"->hunt.botCooldown(a.id)==0&&hunt.botUse(bot);
            // Crying, hearts and an emoticon, or night falling all at once.
            case "minecraft:crying_obsidian","gungang:prettiest_block","minecraft:daylight_detector"->hunt.botCooldown(a.id)==0&&hunt.botUse(bot);
            // Doors, trapdoors, lids and flames: creak open (or go out) and back a couple of seconds later.
            default->{
                var blockState=prop.state();
                if(revertAt<0&&!prop.healthPack()&&(blockState.hasProperty(BlockStateProperties.OPEN)||blockState.hasProperty(BlockStateProperties.LIT))&&hunt.botUse(bot)){revertAt=now+40+random.nextInt(40);yield true;}
                yield false;
            }
        };
        if(!done)hunt.botHorn(bot);
        mode="술래 조롱";
        nextTaunt=now+200+random.nextInt(300);
    }
    /** Found out (or about to be): run from the seekers it knows about, toward ground they cannot see. */
    private void flee(BotAgent a,int now){
        if(state!=Hide.FLEE)exposed.put(a.bot.blockPosition(),now+600);
        if(state!=Hide.FLEE){expose(now);state=Hide.FLEE;fleeUntil=now+300;calmSince=now;fleeTo=null;release(a.bot);a.movement.halt(a.bot);a.nextPath=now;}
    }
    private void fleeing(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        var bot=a.bot;mode="발각 · 도주";
        if(!hunt.hunting()){state=Hide.SEARCH;return;}
        // Out of the floor first: a sunk health pack cannot run.
        if(prop.sinkable()&&prop.sunk())hunt.botUse(bot);
        var known=new ArrayList<Vec3>();
        for(var t:threats)known.add(t.seeker().position());
        if(known.isEmpty())known.addAll(remembered.values());
        boolean watched=!threats.isEmpty();
        if(watched){calmSince=now;fleeUntil=now+300;}
        // Lost them: hide again nearby, somewhere they have not seen.
        if(now-calmSince>60||now>fleeUntil){
            state=Hide.SEARCH;searchCentre=bot.position();searchRadius=18;avoidSeen=true;nextSearch=now;nervous=0;return;
        }
        if(fleeTo==null||bot.getNavigation().isDone()||now%20==0&&watched){
            if(!BotMovement.spent()){var next=escape(a,known);if(next!=null)fleeTo=next;}
        }
        // Charged leaps make it a hard target at close range.
        var nearest=threats.stream().mapToDouble(Threat::distance).min().orElse(99);
        if(bot.onGround()&&nearest<9&&random.nextDouble()<.06&&!PropSticky.sticky(prop.item())){
            var motion=bot.getDeltaMovement();
            bot.setDeltaMovement(motion.x*1.4,PropHuntGame.jumpVelocity(bot,8+random.nextInt(12)),motion.z*1.4);bot.hurtMarked=true;
        }
        // Parting shots on the way.
        if(hunt.botCooldown(a.id)==0&&now>=nextAbility&&nearest<16){
            switch(prop.item()){
                case "minecraft:end_crystal","minecraft:spawner"->{hunt.botUse(bot);nextAbility=now+40;}
                // Five seconds of flight at this height: over gaps and above the pellets aimed at the floor.
                // 179: the eye flies toward the escape point and takes the hider with it.
                case "minecraft:end_portal_frame"->{if(nearest<10&&fleeTo!=null){look(bot,prop.getBoundingBox().getCenter().add(0,.4,0),fleeTo.add(0,1,0));hunt.botUse(bot);nextAbility=now+40;mode="엔더의 눈 순간이동";}}
                case "minecraft:tnt_minecart"->{if(nearest<5){hunt.botUse(bot);nextAbility=now+40;}}
                case "minecraft:command_block"->{if(nearest<8){hunt.botUse(bot);nextAbility=now+40;mode="커맨드 블록 비행 도주";}}
                case "minecraft:nether_portal"->{
                    var box=prop.getBoundingBox().inflate(.15);
                    if(threats.stream().anyMatch(t->t.seeker().getBoundingBox().intersects(box))){hunt.botUse(bot);nextAbility=now+20;}
                }
                case "minecraft:minecart"->{
                    var close=threats.stream().filter(t->t.distance()<3).findFirst().orElse(null);
                    if(close!=null){ramTarget=close.seeker();ramUntil=now+20;state=Hide.RAM;nextAbility=now+60;}
                }
                default->{}
            }
        }
    }
    /** Minecart: drive straight into the seeker, then run. */
    private void ram(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        var bot=a.bot;mode="광산 수레 돌진";
        if(ramTarget==null||!ramTarget.isAlive()||now>ramUntil||!hunt.hunting()||bot.getBoundingBox().inflate(.3).intersects(ramTarget.getBoundingBox())&&now>ramUntil-25){
            ramTarget=null;flee(a,now);return;
        }
        var to=ramTarget.position();
        bot.getNavigation().moveTo(to.x,to.y,to.z,1.2);
        bot.getLookControl().setLookAt(ramTarget,30,30);
    }
    /**
     * Nobody near: small changes a player makes out of boredom, and that also make a seeker's memory of the block
     * wrong: another flower in the pot, a dent in the anvil, a mannequin pose, a hologram, a pad or belt setting.
     */
    private void fidget(BotAgent a,PropHuntMatch hunt,PropEntity prop,int now){
        if(now<nextFidget||!threats.isEmpty())return;
        nextFidget=now+500+random.nextInt(900);
        if(hunt.seekers().stream().anyMatch(s->s.distanceTo(prop)<20))return;
        var bot=a.bot;
        switch(prop.item()){
            case "minecraft:flower_pot","minecraft:anvil","gungang:mannequin","minecraft:sea_pickle","minecraft:turtle_egg","minecraft:armor_stand","gungang:eunbi_doll","gungang:hanbi_doll",
                    "gungang:blue_jump_pad","gungang:lime_jump_pad","gungang:hologram_projector","minecraft:bubble_column"->{
                if(hunt.botCooldown(a.id)==0&&hunt.botUse(bot))mode="모양 바꾸기";
                if(prop.item().equals("gungang:hologram_projector")&&random.nextBoolean())hunt.botRotate(bot);
            }
            default->{}
        }
    }
    /**
     * Dolls on the attack key: 184's beam at a seeker in sight while hidden (it gives the doll away, so only with a good
     * gauge), 191's cloak while running (invisible and untouchable).
     */
    private void dolls(BotAgent a,PropHuntMatch hunt,PropEntity prop){
        String item=prop.item();if(!PropAbilities.doll(item))return;
        var bot=a.bot;boolean on=false;float gauge=hunt.abilities.gauge(a.id);
        if(item.equals(PropAbilities.EUNBI)){
            var target=threats.stream().filter(t->t.distance()<40).min(Comparator.comparingDouble(Threat::distance)).orElse(null);
            beaming=beaming?gauge>0:gauge>.6f;
            if(state==Hide.HIDDEN&&target!=null&&hunt.hunting()&&beaming){look(bot,prop.position().add(0,1.55,0),target.seeker().getBoundingBox().getCenter());on=true;mode="광선 발사";}
        }else{
            on=(state==Hide.FLEE||state==Hide.RAM)&&hunt.hunting()&&gauge>0;
            if(on)mode="투명화 도주";
        }
        hunt.abilities.primary(bot,prop,on);
    }
    /** Turns the bot's view (and so its doll or eye of ender) from {@code from} toward {@code to}. */
    private static void look(net.minecraft.world.entity.Mob bot,Vec3 from,Vec3 to){
        var d=to.subtract(from);
        float yaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90,pitch=(float)(-(Mth.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))*180/Math.PI));
        bot.setYRot(yaw);bot.yBodyRot=yaw;bot.setYHeadRot(yaw);bot.setXRot(pitch);
    }
    /** Quiet hiders relocate every 12–30 seconds, rather than camping for most of the round. */
    private int roamDelay(){return 240+random.nextInt(361);}
    /** Lets go of the wall. */
    private void release(net.minecraft.world.entity.Mob bot){clinging=false;clingWall=null;bot.setNoGravity(false);}
    /** Blocks that really hang on walls, so a disguise up on one looks in place. */
    private static boolean wallBlock(PropEntity prop){
        String id=prop.item();
        return id.equals("minecraft:painting")||id.contains("sign")||id.contains("banner")||id.contains("torch")||id.contains("lantern")
                ||id.contains("ladder")||id.contains("vine")||id.contains("button")||id.contains("lever")||id.contains("item_frame")
                ||id.contains("skull")||id.contains("head")||id.contains("shutter");
    }
    /** A wall beside this floor cell the body can hang on (wall behind its feet and well above its head), or null. */
    private static Direction hangWall(Level level,BlockPos feet,net.minecraft.world.entity.Mob bot){
        var size=bot.getDimensions(bot.getPose());var centre=Vec3.atBottomCenterOf(feet);
        for(var d:Direction.Plane.HORIZONTAL){
            var flush=centre.add(Vec3.atLowerCornerOf(d.getNormal()).scale(.5-size.width/2-.01));
            var box=size.makeBoundingBox(flush);
            if(level.noCollision(bot,box)&&PropCling.attached(level,box,d,PropCling.REACH)&&PropCling.attached(level,box.move(0,1,0),d,PropCling.REACH))return d;
        }
        return null;
    }
    /** A reachable point away from the known seekers, preferably out of their sight. */
    private Vec3 escape(BotAgent a,List<Vec3> seekers){
        var bot=a.bot;var level=bot.level();var arena=BotManager.arena();var here=bot.position();
        Vec3 centre=seekers.isEmpty()?here.add(bot.getLookAngle().scale(-4)):seekers.stream().reduce(Vec3.ZERO,Vec3::add).scale(1.0/seekers.size());
        double nearestNow=seekers.stream().mapToDouble(s->s.distanceTo(here)).min().orElse(99);
        record Escape(BlockPos feet,double score){}
        var options=new ArrayList<Escape>();
        for(int i=0;i<16;i++){
            double angle=random.nextDouble()*Math.PI*2,r=4+random.nextDouble()*15;
            var feet=BotMovement.ground(bot,arena,here.add(Math.cos(angle)*r,0,Math.sin(angle)*r));if(feet==null)continue;
            var point=Vec3.atBottomCenterOf(feet);
            double nearest=seekers.stream().mapToDouble(s->s.distanceTo(point)).min().orElse(99);
            if(nearest<Math.min(4,nearestNow))continue;
            int ways=exits(bot,feet);
            double score=Math.min(24,nearest)+point.subtract(here).normalize().dot(here.subtract(centre).normalize())*2+(ways>=2?8:-12);
            for(var s:seekers)if(blocked(level,s.add(0,1.5,0),point.add(0,.5,0),bot))score+=24;
            options.add(new Escape(feet,score));
        }
        options.sort(Comparator.comparingDouble(Escape::score).reversed());
        for(var option:options.stream().limit(3).toList()){
            if(BotMovement.spent())return null;
            var path=BotMovement.path(bot,option.feet(),0);
            if(path==null||!path.canReach())continue;
            boolean safe=true;
            for(int n=0;n<path.getNodeCount();n++){
                var step=Vec3.atBottomCenterOf(path.getNode(n).asBlockPos());
                if(seekers.stream().anyMatch(seeker->seeker.distanceToSqr(step)<9&&clear(level,seeker.add(0,1.5,0),step.add(0,.5,0),bot))){safe=false;break;}
            }
            if(safe&&bot.getNavigation().moveTo(path,1))return Vec3.atBottomCenterOf(option.feet());
        }
        var fallback=a.movement.escape(bot,arena,centre,8);
        if(fallback!=null)bot.getNavigation().moveTo(fallback.x,fallback.y,fallback.z,1);
        return fallback;
    }
    /** Shot by a seeker but still alive: found out, so run. */
    void struck(LivingEntity seeker){
        var server=BotManager.server();if(server==null)return;int now=server.getTickCount();
        struckAt=now;remembered.put(seeker.getUUID(),seeker.position());rememberedAt.put(seeker.getUUID(),now);
        nervous=99;
        if(state!=Hide.FLEE){expose(now);state=Hide.FLEE;fleeUntil=now+300;calmSince=now;fleeTo=null;}
    }
    /** A seeker fired: close and toward this block is a warning, a wasted shot nearby is a chance to taunt. */
    void gunfire(BotAgent a,LivingEntity shooter){
        if(!a.team.equals("ALPHA")||a.bot==null)return;
        var hunt=MatchManager.realPropHunt();if(hunt==null||hunt.hider(shooter.getUUID())||!hunt.participant(shooter.getUUID()))return;
        var prop=a.bot.disguise();if(prop==null)return;
        double distance=shooter.distanceTo(prop);if(distance>16)return;
        int now=BotManager.server().getTickCount();
        var toMe=prop.getBoundingBox().getCenter().subtract(shooter.getEyePosition()).normalize();
        double dot=shooter.getLookAngle().dot(toMe);
        if(dot>Math.cos(Math.toRadians(15)))nervous+=.5;
        else if(now-struckAt>40)missAt=now;
    }

    // ---- sight ----
    private static boolean sees(LivingEntity viewer,Vec3 point){return clear(viewer.level(),viewer.getEyePosition(),point,viewer);}
    private static boolean clear(Level level,Vec3 from,Vec3 to,net.minecraft.world.entity.Entity viewer){
        var hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,viewer));
        return hit.getType()==HitResult.Type.MISS||hit.getLocation().distanceToSqr(to)<.36;
    }
    private static boolean blocked(Level level,Vec3 from,Vec3 to,net.minecraft.world.entity.Entity viewer){return !clear(level,from,to,viewer);}
    private static boolean inView(float yaw,Vec3 eye,Vec3 point,double fov){
        var d=point.subtract(eye);float angle=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90;
        return Math.abs(Mth.wrapDegrees(angle-yaw))<=fov/2;
    }
}
