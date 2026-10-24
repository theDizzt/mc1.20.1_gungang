package dev.gungang.acoustics.server;

import dev.gungang.acoustics.server.BotTactics.Action;
import dev.gungang.acoustics.server.BotTree.Node;
import dev.gungang.acoustics.server.BotTree.Status;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Behaviour trees that carry out the action {@link BotTactics} chose. Each run gets a fresh tree; a step that cannot
 * be done (no cover, unreachable spot, cover compromised while moving) fails the action so utility re-decides.
 * The bot's live state arrives in {@link Ctx}, refreshed every tick before {@link #run}.
 */
final class BotActions {
    static final class Ctx {
        BotEntity bot;ArenaMap arena;String team;BotSettings settings;int now;boolean pathTurn,melee,shielding,reloading;
        LivingEntity target;BotPerception perception;BotMovement movement;BotHealth health;BotAim aim;
        Vec3 teamPoint;
        boolean reloadRequested,allowFire=true;
        float ammo;
        BotCombatProfile profile=BotCombatProfile.RIFLE;
        /** Eye positions of every enemy the bot currently knows about (seen or remembered). */
        List<Vec3> threats() {return perception.contacts().stream().map(c->c.position.add(0,1.6,0)).toList();}
    }
    private final Ctx ctx=new Ctx();
    private Action running;
    private Node tree;

    Ctx ctx() {return ctx;}
    void reset() {running=null;tree=null;}
    boolean holdingCover() {return running==Action.COVER&&tree!=null;}

    Status run(Action action) {
        ctx.allowFire=true;
        if(action!=running||tree==null) {ctx.movement.halt(ctx.bot);running=action;tree=build(action);}
        var status=tree.tick();
        if(status!=Status.RUNNING)tree=null;
        return status;
    }

    private Node build(Action action) {
        return switch(action) {
            case ENGAGE->BotTree.leaf(this::engage);
            case PUSH->BotTree.leaf(this::push);
            case COVER->cover();
            case RETREAT->moveTo(()->ctx.movement.retreatSpot(ctx.bot,ctx.arena,ctx.team,ctx.threats()),90,true);
            case REGROUP->regroup();
            case SUPPORT->support();
            case HEAL->BotTree.leaf(this::heal);
            case INVESTIGATE->investigate();
            case PATROL->patrol();
            case RALLY->moveTo(()->ctx.teamPoint,240,true);
            case EVADE->moveTo(()->{
                var hazard=ctx.perception.danger(ctx.bot.position());
                return hazard==null?null:ctx.movement.escape(ctx.bot,ctx.arena,hazard.center(),hazard.radius());
            },120,false);
        };
    }

    private Status engage() {
        if(ctx.target==null)return Status.FAILURE;
        if(!ctx.melee&&ctx.bot.distanceTo(ctx.target)>ctx.profile.engage())ctx.allowFire=false;
        // Gun range: strafe every tick. Melee or out of range: close in along a path.
        if(!ctx.movement.combat(ctx.bot,ctx.arena,ctx.target,ctx.now,ctx.melee,ctx.shielding)&&ctx.pathTurn)approach();
        return Status.RUNNING;
    }
    private Status push() {
        if(ctx.target==null)return Status.FAILURE;
        if(!ctx.melee&&ctx.bot.distanceTo(ctx.target)<=ctx.profile.far())return engage();
        if(ctx.pathTurn)approach();
        return Status.RUNNING;
    }
    private void approach() {
        if(ctx.melee) {
            ctx.movement.pursue(ctx.bot,ctx.arena,ctx.team,ctx.target,ctx.now);
            return;
        }
        ctx.movement.travel(ctx.bot,ctx.arena,ctx.team,ctx.target.position(),ctx.now);
    }

