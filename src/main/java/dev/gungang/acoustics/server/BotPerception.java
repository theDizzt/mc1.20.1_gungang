package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * What the bot is allowed to know. The server knows every position; the bot only learns enemies it can see (inside
 * its vision cone with a clear line, or brushing past within arm's reach), hear (gunfire, nearby footsteps, a
 * teammate getting shot), feel (being hit) or that a scan reveals. Heard positions carry error that grows with
 * distance. Enemies and allies are players and other bots alike. Each enemy is remembered for {@code memorySeconds} after the last cue, with its last observed velocity,
 * so the bot can predict where it went instead of tracking it through walls.
 */
final class BotPerception {
    enum Cue {SEEN,HEARD,HIT,ALLY,SCAN}
    static final class Contact {
        final UUID id;LivingEntity player;Vec3 position,velocity=Vec3.ZERO;int updated,seenAt=-100000;Cue cue;boolean visible;
        Contact(UUID id){this.id=id;}
        /** Where the enemy probably is now: last position plus up to a second of its last velocity, capped at 8 blocks. */
        Vec3 predicted(int now) {
            Vec3 ahead=new Vec3(velocity.x,0,velocity.z).scale(Math.min(20,now-updated));
            return position.add(ahead.length()>8?ahead.normalize().scale(8):ahead);
        }
    }
    private static final double FOOTSTEPS=14,PERIPHERAL=4,GUNFIRE=48,SCAN_RANGE=64;
    /**
     * Dangerous ground the bot has noticed: an enemy mine or trap it saw (remembered until the device is gone),
     * a burning molotov patch or an orbital strike marker. {@code flee}: get out now, not just path around it.
     */
    record Hazard(java.util.UUID id,Vec3 center,double radius,boolean flee) {
        boolean covers(double x,double y,double z) {double dx=x-center.x,dz=z-center.z;return dx*dx+dz*dz<=radius*radius&&Math.abs(y-center.y)<4;}
    }
    private final Map<UUID,Hazard> hazards=new HashMap<>();
    private final Map<UUID,Contact> contacts=new HashMap<>();
    private final Map<UUID,Vec3> lastSample=new HashMap<>();
    private final List<LivingEntity> allies=new ArrayList<>();
    private int visibleCount,allyEngagedAt=-100000,lastHurt=-100000;
    private LivingEntity engagedAlly;
    private UUID lastAttacker;
    private Vec3 allyThreat;
    private float damageWindow;
    private int damageAt;
    private int nextWideHazardScan;

    void reset() {hazards.clear();contacts.clear();lastSample.clear();allies.clear();visibleCount=0;engagedAlly=null;allyThreat=null;damageWindow=0;lastHurt=allyEngagedAt=-100000;lastAttacker=null;damageAt=0;nextWideHazardScan=0;}

