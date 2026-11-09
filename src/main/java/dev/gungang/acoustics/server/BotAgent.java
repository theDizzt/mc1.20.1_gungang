package dev.gungang.acoustics.server;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/**
 * One bot in a match: its body, loadout and mind. Each tick: perceive ({@link BotPerception}: only what this bot
 * could see, hear or feel) → decide ({@link BotTactics}, utility scores) → act ({@link BotActions}, behaviour trees)
 * → aim and fire ({@link BotAim}). Shared settings, profiles and events live in {@link BotManager}.
 */
final class BotAgent {
    final int slot;
    final UUID id;
    String team;
    BotEntity bot;
    BotGun gun;
    Loadouts.Weapon weapon,secondary;
    ItemStack primaryStack,secondaryStack;
    boolean usingSecondary,secondarySupplied;
    private int nextSwitch,switchedAt;
    final BotHealth health=new BotHealth();
    final BotMovement movement=new BotMovement();
    final BotGadgets gadgets=new BotGadgets();
    final BotPerception perception=new BotPerception();
    final BotTactics tactics=new BotTactics();
    final BotActions actions=new BotActions();
    final BotAim aim=new BotAim();
    /** 숨바꼭질 mind: hiding as a block, or seeking without ever being told which blocks are players. */
    final BotPropHunt propHunt=new BotPropHunt();
    private List<Loadouts.Weapon> primaryChoices=List.of(),secondaryChoices=List.of();
    private BotLoadoutChoice.Outcome lastLife;
    private final Map<String,List<BotLoadoutChoice.Outcome>> primaryHistory=new HashMap<>(),secondaryHistory=new HashMap<>();
    private boolean chooseOnSpawn=true;
    /** 랜덤 무기: the rotation issued this loadout; deaths keep it instead of choosing again. */
    private boolean rotationLocked;
    private int bornAt;
    private double lifeDamage,encounterDistance;
    String choiceReason="첫 출전";
    LivingEntity target;
    int respawnAt,nextThink,nextPath,nextShot;
    private long completedPoint,activePoint;

    BotAgent(int slot,String team){this.slot=slot;this.id=BotDatabase.id(slot);this.team=team;}

    void damageDealt(double amount){if(bot!=null&&Double.isFinite(amount)&&amount>0)lifeDamage+=amount;}
    BotEntity entity(){return bot;}
    String name(){return BotManager.name(slot);}