    /** Hide, reload, expose briefly, and return along the same safe route. Two bursts per run. */
    private Node cover() {
        var state=new Object(){Vec3 spot,peek;int phase,started=ctx.now,phaseAt=ctx.now,bursts;float health=ctx.bot.getHealth();};
        return BotTree.leaf(()->{
            ctx.allowFire=false;
            if(ctx.now-state.started>320)return Status.FAILURE;
            var lead=ctx.perception.lead(ctx.now);
            if(lead!=null&&ctx.target==null)ctx.aim.look(ctx.bot,lead.predicted(ctx.now).add(0,1.5,0),ctx.settings);
            if(state.spot==null) {
                var cover=ctx.movement.findCover(ctx.bot,ctx.arena,ctx.team,ctx.threats());
                if(cover==null)return BotMovement.spent()?Status.RUNNING:Status.FAILURE;
                state.spot=cover.spot();
                if(cover.path()!=null&&!ctx.bot.getNavigation().moveTo(cover.path(),1))return Status.FAILURE;
            }
            if(!ctx.movement.hidden(ctx.bot,state.spot,ctx.threats()))return Status.FAILURE;
            if((state.phase==2||state.phase==3)&&(ctx.bot.getHealth()<state.health||ctx.reloading||ctx.ammo<.15f)) {
                state.phase=4;state.phaseAt=ctx.now;ctx.bot.getNavigation().stop();
            }
            if(state.phase==0||state.phase==2||state.phase==4) {
                Vec3 destination=state.phase==2?state.peek:state.spot;
                if(state.phase!=0&&!BotMovement.safeSegment(ctx.bot,ctx.arena,ctx.bot.position(),destination))return Status.FAILURE;
                if(ctx.bot.position().distanceToSqr(destination)>.36) {
                    if(ctx.now-state.phaseAt>80)return Status.FAILURE;
                    if(state.phase!=0) {
                        ctx.bot.getMoveControl().setWantedPosition(destination.x,destination.y,destination.z,1);
                        return Status.RUNNING;
                    }
                    if(ctx.bot.getNavigation().isDone()) {
                        var path=BotMovement.path(ctx.bot,net.minecraft.core.BlockPos.containing(destination),0);
                        if(path==null&&BotMovement.spent())return Status.RUNNING;
                        if(path==null||!path.canReach()||!ctx.bot.getNavigation().moveTo(path,1))return Status.FAILURE;
                    }
                    return Status.RUNNING;
                }
                ctx.bot.getNavigation().stop();
                if(state.phase==4&&++state.bursts>=2)return Status.SUCCESS;
                state.phase=state.phase==2?3:1;state.phaseAt=ctx.now;state.health=ctx.bot.getHealth();
            }
            if(state.phase==1) {
                if(ctx.target!=null)return Status.FAILURE;
                if(ctx.ammo<.5f&&!ctx.perception.underFire(ctx.now))ctx.reloadRequested=true;
                if(ctx.reloading||ctx.reloadRequested||ctx.now-state.phaseAt<20)return Status.RUNNING;
                if(lead==null||ctx.now-lead.updated>100)return Status.SUCCESS;
                state.peek=ctx.movement.peekSpot(ctx.bot,ctx.arena,state.spot,lead.predicted(ctx.now));
                if(state.peek==null)return Status.SUCCESS;
                state.phase=2;state.phaseAt=ctx.now;
            } else if(state.phase==3) {
                ctx.allowFire=!ctx.reloading;
                if(ctx.now-state.phaseAt>=ctx.profile.exposureTicks()||ctx.target==null&&ctx.now-state.phaseAt>=10) {
                    ctx.allowFire=false;state.phase=4;state.phaseAt=ctx.now;
                }
            }
            return Status.RUNNING;
        });
    }
    private Node regroup() {
        return BotTree.sequence(moveTo(()->{
            var ally=ctx.perception.nearestAlly(ctx.bot);if(ally==null)return null;
            var lead=ctx.perception.lead(ctx.now);
            return ctx.movement.besideAlly(ctx.bot,ctx.arena,ally,lead==null?null:lead.position);
        },120,true));
    }
    private Node support() {
        return BotTree.sequence(moveTo(()->{
            var ally=ctx.perception.engagedAlly(ctx.now);var threat=ctx.perception.allyThreat();
            return ally==null||threat==null?null:ctx.movement.flankSpot(ctx.bot,ctx.arena,ally,threat);
        },120,true));
    }
    private Status heal() {
        if(ctx.health.navigate(ctx.bot,ctx.now,ctx.settings.range())) {ctx.movement.interrupt();return Status.RUNNING;}
        return ctx.bot.getHealth()>=ctx.bot.getMaxHealth()*.9f?Status.SUCCESS:Status.FAILURE;
    }
    /** Walk to where the clue points (predicted, re-read every tick), gun toward it, then look around before giving up. */
    private Node investigate() {
        var state=new Object(){int searchFrom=-1;java.util.UUID id;};
        return BotTree.sequence(
                BotTree.leaf(()->{
                    var lead=ctx.perception.lead(ctx.now);if(lead==null)return Status.SUCCESS;
                    state.id=lead.id;
                    Vec3 point=lead.predicted(ctx.now);
                    ctx.aim.look(ctx.bot,point.add(0,1.5,0),ctx.settings);
                    if(arrived(ctx.bot.position(),point,4))return Status.SUCCESS;
                    if(ctx.pathTurn&&ctx.movement.travel(ctx.bot,ctx.arena,ctx.team,point,ctx.now))return arrived(ctx.bot.position(),point,16)?Status.SUCCESS:Status.FAILURE;
                    return Status.RUNNING;
                }),
                BotTree.leaf(()->{
                    if(state.searchFrom<0)state.searchFrom=ctx.now;
                    ctx.movement.search(ctx.bot,ctx.now,state.searchFrom);
                    if(ctx.now-state.searchFrom<40)return Status.RUNNING;
                    // Nothing here: forget the clue so the bot does not walk back to the same empty spot.
                    if(state.id!=null)ctx.perception.forget(state.id);
                    return Status.SUCCESS;
                }));
    }
    private Node patrol() {
        var state=new Object(){Vec3 destination,cover;int coverAt,holdAt=-1,nextCover=ctx.now;};
        return BotTree.leaf(()->{
            if(state.cover!=null) {
                if(ctx.now-state.coverAt>100||BotMovement.knownDanger(ctx.bot,state.cover)) {
                    state.cover=null;state.holdAt=-1;ctx.movement.halt(ctx.bot);
                } else if(arrived(ctx.bot.position(),state.cover,1)) {
                    ctx.bot.getNavigation().stop();
                    if(state.holdAt<0)state.holdAt=ctx.now;
                    ctx.movement.search(ctx.bot,ctx.now,state.holdAt);
                    if(ctx.now-state.holdAt<40+Math.floorMod(ctx.bot.getUUID().hashCode(),41))return Status.RUNNING;
                    state.cover=null;state.holdAt=-1;
                } else if(!ctx.bot.getNavigation().isDone()) {ctx.aim.idle(ctx.bot);return Status.RUNNING;}
                else state.cover=null;
            }
            if(state.destination!=null&&arrived(ctx.bot.position(),state.destination,4)&&ctx.now>=state.nextCover) {
                state.destination=null;state.nextCover=ctx.now+200;
                var cover=ctx.movement.scoutCover(ctx.bot,ctx.arena,ctx.team);
                if(cover!=null&&(cover.path()==null||ctx.bot.getNavigation().moveTo(cover.path(),1))) {
                    state.cover=cover.spot();state.coverAt=ctx.now;state.holdAt=-1;
                    return Status.RUNNING;
                }
            }
            if(ctx.pathTurn||ctx.bot.getNavigation().isDone()) {
                ctx.movement.travel(ctx.bot,ctx.arena,ctx.team,null,ctx.now);
                state.destination=ctx.movement.goal();
            }
            ctx.aim.idle(ctx.bot);
            return Status.RUNNING;
        });
    }