    void update(BotEntity bot,String team,BotSettings settings,int now) {
        var level=bot.level();double range=settings.visionRange();
        int memory=(int)Math.round(settings.memorySeconds()*20);
        visibleCount=0;allies.clear();
        for(var contact:contacts.values())contact.visible=false;
        // A running scan by our team (anyone's scanner, ours included) shows every enemy, as on a player's screen.
        boolean scan=CombatGadgets.teamScanning(bot);
        hazards(bot,team,now);
        var everyone=new ArrayList<LivingEntity>(level.players());everyone.addAll(BotManager.entities());
        for(var player:everyone) {
            if(player==bot||!player.isAlive()||player.isSpectator()||player.level()!=level||player instanceof ServerPlayer p&&(LobbyManager.respawning(p.getUUID())||p.isCreative()&&!BotManager.side(p).equals(team)))continue;
            String side=BotManager.side(player);
            if(!side.equals("ALPHA")&&!side.equals("BRAVO"))continue;
            if(side.equals(team)) {if(player.distanceToSqr(bot)<64*64)allies.add(player);continue;}
            double distanceSqr=player.distanceToSqr(bot);
            Vec3 previous=lastSample.put(player.getUUID(),player.position());
            if(scan&&distanceSqr<=SCAN_RANGE*SCAN_RANGE)note(bot,player,Cue.SCAN,now,.5);
            if(distanceSqr>range*range)continue;
            boolean inView=distanceSqr<=PERIPHERAL*PERIPHERAL||inCone(bot,player.getEyePosition(),settings.fov());
            if(inView&&bot.hasLineOfSight(player)&&!SpawnGateProtection.separates(bot,player)) {
                var contact=contacts.computeIfAbsent(player.getUUID(),Contact::new);
                if(contact.position!=null&&contact.cue==Cue.SEEN&&now>contact.updated)
                    contact.velocity=player.position().subtract(contact.position).scale(1.0/(now-contact.updated));
                contact.player=player;contact.position=player.position();contact.updated=now;contact.seenAt=now;contact.cue=Cue.SEEN;contact.visible=true;
                visibleCount++;continue;
            }
            // Footsteps: someone moving nearby without sneaking is audible through walls, roughly.
            boolean moving=previous!=null&&previous.distanceToSqr(player.position())>.0025;
            if(moving&&!player.isCrouching()&&distanceSqr<=FOOTSTEPS*FOOTSTEPS)note(bot,player,Cue.HEARD,now,Math.sqrt(distanceSqr)/7);
        }
        contacts.values().removeIf(c->!c.visible&&now-c.updated>(c.cue==Cue.SEEN||c.cue==Cue.HIT?memory:memory*3/2));
        lastSample.keySet().removeIf(id->((net.minecraft.server.level.ServerLevel)level).getEntity(id)==null);
        if(now-damageAt>60)damageWindow=0;
    }
    /** A non-visual cue; never overwrites a fresher sighting with a vaguer guess. */
    private void note(BotEntity bot,LivingEntity player,Cue cue,int now,double error) {
        var contact=contacts.computeIfAbsent(player.getUUID(),Contact::new);
        if(contact.visible||contact.cue==Cue.SEEN&&now-contact.updated<10)return;
        var random=bot.getRandom();
        contact.player=player;contact.position=player.position().add((random.nextDouble()*2-1)*error,0,(random.nextDouble()*2-1)*error);
        contact.velocity=Vec3.ZERO;contact.updated=now;contact.cue=cue;
    }
    void gunfire(BotEntity bot,LivingEntity shooter,int now) {
        double distance=shooter.distanceTo(bot);
        if(distance<=GUNFIRE)note(bot,shooter,Cue.HEARD,now,distance/12);
    }
    void hit(BotEntity bot,LivingEntity attacker,int now,float amount) {
        lastHurt=now;lastAttacker=attacker.getUUID();
        if(now-damageAt>60)damageWindow=0;
        damageWindow+=amount;damageAt=now;
        note(bot,attacker,Cue.HIT,now,1);
    }
    /** A teammate is under fire: like a callout, the bot learns roughly where from and that the ally needs help. */
    void allyHit(BotEntity bot,LivingEntity ally,LivingEntity attacker,int now) {
        if(ally.distanceTo(bot)>48)return;
        engagedAlly=ally;allyEngagedAt=now;
        note(bot,attacker,Cue.ALLY,now,3);
        // Use the same uncertain report (or our fresher sighting) for support and investigation.
        allyThreat=contacts.get(attacker.getUUID()).position;
    }
    private void hazards(BotEntity bot,String team,int now) {
        var level=bot.level();String own=team.equals("ALPHA")?"Red":"Blue";
        // Forget devices that are gone; a sprung trap is no longer a threat.
        hazards.values().removeIf(h->{
            var entity=((net.minecraft.server.level.ServerLevel)level).getEntity(h.id());
            return entity==null||entity.isRemoved()||entity instanceof BearTrapEntity trap&&trap.sprung()
                    ||entity instanceof OrdnanceEntity strike&&strike.orbital()&&strike.detonated();
        });
        // Nearby threats retain the normal think cadence. Only distant warning discovery is throttled.
        // Include the configured blast radius so a large strike covering us is never treated as distant.
        boolean wide=now>=nextWideHazardScan;
        double radius=wide?96:Math.min(96,Math.max(24,ExtraGadgetConfig.orbital.radius()+3));
        if(wide)nextWideHazardScan=now+20;
        for(var entity:level.getEntities(bot,bot.getBoundingBox().inflate(radius),e->e instanceof DeployableEntity||e instanceof BearTrapEntity||e instanceof OrdnanceEntity)) {
            if(hazards.containsKey(entity.getUUID()))continue;
            double distance=entity.distanceTo(bot);
            if(entity instanceof DeployableEntity mine&&!mine.wall()&&!own.equals(mine.teamName())&&distance<=10&&bot.hasLineOfSight(mine))
                hazards.put(mine.getUUID(),new Hazard(mine.getUUID(),mine.position(),GadgetConfig.mine.triggerRadius()+.8,false));
            else if(entity instanceof BearTrapEntity trap&&!trap.sprung()&&!own.equals(trap.teamName())&&distance<=10&&bot.hasLineOfSight(trap))
                hazards.put(trap.getUUID(),new Hazard(trap.getUUID(),trap.position(),1.0,false));
            // Fire and orbital strikes hurt everyone, allies included; the strike's beam is visible from afar.
            else if(entity instanceof OrdnanceEntity strike&&!strike.orbital()&&distance<=24)
                hazards.put(strike.getUUID(),new Hazard(strike.getUUID(),strike.position(),ExtraGadgetConfig.molotov.radius()+.5,true));
            else if(entity instanceof OrdnanceEntity strike&&strike.orbital()&&!strike.detonated()&&distance<=96)
                hazards.put(strike.getUUID(),new Hazard(strike.getUUID(),strike.position(),ExtraGadgetConfig.orbital.radius()+3,true));
        }
    }
    /** Ground to route around (pathfinding cost), for every hazard the bot knows of. */
    boolean avoid(double x,double y,double z) {for(var h:hazards.values())if(h.covers(x,y,z))return true;return false;}
    /** The flee-now hazard the bot is standing in, if any (the largest when several overlap). */
    Hazard danger(Vec3 at) {
        return hazards.values().stream().filter(h->h.flee()&&h.covers(at.x,at.y,at.z)).max(Comparator.comparingDouble(Hazard::radius)).orElse(null);
    }
    int hazardCount() {return hazards.size();}