    /**
     * Mobs only tick inside a player's simulation distance, so a bot far from everyone would plan paths but never
     * walk them. A short-lived region ticket keeps its own chunks entity-ticking; it lapses on its own once the bot is gone.
     */
    private static final TicketType<net.minecraft.world.level.ChunkPos> CHUNKS=
            TicketType.create("gungang_bot",Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong),40);
    private void keepTicking(){
        var chunk=bot.chunkPosition();
        // Distance 3: the bot's chunk and its neighbours tick entities; three chunks around stay loaded for path searches.
        ((ServerLevel)bot.level()).getChunkSource().addRegionTicket(CHUNKS,chunk,3,chunk);
    }

    void activate(Loadouts loadouts)throws Exception{
        movement.routeMemory.clear();rotationLocked=false;
        weapon=BotManager.selectWeapon(loadouts);secondary=BotManager.selectSecondary(loadouts);
        primaryChoices=loadouts.primary().stream().filter(BotManager::supportedPrimary).toList();
        secondaryChoices=loadouts.secondary().stream().filter(BotManager::supportedSecondary).toList();
        chooseOnSpawn=true;respawnAt=BotManager.server().getTickCount();
        MatchManager.botParticipant(id,name(),team);
        spawn();
    }
    private void spawn()throws Exception{
        var hunt=MatchManager.realPropHunt();if(hunt!=null){spawnPropHunt(hunt);return;}
        var server=BotManager.server();var arena=BotManager.arena();var settings=BotManager.settings();
        var level=BotManager.level();if(level==null)throw new IllegalStateException("Bot dimension unavailable");
        if(chooseOnSpawn){
            String oldPrimary=lastLife==null?null:weapon.id(),oldSecondary=lastLife==null||secondary==null?null:secondary.id();
            weapon=choose(primaryChoices,settings.weapon(),oldPrimary,false);
            secondary=choose(secondaryChoices,settings.secondary(),oldSecondary,true);
            choiceReason=lastLife==null?"첫 출전: 체력·이동·사거리·화력·장비 역할 비교":
                    !lastLife.combat()?"비전투 사망: 장비 유지":lastLife.productive()?"직전 교전 성과 양호: 자동 장비 유지":
                    "직전 교전 성과 낮음: 교전 거리·무기 성능·최근 장비별 성과로 대안 비교";
            chooseOnSpawn=false;
            LogUtils.getLogger().info("[GunGang] Bot {} loadout primary={} secondary={} reason={}",slot+1,weapon.id(),secondary==null?"none":secondary.id(),choiceReason);
        }
        if(LobbyManager.secondaryBanned(team))secondary=null; // head-count handicap: the bigger team plays without secondaries
        bot=BotEntity.TYPE.get().create(level);if(bot==null)throw new IllegalStateException("Bot creation failed");
        bot.setUUID(id);
        // Free-for-all: the free-for-all spawn farthest from everyone, like a player's respawn.
        var ffa=MatchManager.freeForAll();
        // 이동점령 attackers respawn at the current point's attack spawn once the match runs.
        boolean assault=MatchManager.botRunning()&&team.equals("BRAVO")&&MatchManager.attackSpawn()!=null;
        var spawn=ffa!=null?ffa.spawnFor(bot):assault?MatchManager.attackSpawn():BotMovement.homeSpawn(arena,team);
        level.getChunk(net.minecraft.core.BlockPos.containing(spawn.x(),spawn.y(),spawn.z()));
        bot.moveTo(spawn.x(),spawn.y(),spawn.z(),spawn.yaw(),spawn.pitch());
        // Free-for-all hides every name tag; the bot's must not give it away either.
        bot.setCustomName(Component.literal(name()).withStyle(LobbyManager.options().formatting(team)));bot.setCustomNameVisible(ffa==null);
        bot.skin(BotManager.skin(slot));
        bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(LoadoutAttributes.maxHealth(weapon.maxHealth(),LobbyManager.healthScale(team)));
        usingSecondary=false;secondarySupplied=false;nextSwitch=switchedAt=0;health.reset();movement.reset();
        primaryStack=weapon.stack();secondaryStack=secondary==null?ItemStack.EMPTY:secondary.stack();
        updateSpeed();
        gadgets.equip(bot,secondary,secondaryStack,heldSecondary());
        perception.reset();tactics.reset(server.getTickCount());actions.reset();aim.reset(bot);
        bot.setHealth(bot.getMaxHealth());bot.setItemSlot(EquipmentSlot.MAINHAND,primaryStack);
        bot.setDropChance(EquipmentSlot.MAINHAND,0);
        var scoreboard=server.getScoreboard();
        if(ffa!=null)ffa.add(bot); // own colour team, score row and respawn protection
        else {
            String key=team.equals("ALPHA")?"Red":"Blue";
            var side=scoreboard.getPlayerTeam(key);if(side==null)side=scoreboard.addPlayerTeam(key);
            side.setColor(LobbyManager.options().formatting(team));scoreboard.addPlayerToTeam(bot.getScoreboardName(),side);
        }
        if(!level.addFreshEntity(bot))throw new IllegalStateException("Bot entity was rejected");
        keepTicking();
        // Preparation gates are closed. Search only once running, after the gates have opened. Free-for-all and
        // 이동점령 attack spawns have no spawn room (the attackers' respawn protection is a short resistance instead).
        if(ffa==null&&!assault)movement.prepareSpawnExit();
        if(assault)bot.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,60,5,false,false));
        gun=BotGun.supports(bot.getMainHandItem())?new BotGun(bot):null;
        bornAt=server.getTickCount();lifeDamage=0;encounterDistance=0;
        // Each bot thinks and paths on its own offset, so several bots never pile their work onto the same tick.
        int now=server.getTickCount();target=null;nextShot=now;
        nextThink=now+slot*settings.thinkTicks()/BotManager.MAX;nextPath=now+slot*settings.pathTicks()/BotManager.MAX;
    }
    /**
     * 숨바꼭질: hiders start at the ALPHA spawn as a block with nothing in hand, seekers in their waiting room with the
     * issued shotgun. A found hider stays out for good.
     */
    private void spawnPropHunt(PropHuntMatch hunt)throws Exception{
        var server=BotManager.server();var arena=BotManager.arena();var settings=BotManager.settings();
        var level=BotManager.level();if(level==null)throw new IllegalStateException("Bot dimension unavailable");
        boolean hider=team.equals("ALPHA");
        if(!hunt.participant(id)||hider&&!hunt.alive(id)){respawnAt=Integer.MAX_VALUE;return;}
        bot=BotEntity.TYPE.get().create(level);if(bot==null)throw new IllegalStateException("Bot creation failed");
        bot.setUUID(id);
        var spawn=hider?arena.alphaSpawn():arena.bravoSpawn();
        level.getChunk(net.minecraft.core.BlockPos.containing(spawn.x(),spawn.y(),spawn.z()));
        bot.moveTo(spawn.x(),spawn.y(),spawn.z(),spawn.yaw(),spawn.pitch());
        bot.setCustomName(Component.literal(name()).withStyle(LobbyManager.options().formatting(team)));bot.setCustomNameVisible(!hider);
        bot.skin(BotManager.skin(slot));
        secondary=null;secondaryStack=ItemStack.EMPTY;usingSecondary=false;secondarySupplied=false;nextSwitch=switchedAt=0;
        primaryStack=hider?ItemStack.EMPTY:hunt.seekerShotgun();
        bot.setItemSlot(EquipmentSlot.MAINHAND,primaryStack);bot.setDropChance(EquipmentSlot.MAINHAND,0);
        health.reset();movement.reset();perception.reset();tactics.reset(server.getTickCount());actions.reset();aim.reset(bot);
        var scoreboard=server.getScoreboard();String key=hider?"Red":"Blue";
        var side=scoreboard.getPlayerTeam(key);if(side==null)side=scoreboard.addPlayerTeam(key);
        side.setColor(LobbyManager.options().formatting(team));scoreboard.addPlayerToTeam(bot.getScoreboardName(),side);
        if(!level.addFreshEntity(bot))throw new IllegalStateException("Bot entity was rejected");
        keepTicking();
        hunt.botSpawned(bot);updateSpeed();
        gun=hider||!BotGun.supports(primaryStack)?null:new BotGun(bot);
        bornAt=server.getTickCount();lifeDamage=0;encounterDistance=0;target=null;nextShot=server.getTickCount();
        nextThink=server.getTickCount()+slot*settings.thinkTicks()/BotManager.MAX;
        propHunt.reset(this,hunt);
    }
    /** 숨바꼭질 tick: the match handles the disguise and waiting room; the bot only decides where to go and what to do. */
    private void propTick(PropHuntMatch hunt,boolean running)throws Exception{
        var server=BotManager.server();var arena=BotManager.arena();int now=server.getTickCount();
        if(bot!=null&&!bot.isRemoved())keepTicking();
        if(bot==null){if(now>=respawnAt)spawn();return;}
        if(bot.isRemoved()){bot=null;respawnAt=now+20;return;}
        if(!running){bot.getNavigation().stop();return;}
        var bounds=bot.getBoundingBox();
        if(bot.getY()<bot.level().getMinBuildHeight()||DeathZoneBlock.touching(bot)){
            hunt.eliminate(bot,null);return;
        }
        if(bot.parkour.active())return;
        if(bot.isInWater())bot.getJumpControl().jump();
        propHunt.tick(this,hunt,now);
    }
    /** 폭탄전 round start: a fresh body (full health and ammunition) at the side's spawn on the next tick. */
    void nextRound(){remove();respawnAt=BotManager.server().getTickCount();}
    /** 이동점령 start: from the attackers' holding room to the launch point. */
    void launch(ArenaMap.Spawn launch){
        if(bot==null)return;
        bot.getNavigation().stop();bot.teleportTo(launch.x(),launch.y(),launch.z());bot.setYRot(launch.yaw());bot.fallDistance=0;
        movement.reset();
    }
    /** Free-for-all opening grace: back to a spawn with full health, nothing recorded. */
    void openingDeath(){
        BotLearning.end(this,false);
        var ffa=MatchManager.freeForAll();if(bot==null||ffa==null)return;
        var spawn=ffa.spawnFor(bot);
        bot.setHealth(bot.getMaxHealth());bot.clearFire();bot.getNavigation().stop();bot.fallDistance=0;
        bot.teleportTo(spawn.x(),spawn.y(),spawn.z());bot.setYRot(spawn.yaw());movement.reset();
    }
    void remove(){
        BotLearning.end(this,false);
        var server=BotManager.server();
        if(bot!=null){
            if(server!=null)server.getScoreboard().removePlayerFromTeam(bot.getScoreboardName());
            bot.discard();bot=null;
        }
        if(server!=null)for(var level:server.getAllLevels())for(var entity:level.getAllEntities())
            if(entity instanceof Projectile projectile&&projectile.getOwner()!=null&&id.equals(projectile.getOwner().getUUID()))projectile.discard();
        gadgets.reset(null);gun=null;target=null;perception.reset();actions.reset();health.reset();movement.reset();
    }
    /** Match over: also clear this bot's deployables and cooldowns. */
    void close(){
        movement.routeMemory.clear();
        var server=BotManager.server();
        if(server!=null){GadgetManager.clearOwned(server,id);BearTraps.clear(server,id);}
        gadgets.clearCooldowns();remove();
    }
    private Loadouts.Weapon choose(List<Loadouts.Weapon> choices,String fixed,String previous,boolean secondarySlot){
        choices=choices.stream().filter(w->!LobbyManager.teamWeaponTaken(id,team,!secondarySlot,w.id())).toList();
        String requested=fixed;
        if(!fixed.equals("none")&&choices.stream().noneMatch(w->w.id().equals(requested)))fixed="";
        if(choices.isEmpty()&&!secondarySlot)throw new IllegalStateException("중복 제한으로 봇이 사용할 주무기가 없습니다.");
        var options=choices.stream().map(w->{
            var stack=w.stack();boolean melee=BotManager.melee(stack),held=BotManager.held(stack);
            var data=BotRanged.supports(stack)?BotRanged.data(stack):BotWeaponData.read(stack);
            var profile=BotCombatProfile.of(data.type(),w.category());
            String role=BotGadgets.kind(stack,held);
            return new BotLoadoutChoice.Option(w.id(),w.maxHealth(),w.movementSpeed(),melee,
                    melee?0:role.isEmpty()?profile.near():0,melee?3:role.isEmpty()?profile.far():0,
                    data.damage(),data.rpm(),data.magazine(),data.reloadSeconds(),role);
        }).toList();
        String chosen=BotLoadoutChoice.choose(options,fixed,previous,lastLife,secondarySlot,secondarySlot?secondaryHistory:primaryHistory);
        return chosen==null?null:choices.stream().filter(w->w.id().equals(chosen)).findFirst().orElseThrow();
    }
    /** Sprinting player pace per unit of forward input: speed 0.1 × sprint 1.3. */
    private static final double PLAYER_SPRINT=.1*1.3;
    /**
     * Path-walking mobs move attribute² (MoveControl feeds the speed in as the forward input as well), players
     * input 1 × attribute; both then lose the same 0.98 input damping. So the attribute is the square root of the
     * target pace: a sprinting player at the weapon's speed, scaled by the bot multiplier (1.0 = exactly that player).
     */
    void updateSpeed(){
        // 숨바꼭질 has no loadout: hiders and seekers alike move at a sprinting player's pace.
        if(bot!=null&&MatchManager.realPropHunt()!=null)bot.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.sqrt(PLAYER_SPRINT*BotManager.settings().movementSpeed()));
        else if(bot!=null&&weapon!=null)bot.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
                Math.sqrt(PLAYER_SPRINT*(usingSecondary?secondary:weapon).movementSpeed()/100*BotManager.settings().movementSpeed()));
    }
    private boolean heldSecondary(){return secondary!=null&&!secondary.id().equals(Defibrillator.ID)&&secondaryStack!=null&&!secondaryStack.isEmpty()&&BotManager.held(secondaryStack);}
    private void equip(boolean useSecondary,int now)throws ReflectiveOperationException{
        if(useSecondary==usingSecondary||useSecondary&&!heldSecondary()||now<nextSwitch)return;
        if(gun!=null)gun.unequip();
        usingSecondary=useSecondary;
        bot.setItemSlot(EquipmentSlot.MAINHAND,useSecondary?secondaryStack:primaryStack);
        gun=BotGun.supports(bot.getMainHandItem())?new BotGun(bot,useSecondary&&!secondarySupplied):null;
        if(useSecondary)secondarySupplied=true;
        updateSpeed();switchedAt=now;nextSwitch=now+60;nextShot=now+4;nextPath=0;
    }
    void died(UUID killer){
        BotLearning.end(this,true);
        var server=BotManager.server();
        lastLife=new BotLoadoutChoice.Outcome(killer!=null||lifeDamage>0||encounterDistance>0,lifeDamage,
                Math.max(0,server.getTickCount()-bornAt),encounterDistance>0?encounterDistance:12);
        remember(primaryHistory,weapon.id(),lastLife);
        if(secondary!=null)remember(secondaryHistory,secondary.id(),lastLife);
        chooseOnSpawn=!rotationLocked;
        remove();respawnAt=server.getTickCount()+LobbyManager.respawnTicks();
    }
    /**
     * 랜덤 무기: the rotation's weapons replace the loadout at once (a dead bot takes them on respawn) and stay through
     * deaths until the next change. A secondary the bot cannot use is left out.
     */
    void rotate(Loadouts.Weapon primary,Loadouts.Weapon second){
        weapon=primary;secondary=second!=null&&BotManager.supportedSecondary(second)&&!LobbyManager.secondaryBanned(team)?second:null;
        rotationLocked=true;chooseOnSpawn=false;choiceReason="랜덤 무기";
        if(bot==null||bot.isRemoved())return;
        try{if(gun!=null)gun.unequip();}catch(ReflectiveOperationException ignored){}
        usingSecondary=false;secondarySupplied=false;nextSwitch=switchedAt=0;
        primaryStack=weapon.stack();secondaryStack=secondary==null?ItemStack.EMPTY:secondary.stack();
        bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(LoadoutAttributes.maxHealth(weapon.maxHealth(),LobbyManager.healthScale(team)));
        if(bot.getHealth()>bot.getMaxHealth())bot.setHealth(bot.getMaxHealth());
        gadgets.equip(bot,secondary,secondaryStack,heldSecondary());
        bot.setItemSlot(EquipmentSlot.MAINHAND,primaryStack);
        try{gun=BotGun.supports(primaryStack)?new BotGun(bot):null;}catch(ReflectiveOperationException error){gun=null;}
        updateSpeed();
    }
    private static void remember(Map<String,List<BotLoadoutChoice.Outcome>> history,String id,BotLoadoutChoice.Outcome outcome) {
        var entries=history.computeIfAbsent(id,key->new ArrayList<>());entries.add(outcome);
        if(entries.size()>4)entries.remove(0);
    }

    void tick(boolean running)throws Exception{
        var hunt=MatchManager.realPropHunt();if(hunt!=null){propTick(hunt,running);return;}
        var server=BotManager.server();var arena=BotManager.arena();var settings=BotManager.settings();
        // Keep countdown spawns loaded too, and move the ticket immediately at chunk boundaries.
        if(bot!=null&&!bot.isRemoved())keepTicking();
        // 폭탄전 between rounds counts as not running (preparation, round end); a fresh round respawns benched bots.
        var bombMatch=MatchManager.bomb();
        boolean between=bombMatch!=null&&!bombMatch.activePlay();
        if(!running||between){
            if(between&&bot==null&&server.getTickCount()>=respawnAt){spawn();return;}
            if(bot!=null&&!setUp(server.getTickCount()))bot.getNavigation().stop();
            return;
        }
        if(bot==null){if(server.getTickCount()>=respawnAt)spawn();return;}
        if(bot.isRemoved()){remove();respawnAt=server.getTickCount()+20;return;}
        var bounds=bot.getBoundingBox();
        if(bot.getY()<bot.level().getMinBuildHeight()||DeathZoneBlock.touching(bot)){
            MatchManager.botDeath(id,null);return;
        }
        int now=server.getTickCount();
        if(bot.parkour.active()){aim.idle(bot);return;}
        if(bot.isInWater())bot.getJumpControl().jump();
        boolean thought=now>=nextThink;
        if(thought){nextThink=now+settings.thinkTicks();think(now);}
        if(target!=null&&!eligible(target))target=null;
        if(usingSecondary&&(target==null||BotManager.melee(secondaryStack)&&bot.distanceToSqr(target)>25||!BotManager.melee(secondaryStack)&&now-switchedAt>200))equip(false,now);
        else if(!usingSecondary&&target!=null&&BotManager.melee(secondaryStack)&&bot.distanceToSqr(target)<9)equip(true,now);
        else if(!usingSecondary&&target!=null&&BotManager.melee(primaryStack)&&heldSecondary()&&!BotManager.melee(secondaryStack)&&bot.distanceToSqr(target)>25)equip(true,now);
        // Free-for-all spawn protection also stops the protected one from attacking: use it to move, not to fight.
        var ffa=MatchManager.freeForAll();
        boolean shielded=ffa!=null&&FreeForAllMatch.blocked(bot);
        boolean seen=!shielded&&target!=null&&visible(target);
        var action=tactics.action();
        boolean traveling=Set.of(BotTactics.Action.PATROL,BotTactics.Action.INVESTIGATE,BotTactics.Action.REGROUP,BotTactics.Action.SUPPORT).contains(action);
        gadgets.tick(bot,arena,team,target,seen,movement,thought,traveling);
        if(bot==null||bot.isRemoved())return;
        long time=bot.level().getGameTime();
        if(gadgets.jetting()){aim.idle(bot);return;} // The jetpack steers the body.
        // A known spawn exit outranks wandering toward sounds or regrouping, but never an immediate threat.
        boolean bombObjective=bombMatch!=null&&bombMatch.objective(team,slot)!=null;
        if(bombObjective)movement.followObjectiveFromSpawn();
        if(!bombObjective&&!seen&&!perception.underFire(now)&&perception.danger(bot.position())==null&&bot.getHealth()>=bot.getMaxHealth()*.6f) {
            boolean leaving=movement.leavingSpawn();
            if(movement.exitSpawn(bot,arena,team,now)) {aim.idle(bot);return;}
            if(leaving)nextPath=now; // Continue out into the map without waiting for the patrol interval.
        }
        boolean pathTurn=now>=nextPath;if(pathTurn)nextPath=now+settings.pathTicks();
        boolean melee=BotManager.melee(bot.getMainHandItem()),reloading=gun!=null&&gun.reloading();
        // Decide: everything the bot knows, scored. A raised shield keeps it fighting while backing off.
        var lead=perception.lead(now);var ally=perception.nearestAlly(bot);
        // Team pings are for teammates; free-for-all has none.
        var point=ffa!=null?null:TeamPoints.latest(team,bot.level().dimension().location().toString(),completedPoint);
        // 폭탄전 attackers: a ping on a bomb site is an order to plant there (the mission), not a spot to look at.
        if(point!=null&&bombMatch!=null&&team.equals(bombMatch.game.attacker())&&bombMatch.siteOf(point.x(),point.y(),point.z())!=null){completedPoint=point.sequence();point=null;}
        if(point!=null&&point.sequence()!=activePoint) {
            if(tactics.action()==BotTactics.Action.RALLY)actions.reset();
            activePoint=point.sequence();nextPath=now;pathTurn=true;
        }
        if(point==null&&tactics.action()==BotTactics.Action.RALLY)actions.reset();
        var situation=new BotTactics.Situation(bot.getHealth()/bot.getMaxHealth(),gun==null?1:gun.ammo(bot.getMainHandItem()),reloading,
                seen?Math.max(1,perception.visible()):0,perception.alliesNear(bot,16),perception.allies().size(),perception.engagedAlly(now)!=null,
                seen?bot.distanceTo(target):Double.POSITIVE_INFINITY,perception.underFire(now),melee,
                bot.getHealth()<bot.getMaxHealth()*.6f&&health.available(bot,now,settings.range()),
                lead==null?Long.MAX_VALUE:now-lead.updated,ally!=null&&ally.distanceToSqr(bot)>24*24
                        &&BotMovement.forwardAlly(arena,team,bot.position(),ally.position()),perception.danger(bot.position())!=null);
        // Finish the bounded hide/peek/return cycle unless an urgent close threat or low health interrupts it.
        // The mode's mission (capture point...): every bot pushes it, half the team guards it once it is ours.
        var mission=MatchManager.botObjective(team,slot);
        float missionScore=mission==null?0:BotTactics.objective(situation,mission.stance(),mission.contains(bot.getBoundingBox()),mission.guardAll()||slot%2==0);
        action=tactics.choose(situation,now,
                actions.holdingCover()&&!melee&&bot.getHealth()>bot.getMaxHealth()*.2f&&(!seen||bot.distanceToSqr(target)>16),
                gadgets.shielding(time)&&seen,point!=null,missionScore,BotLearning.offsets(this,situation,mission,now));
        if(bombMatch!=null&&mission!=null&&mission.act()!=BotObjective.Act.NONE&&!situation.hazard()){
            boolean defusing=mission.act()==BotObjective.Act.DEFUSE;
            int duration=defusing?bombMatch.timing().defuseTicks():bombMatch.timing().plantTicks();
            int timer=defusing?bombMatch.game.fuseTicks():bombMatch.game.remainingTicks();
            boolean safe=!seen&&!perception.underFire(now)&&(lead==null||now-lead.updated>60);
            if(BotBombDecision.commit(defusing,timer,duration,bombMatch.botProgress(id),safe))action=BotTactics.Action.OBJECTIVE;
        }
        // Act: the chosen behaviour's tree.
        BotLearning.decision(this,situation,mission,action,now);
        var ctx=actions.ctx();
        ctx.bot=bot;ctx.arena=arena;ctx.team=team;ctx.settings=settings;ctx.now=now;ctx.pathTurn=pathTurn;ctx.melee=melee;
        ctx.shielding=gadgets.shielding(time);ctx.reloading=reloading;ctx.target=seen?target:null;
        ctx.ammo=gun==null?1:gun.ammo(bot.getMainHandItem());ctx.reloadRequested=false;
        ctx.profile=BotCombatProfile.of(gun==null?"":gun.type(),(usingSecondary?secondary:weapon).category());
        movement.profile(ctx.profile);
        ctx.perception=perception;ctx.movement=movement;ctx.health=health;ctx.aim=aim;
        ctx.teamPoint=point==null?null:new Vec3(point.x(),point.y(),point.z());
        ctx.objective=mission;
        var status=actions.run(action);
        if(action==BotTactics.Action.RALLY&&status!=BotTree.Status.RUNNING&&point!=null)completedPoint=point.sequence();
        boolean learnedReload=BotLearning.requestReload(this,now)&&!seen&&!situation.hazard()&&situation.ammo()<.65f
                &&(mission==null||mission.act()==BotObjective.Act.NONE);
        if((ctx.reloadRequested||learnedReload)&&gun!=null&&!gadgets.blocksAttack(time))gun.reload();
        if(status==BotTree.Status.FAILURE)tactics.failed(action,now);
        // Finished its job: rest the action a moment so the bot moves on (re-engage after cover, etc.).
        else if(status==BotTree.Status.SUCCESS)tactics.completed(action,now,action==BotTactics.Action.COVER?120:60);
        // Aim and fire: a visible target always gets the crosshair, whatever the legs are doing.
        if(seen){
            var contact=perception.contact(target.getUUID());
            boolean bow=BotRanged.supports(bot.getMainHandItem());
            var flight=bow?BotRanged.data(bot.getMainHandItem()):gun==null?BotWeaponData.UNKNOWN:gun.ballistics();
            aim.track(bot,target,contact==null?null:contact.velocity,settings,now,melee?0:flight.speed(),flight.gravity(),flight.drag());
            if(ctx.allowFire&&(melee||BotLearning.allowFire(this,now))&&(melee||bot.distanceTo(target)<=ctx.profile.engage())&&now>=nextShot&&(gun==null||!gun.reloading())&&!gadgets.blocksAttack(time)&&aim.ready(bot,target,now,melee)){
                nextShot=now+1;
                if(melee||BotShot.clear(bot,aim.yaw(),aim.pitch(),Math.min(80,bot.distanceTo(target)+8),flight))shoot(target);
            }
        }
    }
    /**
     * Preparation: a team whose gate is open (이동점령 / 호위 / 폭탄전 defenders) uses the time to take up positions at the mission,
     * as players do; everyone else waits in the spawn room.
     */
    private boolean setUp(int now){
        if(bot==null||bot.isRemoved()||!team.equals(MatchManager.preparationGate()))return false;
        var mission=MatchManager.botObjective(team,slot);if(mission==null)return false;
        if(mission.contains(bot.getBoundingBox())){movement.halt(bot);movement.search(bot,now,0);return true;}
        if(now>=nextPath||bot.getNavigation().isDone()){nextPath=now+BotManager.settings().pathTicks();movement.travel(bot,BotManager.arena(),team,mission.center(),now,false);}
        aim.idle(bot);return true;
    }
    /** An enemy of this bot that it may fight: a player or another bot on the other team, within range. */
    boolean eligible(LivingEntity e){
        int range=BotManager.settings().visionRange();
        return enemy(e)&&bot.distanceToSqr(e)<=range*range;
    }
    /** Damage cues are valid beyond the visual acquisition radius. */
    private boolean enemy(LivingEntity e){
        if(e==null||e==bot||!e.isAlive()||e.isSpectator()||e.level()!=bot.level())return false;
        if(e instanceof ServerPlayer p&&(p.isCreative()||LobbyManager.respawning(p.getUUID())))return false;
        if(!(e instanceof ServerPlayer)&&!(e instanceof BotEntity))return false;
        String side=BotManager.side(e);
        var ffa=MatchManager.freeForAll();
        if(ffa!=null&&ffa.remaining(e.getUUID())>0)return false; // protected: invulnerable and invisible
        return BotManager.combatant(side)&&!side.equals(BotManager.side(bot));
    }
    private boolean visible(LivingEntity e){return eligible(e)&&bot.hasLineOfSight(e)&&!SpawnGateProtection.separates(bot,e)&&!SmokeScreen.blocks(bot,e);}
    private void think(int now)throws ReflectiveOperationException{
        perception.update(bot,team,BotManager.settings(),now);
        movement.allies(perception.allies().stream().map(Entity::position).toList());
        LivingEntity previous=target;
        target=perception.target(bot,previous,now);
        if(target!=null){
            double distance=bot.distanceTo(target);encounterDistance=encounterDistance==0?distance:encounterDistance*.75+distance*.25;
            if(target!=previous)nextPath=0;
        }else if(gun!=null)gun.stop();
    }
    /** Fires along the bot's current aim (spread, tremor and lead included), not straight at the target. */
    private void shoot(LivingEntity enemy)throws ReflectiveOperationException{
        var server=BotManager.server();
        float yaw=aim.yaw(),pitch=aim.pitch();
        bot.setYRot(yaw);bot.setYHeadRot(yaw);bot.setXRot(pitch);
        if(gun!=null){
            String result=gun.fire(pitch,yaw);
            BotManager.heard(bot);
            if(result.equals("NO_AMMO")&&!usingSecondary&&heldSecondary()
                    &&(!BotManager.melee(secondaryStack)||bot.distanceToSqr(enemy)<9))equip(true,server.getTickCount());
            return;
        }
        if(BotManager.melee(bot.getMainHandItem())){
            if(bot.distanceToSqr(enemy)<=7.84){bot.doHurtTarget(enemy);bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND);nextShot=server.getTickCount()+12;}
            return;
        }
        if(BotRanged.supports(bot.getMainHandItem()))nextShot=server.getTickCount()+BotRanged.fire(bot,yaw,pitch);
    }
    /** Enemy gunfire within earshot sends an idle bot to investigate, the way players react to shots. */
    void heard(LivingEntity shooter){
        if(bot==null||shooter==bot||shooter.level()!=bot.level())return;
        if(MatchManager.realPropHunt()!=null){propHunt.gunfire(this,shooter);return;}
        String side=BotManager.side(shooter);
        if(!BotManager.combatant(side)||side.equals(BotManager.side(bot)))return;
        perception.gunfire(bot,shooter,BotManager.server().getTickCount());
    }
    void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        int now=BotManager.server().getTickCount();
        if(MatchManager.realPropHunt()!=null){if(bot!=null)propHunt.hurt(this,event.getSource());return;}
        gadgets.hurt(bot,event);
        // Shot by someone: remember roughly where from and re-think now; unseen attackers get investigated (turned toward).
        if(event.getAmount()>0&&event.getSource().getEntity() instanceof LivingEntity attacker&&enemy(attacker)){
            movement.hit(bot,event.getAmount(),now);nextPath=now;
            perception.hit(bot,attacker,now,event.getAmount());nextThink=now;
        }
    }
    /** A teammate under fire: the bot learns about it as from a callout. */
    void allyHit(LivingEntity ally,LivingEntity attacker){
        if(bot!=null&&ally!=bot&&eligible(attacker))perception.allyHit(bot,ally,attacker,BotManager.server().getTickCount());
    }
    /** A score, or "쉼" while the action rests after failing or finishing (held at -∞ by tactics). */
    private String score(BotTactics.Action action){float v=tactics.lastScore(action);return Float.isInfinite(v)?"쉼":String.format(Locale.ROOT,"%.0f",v);}
    String status(){
        var server=BotManager.server();
        if(MatchManager.realPropHunt()!=null)return String.format(Locale.ROOT,"%s: %s팀 · 숨바꼭질 %s",name(),team,bot==null?(team.equals("ALPHA")?"발견됨":"대기"):propHunt.describe());
        if(bot==null)return String.format(Locale.ROOT,"%s: %s팀 · 리스폰 대기",name(),team);
        var mission=MatchManager.botObjective(team,slot);var ffa=MatchManager.freeForAll();
        String rules=ffa==null?"":String.format(Locale.ROOT," · 개인전 %d점 %d위%s",ffa.points(id),ffa.rank(id),FreeForAllMatch.blocked(bot)?" (무적: 공격 불가)":"");
        return rules.isEmpty()?statusLine(server,mission):statusLine(server,mission)+rules;
    }
    private String statusLine(net.minecraft.server.MinecraftServer server,BotObjective mission){
        return String.format(Locale.ROOT,"%s: %s팀 · 행동=%s %.1f초째 (교전 %s·엄폐 %s·후퇴 %s·합류 %s·지원 %s·추적 %s·임무 %s) · 임무=%s · 인지 %d명(보임 %d) · 위험 지점 %d · 주무기=%s 보조=%s · %s",
                name(),team,tactics.action().label,(server.getTickCount()-tactics.since())/20.0,
                score(BotTactics.Action.ENGAGE),score(BotTactics.Action.COVER),score(BotTactics.Action.RETREAT),
                score(BotTactics.Action.REGROUP),score(BotTactics.Action.SUPPORT),score(BotTactics.Action.INVESTIGATE),score(BotTactics.Action.OBJECTIVE),
                mission==null?"없음":mission.label()+" "+mission.doing(),
                perception.known(),perception.visible(),perception.hazardCount(),weapon==null?"대기":weapon.id(),secondary==null?"없음":secondary.id(),gadgets.describe(bot));
    }
}