    private Node moveTo(java.util.function.Supplier<Vec3> where,int timeout,boolean watch) {return moveTo(where,timeout,watch,null);}
    /**
     * Go to a spot chosen once: SUCCESS on arrival, FAILURE when none could be chosen, it proves unreachable, it takes
     * too long, or (for cover) the bot is hit hard on the way. {@code watch} keeps the view on the freshest threat.
     */
    private Node moveTo(java.util.function.Supplier<Vec3> where,int timeout,boolean watch,Object coverState) {
        var state=new Object(){Vec3 spot;int started=-1;float damage;};
        return BotTree.leaf(()->{
            if(state.started<0) {
                state.started=ctx.now;state.damage=ctx.perception.recentDamage();
                state.spot=where.get();
                if(state.spot==null&&BotMovement.spent()) {state.started=-1;return Status.RUNNING;}
                if(state.spot==null)return Status.FAILURE;
            }
            if(flat(ctx.bot.position(),state.spot)<2.25&&Math.abs(ctx.bot.getY()-state.spot.y)<1.5)return Status.SUCCESS;
            if(ctx.now-state.started>timeout)return Status.FAILURE;
            if(coverState!=null&&ctx.perception.recentDamage()-state.damage>ctx.bot.getMaxHealth()*.3f)return Status.FAILURE;
            if(watch) {var lead=ctx.perception.lead(ctx.now);if(lead!=null)ctx.aim.look(ctx.bot,lead.predicted(ctx.now).add(0,1.5,0),ctx.settings);else ctx.aim.idle(ctx.bot);}
            if(ctx.pathTurn&&ctx.movement.travel(ctx.bot,ctx.arena,ctx.team,state.spot,ctx.now))
                return arrived(ctx.bot.position(),state.spot,16)?Status.SUCCESS:Status.FAILURE;
            return Status.RUNNING;
        });
    }
    private static double flat(Vec3 a,Vec3 b) {double x=a.x-b.x,z=a.z-b.z;return x*x+z*z;}
    private static boolean arrived(Vec3 a,Vec3 b,double radiusSquared){return flat(a,b)<radiusSquared&&Math.abs(a.y-b.y)<1.5;}
}