    private static boolean inCone(BotEntity bot,Vec3 point,int fov) {
        if(fov>=360)return true;
        Vec3 d=point.subtract(bot.getEyePosition());
        float yaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90;
        return Math.abs(Mth.wrapDegrees(yaw-bot.getYHeadRot()))<=fov/2f;
    }

    /**
     * Best enemy to shoot among the visible ones: nearest, but staying on the current target (switching costs a
     * fresh reaction) and turning on whoever just hit us.
     */
    LivingEntity target(BotEntity bot,LivingEntity current,int now) {
        Contact best=null;double bestScore=Double.MAX_VALUE;
        for(var c:contacts.values()) {
            if(!c.visible||c.player==null)continue;
            double score=c.player.distanceToSqr(bot);
            if(c.player==current)score*=.6;
            if(c.id.equals(lastAttacker)&&now-lastHurt<40)score*=.5;
            if(score<bestScore) {best=c;bestScore=score;}
        }
        return best==null?null:best.player;
    }
    /** The freshest enemy we cannot see right now: what to investigate. */
    Contact lead(int now) {
        return contacts.values().stream().filter(c->!c.visible).max(Comparator.comparingInt(c->c.updated)).orElse(null);
    }
    Contact contact(UUID id) {return contacts.get(id);}
    /** A clue that led nowhere; a fresh cue about the same enemy brings it back. */
    void forget(UUID id) {var c=contacts.get(id);if(c!=null&&!c.visible)contacts.remove(id);}
    Collection<Contact> contacts() {return contacts.values();}
    int visible() {return visibleCount;}
    int known() {return contacts.size();}
    List<LivingEntity> allies() {return allies;}
    int alliesNear(BotEntity bot,double radius) {return (int)allies.stream().filter(a->a.distanceToSqr(bot)<=radius*radius).count();}
    LivingEntity nearestAlly(BotEntity bot) {return allies.stream().min(Comparator.comparingDouble(a->a.distanceToSqr(bot))).orElse(null);}
    /** An ally fighting within the last two seconds, and roughly where its enemy is. */
    LivingEntity engagedAlly(int now) {return engagedAlly!=null&&engagedAlly.isAlive()&&now-allyEngagedAt<40?engagedAlly:null;}
    Vec3 allyThreat() {return allyThreat;}
    boolean underFire(int now) {return now-lastHurt<30;}
    /** Damage taken in the last three seconds. */
    float recentDamage() {return damageWindow;}
}
